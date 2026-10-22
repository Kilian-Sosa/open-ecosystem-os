package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.Set;
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
