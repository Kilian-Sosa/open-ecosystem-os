package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DriveFileUserGrantPostgresRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  private JdbcTemplate jdbcTemplate;
  private DriveFileUserGrantRepository repository;
  private String schema;

  @BeforeEach
  void setUp() {
    schema = "drive_grant_" + UUID.randomUUID().toString().replace("-", "");
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .createSchemas(true)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    jdbcTemplate =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));
    repository = new DriveFileUserGrantRepository(jdbcTemplate);
    insertActiveMember("wrk_one", "usr_owner");
    insertActiveMember("wrk_one", "usr_grantee");
    insertDriveFile("file_one", "wrk_one");
  }

  @Test
  void grantsActiveSameWorkspaceUserAndRegrantReusesRelationship() {
    DriveFileUserGrant first =
        repository.grantOrReactivateView(
            "grant_first", "wrk_one", "file_one", "usr_grantee", "usr_owner", NOW);
    repository.revokeView("wrk_one", "file_one", "usr_grantee", "usr_owner", NOW.plusSeconds(60));
    insertActiveMember("wrk_one", "usr_regrantor");

    DriveFileUserGrant regranted =
        repository.grantOrReactivateView(
            "grant_second",
            "wrk_one",
            "file_one",
            "usr_grantee",
            "usr_regrantor",
            NOW.plusSeconds(120));

    assertThat(first.active()).isTrue();
    assertThat(regranted.grantId()).isEqualTo("grant_first");
    assertThat(regranted.createdAt()).isEqualTo(NOW);
    assertThat(regranted.grantedByUserId()).isEqualTo("usr_regrantor");
    assertThat(regranted.grantedAt()).isEqualTo(NOW.plusSeconds(120));
    assertThat(regranted.revokedByUserId()).isNull();
    assertThat(regranted.revokedAt()).isNull();
    assertThat(regranted.updatedAt()).isEqualTo(NOW.plusSeconds(120));
  }

  @Test
  void rejectsInactiveMissingRemovedAndForeignGrantParticipants() {
    insertActiveMember("wrk_one", "usr_disabled");
    jdbcTemplate.update(
        "update identity_users set status = 'disabled' where user_id = 'usr_disabled'");
    insertActiveMember("wrk_one", "usr_removed");
    jdbcTemplate.update(
        "delete from workspace_memberships where workspace_id = 'wrk_one' and user_id ="
            + " 'usr_removed'");
    insertActiveMember("wrk_other", "usr_foreign");
    insertActiveMember("wrk_other", "usr_foreign_grantor");
    insertActiveMember("wrk_one", "usr_inactive_grantor");
    jdbcTemplate.update(
        "update identity_users set status = 'disabled' where user_id = 'usr_inactive_grantor'");

    assertThatThrownBy(
            () ->
                repository.grantOrReactivateView(
                    "grant_missing", "wrk_one", "file_one", "usr_missing", "usr_owner", NOW))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                repository.grantOrReactivateView(
                    "grant_disabled", "wrk_one", "file_one", "usr_disabled", "usr_owner", NOW))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                repository.grantOrReactivateView(
                    "grant_removed", "wrk_one", "file_one", "usr_removed", "usr_owner", NOW))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                repository.grantOrReactivateView(
                    "grant_foreign", "wrk_one", "file_one", "usr_foreign", "usr_owner", NOW))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                repository.grantOrReactivateView(
                    "grant_inactive_grantor",
                    "wrk_one",
                    "file_one",
                    "usr_grantee",
                    "usr_inactive_grantor",
                    NOW))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () ->
                repository.grantOrReactivateView(
                    "grant_foreign_grantor",
                    "wrk_one",
                    "file_one",
                    "usr_grantee",
                    "usr_foreign_grantor",
                    NOW))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void concurrentlyGrantsAndRegrantsOneActiveRelationship() throws Exception {
    List<DriveFileUserGrant> initialResults =
        runConcurrently(
            () ->
                repository.grantOrReactivateView(
                    "grant_initial_one", "wrk_one", "file_one", "usr_grantee", "usr_owner", NOW),
            () ->
                repository.grantOrReactivateView(
                    "grant_initial_two",
                    "wrk_one",
                    "file_one",
                    "usr_grantee",
                    "usr_owner",
                    NOW.plusSeconds(1)));
    DriveFileUserGrant existing = initialResults.getFirst();
    assertThat(initialResults)
        .extracting(DriveFileUserGrant::grantId)
        .containsOnly(existing.grantId());
    repository.revokeView("wrk_one", "file_one", "usr_grantee", "usr_owner", NOW.plusSeconds(2));

    List<DriveFileUserGrant> concurrentResults =
        runConcurrently(
            () ->
                repository.grantOrReactivateView(
                    "grant_one",
                    "wrk_one",
                    "file_one",
                    "usr_grantee",
                    "usr_owner",
                    NOW.plusSeconds(3)),
            () ->
                repository.grantOrReactivateView(
                    "grant_two",
                    "wrk_one",
                    "file_one",
                    "usr_grantee",
                    "usr_owner",
                    NOW.plusSeconds(4)));

    assertThat(concurrentResults)
        .extracting(DriveFileUserGrant::grantId)
        .containsOnly(existing.grantId());
    assertThat(repository.find("wrk_one", "file_one", "usr_grantee", "file:view"))
        .get()
        .satisfies(grant -> assertThat(grant.active()).isTrue());
    assertThat(
            jdbcTemplate.queryForObject(
                """
                select count(*)
                from drive_file_user_grants
                where workspace_id = 'wrk_one'
                  and file_id = 'file_one'
                  and grantee_user_id = 'usr_grantee'
                  and action = 'file:view'
                """,
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void revokesOnceAndReturnsTheCurrentGrantForRepeatedRequests() {
    repository.grantOrReactivateView(
        "grant_revoke", "wrk_one", "file_one", "usr_grantee", "usr_owner", NOW);

    DriveFileUserGrant revoked =
        repository
            .revokeView("wrk_one", "file_one", "usr_grantee", "usr_owner", NOW.plusSeconds(10))
            .orElseThrow();
    DriveFileUserGrant repeated =
        repository
            .revokeView("wrk_one", "file_one", "usr_grantee", "usr_owner", NOW.plusSeconds(20))
            .orElseThrow();

    assertThat(revoked.revokedByUserId()).isEqualTo("usr_owner");
    assertThat(revoked.revokedAt()).isEqualTo(NOW.plusSeconds(10));
    assertThat(repeated).isEqualTo(revoked);
    assertThat(repository.revokeView("wrk_one", "file_one", "usr_missing", "usr_owner", NOW))
        .isEmpty();
  }

  @SafeVarargs
  private final List<DriveFileUserGrant> runConcurrently(Callable<DriveFileUserGrant>... operations)
      throws Exception {
    try (ExecutorService executor = Executors.newFixedThreadPool(operations.length)) {
      CountDownLatch ready = new CountDownLatch(operations.length);
      CountDownLatch start = new CountDownLatch(1);
      List<Future<DriveFileUserGrant>> futures =
          java.util.Arrays.stream(operations)
              .map(
                  operation ->
                      executor.submit(
                          () -> {
                            ready.countDown();
                            start.await();
                            return operation.call();
                          }))
              .toList();
      ready.await();
      start.countDown();
      return futures.stream().map(this::resultOf).toList();
    }
  }

  private DriveFileUserGrant resultOf(Future<DriveFileUserGrant> future) {
    try {
      return future.get();
    } catch (Exception exception) {
      throw new AssertionError("Concurrent grant command failed", exception);
    }
  }

  private void insertActiveMember(String workspaceId, String userId) {
    jdbcTemplate.update(
        """
        insert into identity_users (
          user_id, display_name, email, avatar_initials, status, is_seeded, created_at, updated_at
        ) values (?, ?, ?, 'TU', 'active', false, ?, ?)
        """,
        userId,
        userId,
        userId + "@example.test",
        Timestamp.from(NOW),
        Timestamp.from(NOW));
    jdbcTemplate.update(
        """
        insert into workspaces (
          workspace_id, name, slug, status, is_seeded, created_at, updated_at
        ) values (?, ?, ?, 'active', false, ?, ?)
        on conflict (workspace_id) do nothing
        """,
        workspaceId,
        workspaceId,
        workspaceId,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
    jdbcTemplate.update(
        """
        insert into workspace_memberships (
          workspace_id, user_id, role, is_default, created_at, updated_at
        ) values (?, ?, 'VIEWER', false, ?, ?)
        """,
        workspaceId,
        userId,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }

  private void insertDriveFile(String fileId, String workspaceId) {
    jdbcTemplate.update(
        """
        insert into drive_files (
          file_id, workspace_id, owner_id, encrypted_name, content_type, size_bytes,
          checksum_sha256, storage_key, encryption_algorithm, encryption_key_id,
          content_iv, name_iv, created_at, updated_at
        ) values (?, ?, 'usr_owner', 'encrypted-name', 'application/pdf', 10,
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', ?,
          'AES-256-GCM', 'test-key', 'content-iv', 'name-iv', ?, ?)
        """,
        fileId,
        workspaceId,
        "workspaces/" + workspaceId + "/drive/" + fileId + "/original",
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }
}
