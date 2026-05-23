package com.openecosystem.os.media;

import java.time.Instant;

public record FileUploadedEvent(
    String eventId,
    int version,
    Instant occurredAt,
    String workspaceId,
    String actorId,
    String correlationId,
    String idempotencyKey,
    String fileId,
    String contentType,
    long sizeBytes,
    String storageKey,
    Instant uploadedAt) {}
