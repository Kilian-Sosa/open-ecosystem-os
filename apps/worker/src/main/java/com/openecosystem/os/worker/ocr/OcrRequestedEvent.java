package com.openecosystem.os.worker.ocr;

import java.time.Instant;

public record OcrRequestedEvent(
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
    String contentType,
    String storageKey,
    int attemptCount,
    int maxAttempts,
    Instant requestedAt) {}
