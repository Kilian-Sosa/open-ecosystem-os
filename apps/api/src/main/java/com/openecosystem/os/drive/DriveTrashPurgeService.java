package com.openecosystem.os.drive;

import com.openecosystem.os.audit.AuditOutcome;
import com.openecosystem.os.audit.AuditRecord;
import com.openecosystem.os.audit.JdbcAuditRecordRepository;
import com.openecosystem.os.common.ids.Ids;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.common.security.CorrelationContext;
import com.openecosystem.os.drive.storage.FileObjectStorage;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DriveTrashPurgeService {
  private static final int MAX_DERIVED_ITEMS_PER_RUN = 100;
  private final DriveTrashRepository trash;
  private final DriveFolderRepository folders;
  private final DriveFileRepository files;
  private final FileObjectStorage objects;
  private final AuthenticationContext authentication;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final JdbcAuditRecordRepository audits;

  public DriveTrashPurgeService(
      DriveTrashRepository trash,
      DriveFolderRepository folders,
      DriveFileRepository files,
      FileObjectStorage objects,
      AuthenticationContext authentication,
      JdbcTemplate jdbc,
      TransactionTemplate transactions,
      Clock clock,
      JdbcAuditRecordRepository audits) {
    this.trash = trash;
    this.folders = folders;
    this.files = files;
    this.objects = objects;
    this.authentication = authentication;
    this.jdbc = jdbc;
    this.transactions = transactions;
    this.clock = clock;
    this.audits = audits;
  }

  public PurgeResult purgeNow(String groupId) {
    var actor = authentication.currentPrincipal();
    DriveTrashCleanupClaim claim =
        transactions.execute(
            status -> {
              if (!actor.authenticated()
                  || !folders.isActiveMember(actor.workspaceId(), actor.actorId()))
                throw DriveTrashService.notFound();
              folders.lockWorkspaceForMutation(actor.workspaceId());
              var group =
                  trash
                      .lockGroup(groupId, actor.workspaceId())
                      .orElseThrow(DriveTrashService::notFound);
              if (!group.actorId().equals(actor.actorId())) throw DriveTrashService.notFound();
              if (group.state() == DriveTrashState.PURGED) return null;
              if (group.state() == DriveTrashState.RESTORED) throw DriveTrashService.notFound();
              for (var file : trash.filesInGroup(actor.workspaceId(), groupId))
                if (!file.ownerId().equals(actor.actorId())) throw DriveTrashService.notFound();
              for (var folder : trash.foldersInGroup(actor.workspaceId(), groupId))
                if (!folder.ownerId().equals(actor.actorId())) throw DriveTrashService.notFound();
              var claimed =
                  trash.claimForPurge(groupId, actor.workspaceId(), clock.instant()).orElse(null);
              if (claimed != null) audit(claimed.group(), "purge_requested");
              return claimed;
            });
    if (claim != null) cleanup(claim);
    return new PurgeResult(
        groupId, trash.findByIdForWorkspace(groupId, actor.workspaceId()).orElseThrow().state());
  }

  public int runCleanup(Instant now, int batchSize) {
    int limit = Math.min(Math.max(batchSize, 0), 100);
    int processed = 0;
    Instant claimAt = now;
    while (processed < limit) {
      var claims = trash.claimCleanupWork(claimAt, 1);
      if (claims.isEmpty()) break;
      cleanup(claims.getFirst());
      processed++;
      claimAt = clock.instant();
    }
    return processed;
  }

  private void cleanup(DriveTrashCleanupClaim claim) {
    String workspace = claim.group().workspaceId(), group = claim.group().groupId();
    String errorCode = "OBJECT_DELETE_FAILED";
    try {
      if (!ownsClaim(claim)) return;
      if (!claim.objectsDeleted()) {
        for (var file : trash.filesInGroup(workspace, group)) {
          if (!trash.renewClaim(claim, clock.instant())) return;
          objects.deleteEncryptedObject(file.storageKey());
        }
        if (!trash.checkpoint(claim, DriveTrashCleanupPhase.OBJECTS, clock.instant())) return;
      }
      errorCode = "SEARCH_CLEANUP_PENDING";
      if (!claim.searchDeleted()) {
        if (!searchAbsent(workspace, group)) {
          retry(claim, errorCode);
          return;
        }
        if (!trash.checkpoint(claim, DriveTrashCleanupPhase.SEARCH, clock.instant())) return;
      }
      errorCode = "DERIVED_DELETE_FAILED";
      for (int batch = 0; batch < MAX_DERIVED_ITEMS_PER_RUN; batch++) {
        Boolean complete = transactions.execute(status -> cleanupDerivedItem(claim));
        if (complete == null || complete) return;
      }
      retry(claim, "DERIVED_CLEANUP_PENDING");
    } catch (RuntimeException exception) {
      retry(claim, errorCode);
    }
  }

  private Boolean cleanupDerivedItem(DriveTrashCleanupClaim claim) {
    String workspace = claim.group().workspaceId(), group = claim.group().groupId();
    folders.lockWorkspaceForMutation(workspace);
    trash.lockGroup(group, workspace);
    if (!trash.renewClaim(claim, clock.instant())) return null;
    if (!searchAbsent(workspace, group)) {
      retry(claim, "SEARCH_CLEANUP_PENDING");
      return null;
    }
    var members = trash.filesInGroup(workspace, group, 1);
    if (!members.isEmpty()) {
      String file = members.getFirst().fileId();
      deleteDerived(workspace, file);
      jdbc.update(
          "delete from drive_files where workspace_id=? and deletion_group_id=? and file_id=?",
          workspace,
          group,
          file);
      if (claim.group().rootItemType().equals("folder")) {
        if (!trash.renewLockedClaim(claim, clock.instant()))
          throw new IllegalStateException("Cleanup claim changed");
        return false;
      }
    }
    var memberFolders =
        jdbc.queryForList(
            "select folder_id from drive_folders where workspace_id=? and deletion_group_id=?"
                + " order by case when folder_id=? then 1 else 0 end, folder_id limit 1",
            String.class,
            workspace,
            group,
            claim.group().rootItemId());
    if (!memberFolders.isEmpty()) {
      String folder = memberFolders.getFirst();
      jdbc.update(
          "update drive_files set folder_id=null where workspace_id=? and folder_id=?",
          workspace,
          folder);
      jdbc.update(
          "update drive_folders set parent_folder_id=null where workspace_id=? and"
              + " parent_folder_id=?",
          workspace,
          folder);
      jdbc.update(
          "delete from drive_folders where workspace_id=? and deletion_group_id=? and folder_id=?",
          workspace,
          group,
          folder);
      if (!folder.equals(claim.group().rootItemId())) {
        if (!trash.renewLockedClaim(claim, clock.instant()))
          throw new IllegalStateException("Cleanup claim changed");
        return false;
      }
    }
    if (!trash.renewLockedClaim(claim, clock.instant())
        || !trash.checkpoint(claim, DriveTrashCleanupPhase.DERIVED, clock.instant())
        || !trash.completeCleanup(claim, clock.instant()))
      throw new IllegalStateException("Cleanup claim changed");
    audit(claim.group(), "purged");
    return true;
  }

  private boolean ownsClaim(DriveTrashCleanupClaim claim) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from drive_trash_groups where workspace_id=? and group_id=? and"
                + " state='PURGING' and claim_token=? and lease_until>?)",
            Boolean.class,
            claim.group().workspaceId(),
            claim.group().groupId(),
            claim.token(),
            java.sql.Timestamp.from(clock.instant())));
  }

  private boolean searchAbsent(String workspace, String group) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            select not exists(select 1 from drive_files f where f.workspace_id=? and f.deletion_group_id=? and
              (not exists(select 1 from drive_search_lifecycle l where l.workspace_id=f.workspace_id and l.file_id=f.file_id
                and l.desired_state='ABSENT' and l.reconciled_revision=l.revision)
              or exists(select 1 from drive_search_remote_documents d where d.workspace_id=f.workspace_id and d.file_id=f.file_id
                and (d.operation_id is not null or d.desired_present or d.completed_present or d.completed_revision<>d.desired_revision))))
            """,
            Boolean.class,
            workspace,
            group));
  }

  private void deleteDerived(String workspace, String file) {
    jdbc.update(
        "delete from search_documents where workspace_id=? and search_document_id in (select"
            + " document_id from drive_search_remote_documents where workspace_id=? and file_id=?)",
        workspace,
        workspace,
        file);
    String results = "select ocr_result_id from ocr_results where workspace_id=? and file_id=?";
    jdbc.update(
        "delete from invoice_extraction_field_sources where workspace_id=? and ocr_result_id in ("
            + results
            + ")",
        workspace,
        workspace,
        file);
    jdbc.update(
        "delete from invoice_extraction_fields where workspace_id=? and ocr_result_id in ("
            + results
            + ")",
        workspace,
        workspace,
        file);
    jdbc.update(
        "delete from invoice_extractions where workspace_id=? and file_id=?", workspace, file);
    jdbc.update(
        "delete from ocr_result_words where workspace_id=? and ocr_result_id in (" + results + ")",
        workspace,
        workspace,
        file);
    jdbc.update(
        "delete from ocr_result_pages where workspace_id=? and ocr_result_id in (" + results + ")",
        workspace,
        workspace,
        file);
    jdbc.update("delete from ocr_results where workspace_id=? and file_id=?", workspace, file);
    jdbc.update(
        "delete from knowledge_items where workspace_id=? and source_file_id=?", workspace, file);
    jdbc.update(
        "delete from demo_invoice_extractions where workspace_id=? and run_id in (select run_id"
            + " from demo_invoice_runs where workspace_id=? and file_id=?)",
        workspace,
        workspace,
        file);
    jdbc.update(
        "delete from demo_invoice_runs where workspace_id=? and file_id=?", workspace, file);
    jdbc.update("delete from ocr_jobs where workspace_id=? and file_id=?", workspace, file);
    jdbc.update(
        "delete from drive_file_user_grants where workspace_id=? and file_id=?", workspace, file);
  }

  private void retry(DriveTrashCleanupClaim claim, String errorCode) {
    long seconds = Math.min(3600, 30L << Math.min(claim.attempt(), 6));
    trash.retryCleanup(claim, clock.instant().plusSeconds(seconds), errorCode);
  }

  private void audit(DriveTrashGroup group, String action) {
    audits.save(
        new AuditRecord(
            Ids.newId("aud"),
            "drive." + group.rootItemType() + "." + action,
            group.rootItemType(),
            group.rootItemId(),
            group.workspaceId(),
            group.actorId(),
            CorrelationContext.currentOrCreate(),
            clock.instant(),
            AuditOutcome.SUCCESS,
            Map.of(
                "groupId",
                group.groupId(),
                "status",
                action.equals("purged") ? "PURGED" : "PURGING")));
  }

  public record PurgeResult(String groupId, DriveTrashState state) {}
}
