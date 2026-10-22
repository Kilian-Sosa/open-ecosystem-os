package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DriveLifecycleFencePostgresTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static final String WORKSPACE = "wrk_dev_placeholder";
  static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
  JdbcTemplate jdbc;
  TransactionTemplate transactions;
  DriveTrashRepository trash;
  DriveSourceLifecycle lifecycle;

  @BeforeEach
  void database() {
    var root =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    String schema = "fence_" + UUID.randomUUID().toString().replace("-", "");
    root.execute("create schema " + schema);
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema="
                + schema,
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(source);
    transactions = new TransactionTemplate(new JdbcTransactionManager(source));
    trash = new DriveTrashRepository(jdbc);
    lifecycle =
        new DriveSourceLifecycle(
            new DriveFolderRepository(jdbc), trash, Clock.fixed(NOW, ZoneOffset.UTC));
    jdbc.execute("create table test_derived_writes(file_id varchar(64))");
    jdbc.update(
        """
        insert into drive_files (file_id, workspace_id, owner_id, encrypted_name, content_type,
          size_bytes, checksum_sha256, storage_key, encryption_algorithm, encryption_key_id,
          content_iv, name_iv, created_at, updated_at)
        values ('file_test', ?, 'usr_dev_placeholder', 'synthetic-ciphertext', 'application/pdf',
          1, 'synthetic-checksum', 'synthetic-key', 'AES-256-GCM', 'test-key', 'iv', 'iv', ?, ?)
        """,
        WORKSPACE,
        java.sql.Timestamp.from(NOW),
        java.sql.Timestamp.from(NOW));
  }

  @Test
  void sourceCommitRacingWithTrashRetainsFenceUntilCommit() throws Exception {
    var writerHasLock = new CountDownLatch(1);
    var releaseWriter = new CountDownLatch(1);
    var trashStarted = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var writer =
          pool.submit(
              () ->
                  transactions.execute(
                      status -> {
                        assertThat(lifecycle.lockActiveFile(WORKSPACE, "file_test")).isTrue();
                        writerHasLock.countDown();
                        await(releaseWriter);
                        jdbc.update("insert into test_derived_writes values ('file_test')");
                        return true;
                      }));
      assertThat(writerHasLock.await(10, TimeUnit.SECONDS)).isTrue();
      var deletion =
          pool.submit(
              () -> {
                trashStarted.countDown();
                trashFile();
              });
      try {
        assertThat(trashStarted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> deletion.get(200, TimeUnit.MILLISECONDS))
            .isInstanceOf(TimeoutException.class);
      } finally {
        releaseWriter.countDown();
      }
      assertThat(writer.get(10, TimeUnit.SECONDS)).isTrue();
      deletion.get(10, TimeUnit.SECONDS);
      assertThat(jdbc.queryForObject("select count(*) from test_derived_writes", Integer.class))
          .isEqualTo(1);
      assertThat(trash.isActiveFile(WORKSPACE, "file_test", NOW)).isFalse();
    } finally {
      releaseWriter.countDown();
    }
  }

  @Test
  void trashCommittedFirstPreventsSourceReadAndDerivedCommit() {
    trashFile();
    transactions.executeWithoutResult(
        status -> {
          if (lifecycle.lockActiveFile(WORKSPACE, "file_test"))
            jdbc.update("insert into test_derived_writes values ('file_test')");
        });
    assertThat(jdbc.queryForObject("select count(*) from test_derived_writes", Integer.class))
        .isZero();
  }

  private void trashFile() {
    transactions.executeWithoutResult(
        status -> {
          new DriveFolderRepository(jdbc).lockWorkspaceForMutation(WORKSPACE);
          trash.createGroup(
              DriveTrashGroup.trashed(
                  "trash_test", WORKSPACE, "file", "file_test", null, "usr_dev_placeholder", NOW));
          assertThat(trash.attachFile(WORKSPACE, "file_test", "trash_test")).isTrue();
        });
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(10, TimeUnit.SECONDS))
        throw new IllegalStateException("Test barrier timed out");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
