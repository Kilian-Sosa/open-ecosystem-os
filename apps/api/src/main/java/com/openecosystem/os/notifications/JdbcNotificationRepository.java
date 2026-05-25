package com.openecosystem.os.notifications;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationRepository {

  private static final RowMapper<NotificationRecord> ROW_MAPPER =
      JdbcNotificationRepository::mapRow;

  private final JdbcTemplate jdbcTemplate;

  public JdbcNotificationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void save(NotificationRecord notification) {
    jdbcTemplate.update(
        """
        insert into notifications (
          notification_id,
          workspace_id,
          actor_id,
          title,
          body,
          severity,
          status,
          source_type,
          source_id,
          correlation_id,
          idempotency_key,
          created_at,
          read_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        notification.notificationId(),
        notification.workspaceId(),
        notification.actorId(),
        notification.title(),
        notification.body(),
        notification.severity(),
        notification.status(),
        notification.sourceType(),
        notification.sourceId(),
        notification.correlationId(),
        notification.idempotencyKey(),
        Timestamp.from(notification.createdAt()),
        notification.readAt() == null ? null : Timestamp.from(notification.readAt()));
  }

  public List<NotificationRecord> listByWorkspace(String workspaceId, String correlationId) {
    if (correlationId == null || correlationId.isBlank()) {
      return jdbcTemplate.query(
          """
          select *
          from notifications
          where workspace_id = ?
          order by created_at desc
          limit 50
          """,
          ROW_MAPPER,
          workspaceId);
    }
    return jdbcTemplate.query(
        """
        select *
        from notifications
        where workspace_id = ? and correlation_id = ?
        order by created_at desc
        limit 50
        """,
        ROW_MAPPER,
        workspaceId,
        correlationId);
  }

  public Optional<NotificationRecord> findLatestByCorrelationId(
      String workspaceId, String correlationId) {
    List<NotificationRecord> results =
        jdbcTemplate.query(
            """
            select *
            from notifications
            where workspace_id = ? and correlation_id = ?
            order by created_at desc
            limit 1
            """,
            ROW_MAPPER,
            workspaceId,
            correlationId);
    return results.stream().findFirst();
  }

  private static NotificationRecord mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
    Timestamp readAt = resultSet.getTimestamp("read_at");
    return new NotificationRecord(
        resultSet.getString("notification_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("actor_id"),
        resultSet.getString("title"),
        resultSet.getString("body"),
        resultSet.getString("severity"),
        resultSet.getString("status"),
        resultSet.getString("source_type"),
        resultSet.getString("source_id"),
        resultSet.getString("correlation_id"),
        resultSet.getString("idempotency_key"),
        resultSet.getTimestamp("created_at").toInstant(),
        readAt == null ? null : readAt.toInstant());
  }
}
