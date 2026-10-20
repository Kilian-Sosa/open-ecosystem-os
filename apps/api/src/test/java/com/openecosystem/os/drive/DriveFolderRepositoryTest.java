package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DriveFolderRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-10-20T08:00:00Z");
  private JdbcTemplate jdbc;
  private DriveFolderRepository repository;
  private final List<SingleConnectionDataSource> databases = new ArrayList<>();

  @BeforeEach
  void createDatabase() {
    var dataSource = database();
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    jdbc = new JdbcTemplate(dataSource);
    repository = new DriveFolderRepository(jdbc);
  }

  @AfterEach
  void closeDatabases() {
    databases.forEach(SingleConnectionDataSource::destroy);
  }

  private SingleConnectionDataSource database() {
    var dataSource =
        new SingleConnectionDataSource(
            "jdbc:h2:mem:folders_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
            "sa",
            "",
            true);
    databases.add(dataSource);
    return dataSource;
  }

  @Test
  void existingFilesMigrateIntoRoot() {
    var dataSource = database();
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("9"))
        .load()
        .migrate();
    var legacy = new JdbcTemplate(dataSource);
    legacy.update(
        """
        insert into drive_files (
          file_id, workspace_id, owner_id, encrypted_name, content_type, size_bytes,
          checksum_sha256, storage_key, encryption_algorithm, encryption_key_id,
          content_iv, name_iv, created_at, updated_at
        ) values ('file_legacy', 'wrk_dev_placeholder', 'usr_dev_placeholder',
          'synthetic-ciphertext', 'text/plain', 10,
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
          'opaque-original-key', 'AES-256-GCM', 'test-key', 'content-iv', 'name-iv',
          current_timestamp, current_timestamp)
        """);

    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    assertThat(new DriveFileRepository(legacy).listByFolder("wrk_dev_placeholder", null))
        .singleElement()
        .satisfies(
            file -> {
              assertThat(file.fileId()).isEqualTo("file_legacy");
              assertThat(file.folderId()).isNull();
              assertThat(file.encryptedName()).isEqualTo("synthetic-ciphertext");
              assertThat(file.storageKey()).isEqualTo("opaque-original-key");
            });
  }

  @Test
  void nestedEmptyFolderSurvivesReload() {
    repository.save(folder("folder_parent", "wrk_dev_placeholder", null));
    repository.save(folder("folder_child", "wrk_dev_placeholder", "folder_parent"));
    repository.save(folder("folder_grandchild", "wrk_dev_placeholder", "folder_child"));

    var reloaded = new DriveFolderRepository(jdbc);
    assertThat(reloaded.findByIdForWorkspace("folder_child", "wrk_dev_placeholder"))
        .contains(folder("folder_child", "wrk_dev_placeholder", "folder_parent"));
    assertThat(reloaded.findByIdForWorkspace("folder_child", "wrk_other")).isEmpty();
    assertThat(reloaded.listChildren("wrk_dev_placeholder", null))
        .extracting(DriveFolderMetadata::folderId)
        .containsExactly("folder_parent");
    assertThat(reloaded.listChildren("wrk_dev_placeholder", "folder_parent"))
        .extracting(DriveFolderMetadata::folderId)
        .containsExactly("folder_child");
    assertThat(reloaded.listChildren("wrk_dev_placeholder", "folder_grandchild")).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select encrypted_name from drive_folders where folder_id = 'folder_child'",
                String.class))
        .isEqualTo("synthetic-encrypted-name");
  }

  @Test
  void crossWorkspaceFolderReferenceRejected() {
    jdbc.update(
        "insert into workspaces (workspace_id, name, slug, status, is_seeded, created_at,"
            + " updated_at) values ('wrk_other', 'Synthetic', 'synthetic', 'active', false,"
            + " current_timestamp, current_timestamp)");
    repository.save(folder("folder_parent", "wrk_dev_placeholder", null));

    assertThatThrownBy(
            () -> repository.save(folder("folder_foreign", "wrk_other", "folder_parent")))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () -> repository.save(folder("folder_missing", "wrk_dev_placeholder", "missing")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void missingWorkspaceCannotSilentlySkipMutationLock() {
    var transaction = new TransactionTemplate(new JdbcTransactionManager(jdbc.getDataSource()));
    transaction.executeWithoutResult(
        status -> repository.lockWorkspaceForMutation("wrk_dev_placeholder"));
    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    status -> repository.lockWorkspaceForMutation("missing")))
        .isInstanceOf(EmptyResultDataAccessException.class);
  }

  private DriveFolderMetadata folder(String id, String workspaceId, String parentId) {
    return new DriveFolderMetadata(
        id,
        workspaceId,
        "usr_dev_placeholder",
        parentId,
        "synthetic-encrypted-name",
        "synthetic-name-iv",
        NOW,
        NOW);
  }
}
