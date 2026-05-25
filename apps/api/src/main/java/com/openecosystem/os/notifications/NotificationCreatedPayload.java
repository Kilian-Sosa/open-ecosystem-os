package com.openecosystem.os.notifications;

import java.time.Instant;

public record NotificationCreatedPayload(
    String notificationId,
    String title,
    String severity,
    String sourceType,
    String sourceId,
    Instant createdAt) {}
