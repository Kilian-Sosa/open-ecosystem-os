package com.openecosystem.os.notifications;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNotificationRepository {

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
}
