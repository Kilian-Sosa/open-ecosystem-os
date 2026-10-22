package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class DriveTrashRepositoryTest {
  static final String WORKSPACE = "wrk_dev_placeholder";
  static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
  SingleConnectionDataSource database;
  JdbcTemplate jdbc;
  DriveTrashRepository repository;

  @BeforeEach
  void setup() {
    database = createDatabase();
    Flyway.configure()
        .dataSource(database)
        .target(MigrationVersion.fromVersion("10"))
        .locations("classpath:db/migration")
        .load()
        .migrate();
    jdbc = new JdbcTemplate(database);
    file("legacy", null);
    Flyway.configure().dataSource(database).locations("classpath:db/migration").load().migrate();
    repository = new DriveTrashRepository(jdbc);
  }

  SingleConnectionDataSource createDatabase() {
    return new SingleConnectionDataSource(
        "jdbc:h2:mem:trash_" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
        "sa",
        "",
        true);
  }

  @AfterEach
  void close() {
    database.destroy();
  }

  @Test
  void V10FilesMigrateActiveWithoutGroups() {
    var legacy =
        new DriveFileRepository(jdbc).findByIdForWorkspace("legacy", WORKSPACE).orElseThrow();
    assertThat(legacy.deletionGroupId()).isNull();
    assertThat(legacy.storageKey()).isEqualTo("synthetic-key-legacy");
    assertThat(repository.findVisibleGroups(WORKSPACE)).isEmpty();
  }

  @Test
  void fileAndFolderGroupPersistWithoutPlaintext() {
    folder("parent", null);
    file("nested", "parent");
    var group = group("group", "folder", "parent", NOW);
    repository.createGroup(group);
    assertThat(repository.attachFolder(WORKSPACE, "parent", "group")).isTrue();
    assertThat(repository.attachFile(WORKSPACE, "nested", "group")).isTrue();
    assertThat(repository.findByIdForWorkspace("group", WORKSPACE)).contains(group);
    assertThat(repository.findByIdForWorkspace("group", "foreign")).isEmpty();
    assertThat(
            new DriveFileRepository(jdbc).findByIdForWorkspace("nested", WORKSPACE).orElseThrow())
        .satisfies(
            file -> {
              assertThat(file.deletionGroupId()).isEqualTo("group");
              assertThat(file.originalFolderId()).isEqualTo("parent");
              assertThat(file.encryptedName()).isEqualTo("synthetic-ciphertext");
            });
    assertThat(
            new DriveFolderRepository(jdbc)
                .findByIdForWorkspace("parent", WORKSPACE)
                .orElseThrow()
                .deletionGroupId())
        .isEqualTo("group");
    assertThatThrownBy(() -> repository.createGroup(group))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "update drive_files set workspace_id = 'foreign', deletion_group_id = 'group'"
                        + " where file_id = 'legacy'"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void retentionIs720HoursAcrossDst() {
    Instant deletion =
        ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, ZoneId.of("Atlantic/Canary")).toInstant();
    var group =
        DriveTrashGroup.trashed(
            "dst", WORKSPACE, "file", "legacy", null, "usr_dev_placeholder", deletion);
    repository.createGroup(group);
    var loaded = repository.findByIdForWorkspace("dst", WORKSPACE).orElseThrow();
    assertThat(loaded.purgeAfter()).isEqualTo(Instant.parse("2026-11-23T11:00:00Z"));
    assertThat(Duration.between(loaded.trashedAt(), loaded.purgeAfter()).toHours()).isEqualTo(720);
    assertThat(loaded.canRestoreAt(loaded.purgeAfter().minusNanos(1))).isTrue();
    assertThat(loaded.canRestoreAt(loaded.purgeAfter())).isFalse();
  }

  @Test
  void independentlyTrashedChildKeepsOwnDeadline() {
    folder("parent", null);
    file("child", "parent");
    repository.createGroup(group("earlier", "file", "child", NOW.minus(Duration.ofDays(5))));
    repository.attachFile(WORKSPACE, "child", "earlier");
    repository.createGroup(group("parent-group", "folder", "parent", NOW));
    repository.attachFolder(WORKSPACE, "parent", "parent-group");
    assertThat(repository.attachFile(WORKSPACE, "child", "parent-group")).isFalse();
    assertThat(repository.findByIdForWorkspace("earlier", WORKSPACE).orElseThrow().purgeAfter())
        .isEqualTo(Instant.parse("2026-11-16T12:00:00Z"));
    assertThat(
            new DriveFileRepository(jdbc)
                .findByIdForWorkspace("child", WORKSPACE)
                .orElseThrow()
                .deletionGroupId())
        .isEqualTo("earlier");
  }

  @Test
  void claimsDuePurgingGroupBeforeOriginalExpiry() {
    repository.createGroup(group("manual", "file", "legacy", NOW));
    assertThat(repository.claimCleanupWork(NOW, 5)).isEmpty();
    var first = repository.claimForPurge("manual", WORKSPACE, NOW).orElseThrow();
    assertThat(repository.findByIdForWorkspace("manual", WORKSPACE).orElseThrow().canRestoreAt(NOW))
        .isFalse();
    assertThat(repository.retryCleanup(first, NOW.plusSeconds(10), "OBJECT_DELETE_FAILED"))
        .isTrue();
    assertThat(repository.claimCleanupWork(NOW.plusSeconds(9), 5)).isEmpty();
    assertThat(repository.claimCleanupWork(NOW.plusSeconds(10), 5))
        .singleElement()
        .satisfies(
            claim -> {
              assertThat(claim.group().state()).isEqualTo(DriveTrashState.PURGING);
              assertThat(claim.attempt()).isEqualTo(2);
              assertThat(claim.token()).isNotEqualTo(first.token());
            });
  }

  @Test
  void expiredClaimCanBeRecoveredButStaleOwnerCannotComplete() {
    repository.createGroup(group("expired", "file", "legacy", NOW.minus(Duration.ofDays(30))));
    var oldClaim = repository.claimCleanupWork(NOW, 1).getFirst();
    assertThat(repository.claimCleanupWork(NOW.plusSeconds(1), 1)).isEmpty();
    var recovered = repository.claimCleanupWork(oldClaim.leaseUntil(), 1).getFirst();
    assertThat(repository.checkpoint(oldClaim, DriveTrashCleanupPhase.OBJECTS, NOW)).isFalse();
    assertThat(repository.completeCleanup(oldClaim, NOW)).isFalse();
    assertThat(repository.completeCleanup(recovered, recovered.leaseUntil().minusSeconds(1)))
        .isFalse();
    for (var phase : DriveTrashCleanupPhase.values())
      assertThat(repository.checkpoint(recovered, phase, recovered.leaseUntil().minusSeconds(1)))
          .isTrue();
    assertThat(repository.completeCleanup(recovered, recovered.leaseUntil().minusSeconds(1)))
        .isTrue();
    assertThat(repository.findByIdForWorkspace("expired", WORKSPACE).orElseThrow().state())
        .isEqualTo(DriveTrashState.PURGED);
  }

  @Test
  void checkpointsSurviveRetryAndExpiredOwnerCannotAdvance() {
    repository.createGroup(group("checkpoint", "file", "legacy", NOW));
    var claim = repository.claimForPurge("checkpoint", WORKSPACE, NOW).orElseThrow();
    assertThat(repository.checkpoint(claim, DriveTrashCleanupPhase.OBJECTS, NOW)).isTrue();
    assertThat(repository.checkpoint(claim, DriveTrashCleanupPhase.DERIVED, claim.leaseUntil()))
        .isFalse();
    assertThat(repository.retryCleanup(claim, NOW.plusSeconds(3), "CLEANUP_FAILED")).isTrue();
    var retry = repository.claimCleanupWork(NOW.plusSeconds(3), 1).getFirst();
    assertThat(retry.objectsDeleted()).isTrue();
    assertThat(retry.derivedDeleted()).isFalse();
    assertThat(retry.searchDeleted()).isFalse();
    assertThat(repository.claimCleanupWork(NOW, 0)).isEmpty();
  }

  @Test
  void purgingGroupsCannotAcquireNewMembers() {
    repository.createGroup(group("closed", "file", "legacy", NOW));
    repository.claimForPurge("closed", WORKSPACE, NOW).orElseThrow();
    folder("late-folder", null);
    assertThat(repository.attachFile(WORKSPACE, "legacy", "closed")).isFalse();
    assertThat(repository.attachFolder(WORKSPACE, "late-folder", "closed")).isFalse();
  }

  private DriveTrashGroup group(String id, String type, String root, Instant when) {
    return DriveTrashGroup.trashed(id, WORKSPACE, type, root, null, "usr_dev_placeholder", when);
  }

  private void folder(String id, String parent) {
    new DriveFolderRepository(jdbc)
        .save(
            new DriveFolderMetadata(
                id,
                WORKSPACE,
                "usr_dev_placeholder",
                parent,
                "synthetic-ciphertext",
                "synthetic-iv",
                NOW,
                NOW));
  }

  private void file(String id, String folder) {
    jdbc.update(
        """
        insert into drive_files (file_id, workspace_id, owner_id, folder_id, encrypted_name,
          content_type, size_bytes, checksum_sha256, storage_key, encryption_algorithm,
          encryption_key_id, content_iv, name_iv, created_at, updated_at)
        values (?, ?, 'usr_dev_placeholder', ?, 'synthetic-ciphertext', 'text/plain', 1,
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', ?, 'AES-256-GCM',
          'synthetic-key', 'synthetic-iv', 'synthetic-iv', ?, ?)
        """,
        id,
        WORKSPACE,
        folder,
        "synthetic-key-" + id,
        java.sql.Timestamp.from(NOW),
        java.sql.Timestamp.from(NOW));
  }
}
