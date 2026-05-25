package com.openecosystem.os.flows;

import java.time.Instant;

public record OcrCompletedEvent(
    String eventId,
    int version,
    Instant occurredAt,
    String workspaceId,
    String actorId,
    String correlationId,
    String causationId,
    String idempotencyKey,
    String jobId,
    String fileId,
    String provider,
    int attemptCount,
    int extractedTextLength,
    Instant completedAt) {}
