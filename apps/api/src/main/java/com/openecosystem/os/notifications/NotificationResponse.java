package com.openecosystem.os.notifications;

import java.time.Instant;

public record NotificationResponse(
    String notificationId,
    String title,
    String body,
    String severity,
    String status,
    String sourceType,
    String sourceId,
    String correlationId,
    Instant createdAt,
    Instant readAt) {}
