package com.openecosystem.os.search;

import java.time.Instant;

public record IndexingRequestedPayload(
    String searchDocumentId,
    String sourceType,
    String sourceId,
    String resourceHref,
    int attemptCount,
    int maxAttempts,
    Instant requestedAt) {}
