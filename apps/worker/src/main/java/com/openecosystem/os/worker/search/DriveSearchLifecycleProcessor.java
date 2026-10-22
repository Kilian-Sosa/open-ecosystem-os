package com.openecosystem.os.worker.search;

import com.openecosystem.os.worker.drive.DriveLifecycleFence;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DriveSearchLifecycleProcessor {
  private final JdbcTemplate jdbc;
  private final SearchDocumentRepository documents;
  private final DriveLifecycleFence fence;
  private final SearchIndexClient remote;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public DriveSearchLifecycleProcessor(
      JdbcTemplate jdbc,
      SearchDocumentRepository documents,
      DriveLifecycleFence fence,
      SearchIndexClient remote,
      TransactionTemplate transactions,
      Clock clock) {
    this.jdbc = jdbc;
    this.documents = documents;
    this.fence = fence;
    this.remote = remote;
    this.transactions = transactions;
    this.clock = clock;
  }

  public void reconcileFile(String workspaceId, String fileId) {
    Action action = transactions.execute(status -> prepare(workspaceId, fileId));
    if (action == null) return;
    try {
      if (action.kind().equals("SUBMIT")) {
        long uid =
            action.present()
                ? remote.submitUpsert(action.document(), action.operationId())
                : remote.submitRemoval(action.documentId(), action.operationId());
        accepted(action, uid);
      } else if (action.kind().equals("LOOKUP")) {
        var found = remote.findTask(action.operationId(), action.startedAt(), action.cursor());
        if (found.taskUid().isPresent()) accepted(action, found.taskUid().getAsLong());
        else
          jdbc.update(
              "update drive_search_remote_documents set lookup_cursor=? where operation_id=?",
              found.nextCursor(),
              action.operationId());
      } else {
        var state = remote.taskState(action.taskUid());
        if (state == SearchIndexClient.TaskState.SUCCEEDED) {
          boolean verified = action.present() || remote.isAbsent(action.documentId());
          finish(action, verified);
          if (!verified) error(workspaceId, fileId, "REMOTE_ABSENCE_UNVERIFIED");
        } else if (state == SearchIndexClient.TaskState.FAILED) {
          finish(action, false);
          error(workspaceId, fileId, "REMOTE_TASK_FAILED");
        }
      }
    } catch (SearchSubmissionException exception) {
      if (exception.ambiguous())
        jdbc.update(
            "update drive_search_remote_documents set operation_state='UNCERTAIN' where"
                + " operation_id=? and task_uid is null",
            action.operationId());
      else finish(action, false);
      error(
          workspaceId,
          fileId,
          exception.ambiguous() ? "REMOTE_SUBMISSION_UNKNOWN" : "REMOTE_SUBMISSION_REJECTED");
    } catch (RuntimeException exception) {
      // An unclassified error during submission cannot establish that the request was rejected.
      if (action.kind().equals("SUBMIT"))
        jdbc.update(
            "update drive_search_remote_documents set operation_state='UNCERTAIN' where"
                + " operation_id=? and task_uid is null",
            action.operationId());
      error(workspaceId, fileId, "REMOTE_RECONCILIATION_PENDING");
    }
  }

  private Action prepare(String workspaceId, String fileId) {
    if (jdbc.queryForList(
            "select workspace_id from workspaces where workspace_id=? for update",
            String.class,
            workspaceId)
        .isEmpty()) return null;
    boolean active = fence.isActiveFile(workspaceId, fileId);
    var lifecycle =
        jdbc.queryForList(
            "select * from drive_search_lifecycle where workspace_id=? and file_id=?",
            workspaceId,
            fileId);
    if (lifecycle.isEmpty())
      jdbc.update(
          "insert into"
              + " drive_search_lifecycle(workspace_id,file_id,revision,desired_state,next_attempt_at)"
              + " values (?,?,0,?,?)",
          workspaceId,
          fileId,
          active ? "ACTIVE" : "ABSENT",
          time(clock.instant()));
    else if (!active && lifecycle.getFirst().get("desired_state").equals("ACTIVE"))
      jdbc.update(
          "update drive_search_lifecycle set"
              + " revision=revision+1,desired_state='ABSENT',next_attempt_at=? where workspace_id=?"
              + " and file_id=?",
          time(clock.instant()),
          workspaceId,
          fileId);
    var state =
        jdbc.queryForMap(
            "select * from drive_search_lifecycle where workspace_id=? and file_id=?",
            workspaceId,
            fileId);
    long revision = number(state, "revision");
    boolean desiredActive = active && state.get("desired_state").equals("ACTIVE");
    var references = documents.findSourcesForFile(workspaceId, fileId);
    jdbc.update(
        "update drive_search_remote_documents set desired_revision=?,desired_present=false where"
            + " workspace_id=? and file_id=?",
        revision,
        workspaceId,
        fileId);
    for (var reference : references) {
      boolean present = desiredActive && permitted(reference);
      int changed =
          jdbc.update(
              "update drive_search_remote_documents set desired_revision=?,desired_present=? where"
                  + " workspace_id=? and document_id=? and file_id=?",
              revision,
              present,
              workspaceId,
              reference.documentId(),
              fileId);
      if (changed == 0)
        jdbc.update(
            "insert into"
                + " drive_search_remote_documents(workspace_id,file_id,document_id,desired_revision,desired_present)"
                + " values (?,?,?,?,?)",
            workspaceId,
            fileId,
            reference.documentId(),
            revision,
            present);
    }
    jdbc.update(
        "update drive_search_lifecycle set next_attempt_at=? where workspace_id=? and file_id=?",
        time(clock.instant().plusSeconds(2)),
        workspaceId,
        fileId);
    var work =
        jdbc.queryForList(
            "select * from drive_search_remote_documents where workspace_id=? and file_id=? and"
                + " (operation_id is not null or completed_revision<>desired_revision or"
                + " completed_present<>desired_present) order by document_id limit 1",
            workspaceId,
            fileId);
    if (work.isEmpty()) {
      markReconciled(workspaceId, fileId);
      return null;
    }
    jdbc.update(
        "update drive_search_lifecycle set reconciled_revision=-1 where workspace_id=? and"
            + " file_id=?",
        workspaceId,
        fileId);
    var row = work.getFirst();
    String documentId = (String) row.get("document_id");
    String operation = (String) row.get("operation_id");
    if (operation != null) {
      var uid = (Number) row.get("task_uid");
      return new Action(
          uid == null ? "LOOKUP" : "POLL",
          workspaceId,
          fileId,
          documentId,
          operation,
          (Boolean) row.get("operation_present"),
          null,
          instant(row.get("operation_started_at")),
          uid == null ? null : uid.longValue(),
          row.get("lookup_cursor") == null ? null : number(row, "lookup_cursor"));
    }
    boolean present = (Boolean) row.get("desired_present");
    SearchDocument document = null;
    if (present) {
      document =
          documents
              .findById(documentId)
              .filter(
                  d ->
                      workspaceId.equals(d.workspaceId())
                          && fileId.equals(SearchDocumentRepository.sourceFileId(d.metadata())))
              .orElse(null);
      if (document == null) {
        jdbc.update(
            "update drive_search_remote_documents set desired_present=false where workspace_id=?"
                + " and document_id=?",
            workspaceId,
            documentId);
        present = false;
      }
    }
    operation = UUID.randomUUID().toString();
    jdbc.update(
        "update drive_search_remote_documents set"
            + " operation_id=?,operation_revision=desired_revision,operation_present=?,operation_started_at=?,operation_state='SUBMITTING',task_uid=null,lookup_cursor=null"
            + " where workspace_id=? and document_id=?",
        operation,
        present,
        time(clock.instant()),
        workspaceId,
        documentId);
    return new Action(
        "SUBMIT",
        workspaceId,
        fileId,
        documentId,
        operation,
        present,
        document,
        clock.instant(),
        null,
        null);
  }

  private boolean permitted(SearchDocumentRepository.SourceReference source) {
    if (source.sourceType().contains("invoice_extraction"))
      return Boolean.TRUE.equals(
          jdbc.queryForObject(
              "select exists(select 1 from invoice_extractions where workspace_id=? and file_id=?"
                  + " and extraction_id=? and status='confirmed')",
              Boolean.class,
              source.workspaceId(),
              source.fileId(),
              source.sourceId()));
    return true;
  }

  private void accepted(Action action, long uid) {
    jdbc.update(
        "update drive_search_remote_documents set"
            + " task_uid=?,operation_state='PENDING',lookup_cursor=null where operation_id=? and"
            + " (task_uid is null or task_uid=?)",
        uid,
        action.operationId(),
        uid);
  }

  private void finish(Action action, boolean success) {
    transactions.executeWithoutResult(
        status -> {
          jdbc.queryForList(
              "select workspace_id from workspaces where workspace_id=? for update",
              String.class,
              action.workspaceId());
          if (success)
            jdbc.update(
                "update drive_search_remote_documents set"
                    + " completed_revision=operation_revision,completed_present=operation_present,operation_id=null,operation_state=null,task_uid=null,lookup_cursor=null"
                    + " where operation_id=?",
                action.operationId());
          else
            jdbc.update(
                "update drive_search_remote_documents set"
                    + " operation_id=null,operation_state=null,task_uid=null,lookup_cursor=null"
                    + " where operation_id=?",
                action.operationId());
          if (success
              && action.present()
              && projectionReady(action.workspaceId(), action.fileId(), action.documentId())) {
            var source = documents.findSourceById(action.documentId()).orElse(null);
            if (source != null
                && fence.isActiveFile(action.workspaceId(), action.fileId())
                && permitted(source)) documents.complete(action.documentId(), clock.instant());
            else
              jdbc.update(
                  "update drive_search_remote_documents set desired_present=false where"
                      + " workspace_id=? and document_id=?",
                  action.workspaceId(),
                  action.documentId());
          }
          markReconciled(action.workspaceId(), action.fileId());
        });
  }

  private void markReconciled(String workspaceId, String fileId) {
    jdbc.update(
        """
        update drive_search_lifecycle set reconciled_revision=revision,last_error_code=null where workspace_id=? and file_id=?
        and not exists(select 1 from drive_search_remote_documents d where d.workspace_id=? and d.file_id=?
          and (d.operation_id is not null or d.completed_revision<>revision or d.completed_present<>d.desired_present))
        """,
        workspaceId,
        fileId,
        workspaceId,
        fileId);
  }

  private void error(String workspaceId, String fileId, String code) {
    jdbc.update(
        "update drive_search_lifecycle set last_error_code=?,next_attempt_at=? where workspace_id=?"
            + " and file_id=?",
        code,
        time(clock.instant().plusSeconds(10)),
        workspaceId,
        fileId);
  }

  public boolean projectionDesired(String workspaceId, String documentId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from drive_search_remote_documents where workspace_id=? and"
                + " document_id=? and desired_present=true)",
            Boolean.class,
            workspaceId,
            documentId));
  }

  public boolean projectionReady(String workspaceId, String fileId, String documentId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from drive_search_remote_documents d join"
                + " drive_search_lifecycle f on f.workspace_id=d.workspace_id and"
                + " f.file_id=d.file_id where d.workspace_id=? and d.file_id=? and d.document_id=?"
                + " and f.desired_state='ACTIVE' and d.desired_present=true and"
                + " d.completed_present=true and d.completed_revision=f.revision and d.operation_id"
                + " is null)",
            Boolean.class,
            workspaceId,
            fileId,
            documentId));
  }

  public void runDue(int limit) {
    var due =
        jdbc.queryForList(
            "select workspace_id,file_id from drive_search_lifecycle where"
                + " reconciled_revision<>revision and next_attempt_at<=? order by"
                + " next_attempt_at,workspace_id,file_id limit ?",
            time(clock.instant()),
            limit);
    for (var row : due) {
      String workspaceId = (String) row.get("workspace_id"), fileId = (String) row.get("file_id");
      try {
        reconcileFile(workspaceId, fileId);
      } catch (RuntimeException exception) {
        error(workspaceId, fileId, "REMOTE_RECONCILIATION_PENDING");
      }
    }
  }

  private static Instant instant(Object value) {
    return value instanceof java.time.OffsetDateTime offset
        ? offset.toInstant()
        : ((Timestamp) value).toInstant();
  }

  private static Timestamp time(Instant instant) {
    return Timestamp.from(instant);
  }

  private static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  private record Action(
      String kind,
      String workspaceId,
      String fileId,
      String documentId,
      String operationId,
      boolean present,
      SearchDocument document,
      Instant startedAt,
      Long taskUid,
      Long cursor) {}
}
