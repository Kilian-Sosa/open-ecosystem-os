package com.openecosystem.os.drive;

import com.openecosystem.os.common.events.EventEnvelope;
import com.openecosystem.os.common.events.JdbcEventOutboxRepository;
import com.openecosystem.os.common.ids.Ids;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriveSearchLifecycle {
  private final JdbcTemplate jdbc;
  private final JdbcEventOutboxRepository outbox;

  public DriveSearchLifecycle(JdbcTemplate jdbc, JdbcEventOutboxRepository outbox) {
    this.jdbc = jdbc;
    this.outbox = outbox;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public long transition(
      String workspaceId,
      String fileId,
      String desiredState,
      String actorId,
      String correlationId,
      Instant now) {
    var revisions =
        jdbc.queryForList(
            "select revision from drive_search_lifecycle where workspace_id=? and file_id=?",
            Long.class,
            workspaceId,
            fileId);
    long revision = revisions.isEmpty() ? 1 : revisions.getFirst() + 1;
    if (revisions.isEmpty())
      jdbc.update(
          "insert into"
              + " drive_search_lifecycle(workspace_id,file_id,revision,desired_state,next_attempt_at)"
              + " values (?,?,?,?,?)",
          workspaceId,
          fileId,
          revision,
          desiredState,
          java.sql.Timestamp.from(now));
    else
      jdbc.update(
          "update drive_search_lifecycle set"
              + " revision=?,desired_state=?,next_attempt_at=?,last_error_code=null where"
              + " workspace_id=? and file_id=?",
          revision,
          desiredState,
          java.sql.Timestamp.from(now),
          workspaceId,
          fileId);
    outbox.save(
        new EventEnvelope<>(
            Ids.newId("evt"),
            "DriveFileLifecycleChanged",
            1,
            now,
            workspaceId,
            actorId,
            correlationId,
            null,
            "drive",
            "drive:" + fileId + ":lifecycle:" + revision + ":v1",
            Map.of("fileId", fileId, "revision", revision, "desiredState", desiredState)));
    return revision;
  }

  public boolean cleanupComplete(String workspaceId, String fileId, long revision) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists(select 1 from drive_search_lifecycle where workspace_id=? and file_id=?"
                + " and revision=? and desired_state='ABSENT' and reconciled_revision=revision)",
            Boolean.class,
            workspaceId,
            fileId,
            revision));
  }
}
