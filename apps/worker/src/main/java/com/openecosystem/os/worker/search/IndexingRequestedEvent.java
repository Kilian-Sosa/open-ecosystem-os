package com.openecosystem.os.worker.search;

import java.time.Instant;

public record IndexingRequestedEvent(
    String eventId,
    int version,
    Instant occurredAt,
    String workspaceId,
    String actorId,
    String correlationId,
    String causationId,
    String idempotencyKey,
    String searchDocumentId,
    String sourceType,
    String sourceId,
    String resourceHref,
    int attemptCount,
    int maxAttempts,
    Instant requestedAt) {}
