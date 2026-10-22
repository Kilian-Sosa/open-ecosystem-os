package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.openecosystem.os.audit.JdbcAuditRecordRepository;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.drive.storage.FileObjectStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DriveTrashPurgeServiceTest extends DriveTrashRepositoryTest {
  FileObjectStorage objects;
  Clock clock;
  DriveTrashPurgeService purge;

  @BeforeEach
  void service() {
    objects = mock(FileObjectStorage.class);
    clock = mock(Clock.class);
    when(clock.instant()).thenReturn(NOW);
    purge =
        new DriveTrashPurgeService(
            repository,
            new DriveFolderRepository(jdbc),
            new DriveFileRepository(jdbc),
            objects,
            () ->
                new AuthenticatedPrincipal(
                    "usr_dev_placeholder", WORKSPACE, Set.of("INSTANCE_OWNER"), true),
            jdbc,
            new TransactionTemplate(new JdbcTransactionManager(database)),
            clock,
            mock(JdbcAuditRecordRepository.class));
  }

  String trashLegacy() {
    repository.createGroup(
        DriveTrashGroup.trashed(
            "purge", WORKSPACE, "file", "legacy", null, "usr_dev_placeholder", NOW));
    repository.attachFile(WORKSPACE, "legacy", "purge");
    jdbc.update(
        "insert into"
            + " drive_search_lifecycle(workspace_id,file_id,revision,desired_state,reconciled_revision,next_attempt_at)"
            + " values (?, 'legacy', 1, 'ABSENT', 1, ?)",
        WORKSPACE,
        java.sql.Timestamp.from(NOW));
    return "purge";
  }

  @Test
  void manualPurgeImmediatelyPreventsRestoreAndDeletesOnlyItsObjects() {
    String group = trashLegacy();
    assertThat(purge.purgeNow(group).state()).isEqualTo(DriveTrashState.PURGED);
    assertThat(repository.restoreGroup(group, WORKSPACE, NOW)).isFalse();
    verify(objects).deleteEncryptedObject("synthetic-key-legacy");
    verifyNoMoreInteractions(objects);
    assertThat(new DriveFileRepository(jdbc).findByIdForWorkspace("legacy", WORKSPACE)).isEmpty();
    assertThat(purge.purgeNow(group).state()).isEqualTo(DriveTrashState.PURGED);
    verifyNoMoreInteractions(objects);
  }

  @Test
  void failedManualPurgeRetriesBeforeThirtyDayDeadline() {
    String group = trashLegacy();
    doThrow(new IllegalStateException("synthetic secret must not persist"))
        .doNothing()
        .when(objects)
        .deleteEncryptedObject("synthetic-key-legacy");
    assertThat(purge.purgeNow(group).state()).isEqualTo(DriveTrashState.PURGING);
    assertThat(repository.restoreGroup(group, WORKSPACE, NOW)).isFalse();
    assertThat(
            jdbc.queryForObject(
                "select last_error_code from drive_trash_groups where group_id=?",
                String.class,
                group))
        .isEqualTo("OBJECT_DELETE_FAILED");
    when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
    assertThat(purge.runCleanup(clock.instant(), 25)).isEqualTo(1);
    assertThat(repository.findByIdForWorkspace(group, WORKSPACE).orElseThrow().state())
        .isEqualTo(DriveTrashState.PURGED);
  }

  @Test
  void purgeWaitsForLateIndexWriteReconciliationAndResumesAfterObjectDelete() {
    String group = trashLegacy();
    jdbc.update("update drive_search_lifecycle set reconciled_revision=-1");
    assertThat(purge.purgeNow(group).state()).isEqualTo(DriveTrashState.PURGING);
    assertThat(new DriveFileRepository(jdbc).findByIdForWorkspace("legacy", WORKSPACE)).isPresent();
    assertThat(
            jdbc.queryForObject(
                "select objects_deleted from drive_trash_groups where group_id=?",
                Boolean.class,
                group))
        .isTrue();
    jdbc.update("update drive_search_lifecycle set reconciled_revision=revision");
    when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
    serviceAfterRestart();
    assertThat(purge.runCleanup(clock.instant(), 25)).isEqualTo(1);
    verify(objects, times(1)).deleteEncryptedObject("synthetic-key-legacy");
    assertThat(repository.findByIdForWorkspace(group, WORKSPACE).orElseThrow().state())
        .isEqualTo(DriveTrashState.PURGED);
  }

  String trashFolderWithFiles(String id, int count) {
    folder(id, null);
    repository.createGroup(
        DriveTrashGroup.trashed(
            id,
            WORKSPACE,
            "folder",
            id,
            null,
            "usr_dev_placeholder",
            NOW.minus(Duration.ofDays(30))));
    repository.attachFolder(WORKSPACE, id, id);
    for (int i = 0; i < count; i++) {
      String fileId = id + "-" + i;
      file(fileId, id);
      repository.attachFile(WORKSPACE, fileId, id);
      jdbc.update(
          "insert into"
              + " drive_search_lifecycle(workspace_id,file_id,revision,desired_state,reconciled_revision,next_attempt_at)"
              + " values (?, ?, 1, 'ABSENT', 1, ?)",
          WORKSPACE,
          fileId,
          java.sql.Timestamp.from(NOW));
    }
    return id;
  }

  AtomicReference<Instant> slowDeletes() {
    var now = new AtomicReference<>(NOW);
    when(clock.instant()).thenAnswer(ignored -> now.get());
    doAnswer(
            ignored -> {
              now.updateAndGet(instant -> instant.plusSeconds(45));
              return null;
            })
        .when(objects)
        .deleteEncryptedObject(anyString());
    return now;
  }

  @Test
  void folderCleanupRenewsLeaseBetweenObjectDeletes() {
    String group = trashFolderWithFiles("slow-folder", 10);
    var now = slowDeletes();
    assertThat(purge.purgeNow(group).state()).isEqualTo(DriveTrashState.PURGED);
    assertThat(now.get()).isAfter(NOW.plus(Duration.ofMinutes(5)));
    verify(objects, times(10)).deleteEncryptedObject(anyString());
  }

  @Test
  void scheduledBatchClaimsEachGroupWhenItsCleanupStarts() {
    String first = trashFolderWithFiles("a-slow-folder", 10);
    String second = trashFolderWithFiles("z-next-folder", 1);
    slowDeletes();
    assertThat(purge.runCleanup(NOW, 2)).isEqualTo(2);
    assertThat(repository.findByIdForWorkspace(first, WORKSPACE).orElseThrow().state())
        .isEqualTo(DriveTrashState.PURGED);
    assertThat(repository.findByIdForWorkspace(second, WORKSPACE).orElseThrow().state())
        .isEqualTo(DriveTrashState.PURGED);
    verify(objects, times(11)).deleteEncryptedObject(anyString());
  }

  @Test
  void slowDerivedCleanupCanOutliveTheLeaseWithoutRollingBack() {
    String group = trashFolderWithFiles("slow-derived", 2);
    var now = new AtomicReference<>(NOW);
    when(clock.instant()).thenAnswer(ignored -> now.get());
    var slowJdbc =
        new org.springframework.jdbc.core.JdbcTemplate(database) {
          @Override
          public int update(String sql, Object... arguments) {
            int changed = super.update(sql, arguments);
            if (sql.startsWith("delete from ocr_results"))
              now.updateAndGet(instant -> instant.plusSeconds(360));
            return changed;
          }
        };
    useCleanupJdbc(slowJdbc);
    assertThat(purge.purgeNow(group).state()).isEqualTo(DriveTrashState.PURGED);
    assertThat(now.get()).isAfter(NOW.plus(Duration.ofMinutes(10)));
    assertThat(repository.filesInGroup(WORKSPACE, group)).isEmpty();
    assertThat(new DriveFileRepository(jdbc).findByIdForWorkspace("legacy", WORKSPACE)).isPresent();
  }

  @Test
  void derivedCleanupRetainsCommittedProgressAfterFailureAndRestart() {
    String group = trashFolderWithFiles("partial-derived", 2);
    var failingJdbc =
        new org.springframework.jdbc.core.JdbcTemplate(database) {
          @Override
          public int update(String sql, Object... arguments) {
            if (sql.startsWith("delete from ocr_jobs")
                && java.util.Arrays.asList(arguments).contains("partial-derived-1"))
              throw new IllegalStateException("Synthetic database failure");
            return super.update(sql, arguments);
          }
        };
    useCleanupJdbc(failingJdbc);
    assertThat(purge.purgeNow(group).state()).isEqualTo(DriveTrashState.PURGING);
    assertThat(repository.filesInGroup(WORKSPACE, group))
        .extracting(DriveFileMetadata::fileId)
        .containsExactly("partial-derived-1");
    assertThat(repository.restoreGroup(group, WORKSPACE, NOW)).isFalse();
    serviceAfterRestart();
    when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
    assertThat(purge.runCleanup(clock.instant(), 1)).isEqualTo(1);
    assertThat(repository.findByIdForWorkspace(group, WORKSPACE).orElseThrow().state())
        .isEqualTo(DriveTrashState.PURGED);
    verify(objects, times(2)).deleteEncryptedObject(anyString());
  }

  @Test
  void largeDerivedCleanupYieldsAndResumesFromPersistedProgress() {
    String group = trashFolderWithFiles("bounded-derived", 150);
    assertThat(purge.purgeNow(group).state()).isEqualTo(DriveTrashState.PURGING);
    assertThat(repository.filesInGroup(WORKSPACE, group).size()).isBetween(1, 149);
    assertThat(repository.foldersInGroup(WORKSPACE, group)).hasSize(1);
    serviceAfterRestart();
    when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
    assertThat(purge.runCleanup(clock.instant(), 1)).isEqualTo(1);
    assertThat(repository.findByIdForWorkspace(group, WORKSPACE).orElseThrow().state())
        .isEqualTo(DriveTrashState.PURGED);
    verify(objects, times(150)).deleteEncryptedObject(anyString());
  }

  @Test
  void largeFolderMetadataCleanupYieldsWhileKeepingItsRootTombstone() {
    String group = trashFolderWithFiles("bounded-folders", 0);
    for (int i = 0; i < 150; i++) {
      String id = group + "-" + i;
      folder(id, group);
      repository.attachFolder(WORKSPACE, id, group);
    }
    assertThat(purge.purgeNow(group).state()).isEqualTo(DriveTrashState.PURGING);
    assertThat(repository.foldersInGroup(WORKSPACE, group).size()).isBetween(1, 150);
    assertThat(new DriveFolderRepository(jdbc).findByIdForWorkspace(group, WORKSPACE)).isPresent();
    serviceAfterRestart();
    when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
    assertThat(purge.runCleanup(clock.instant(), 1)).isEqualTo(1);
    assertThat(repository.findByIdForWorkspace(group, WORKSPACE).orElseThrow().state())
        .isEqualTo(DriveTrashState.PURGED);
  }

  void useCleanupJdbc(org.springframework.jdbc.core.JdbcTemplate cleanupJdbc) {
    purge =
        new DriveTrashPurgeService(
            repository,
            new DriveFolderRepository(jdbc),
            new DriveFileRepository(jdbc),
            objects,
            () -> new AuthenticatedPrincipal("usr_dev_placeholder", WORKSPACE, Set.of(), true),
            cleanupJdbc,
            new TransactionTemplate(new JdbcTransactionManager(database)),
            clock,
            mock(JdbcAuditRecordRepository.class));
  }

  void serviceAfterRestart() {
    purge =
        new DriveTrashPurgeService(
            repository,
            new DriveFolderRepository(jdbc),
            new DriveFileRepository(jdbc),
            objects,
            () -> new AuthenticatedPrincipal("usr_dev_placeholder", WORKSPACE, Set.of(), true),
            jdbc,
            new TransactionTemplate(new JdbcTransactionManager(database)),
            clock,
            mock(JdbcAuditRecordRepository.class));
  }

  @Test
  void restartAfterPurgeClaimResumesCleanupBeforeExpiry() {
    String group = trashLegacy();
    repository.claimForPurge(group, WORKSPACE, NOW).orElseThrow();
    when(clock.instant()).thenReturn(NOW.plus(Duration.ofMinutes(6)));
    assertThat(purge.runCleanup(clock.instant(), 25)).isEqualTo(1);
    assertThat(repository.findByIdForWorkspace(group, WORKSPACE).orElseThrow().state())
        .isEqualTo(DriveTrashState.PURGED);
  }

  @Test
  void scheduledCleanupClaimsAtExactDeadline() {
    trashLegacy();
    assertThat(purge.runCleanup(NOW.plus(Duration.ofDays(30)).minusSeconds(1), 25)).isZero();
    when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(30)));
    assertThat(purge.runCleanup(clock.instant(), 25)).isEqualTo(1);
  }

  @Test
  void guessedOrUnauthorizedGroupCannotPurge() {
    String group = trashLegacy();
    assertThatThrownBy(() -> purge.purgeNow("missing"))
        .isInstanceOf(com.openecosystem.os.common.errors.ApiException.class);
    jdbc.update(
        "delete from workspace_memberships where workspace_id=? and user_id='usr_dev_placeholder'",
        WORKSPACE);
    assertThatThrownBy(() -> purge.purgeNow(group))
        .isInstanceOf(com.openecosystem.os.common.errors.ApiException.class);
    verifyNoInteractions(objects);
  }
}
