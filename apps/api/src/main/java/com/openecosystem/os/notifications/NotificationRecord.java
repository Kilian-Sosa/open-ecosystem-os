package com.openecosystem.os.notifications;

import java.time.Instant;

public record NotificationRecord(
    String notificationId,
    String workspaceId,
    String actorId,
    String title,
    String body,
    String severity,
    String status,
    String sourceType,
    String sourceId,
    String correlationId,
    String idempotencyKey,
    Instant createdAt,
    Instant readAt) {}
