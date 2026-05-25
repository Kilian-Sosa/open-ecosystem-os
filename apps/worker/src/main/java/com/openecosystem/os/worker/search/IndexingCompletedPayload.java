package com.openecosystem.os.worker.search;

import java.time.Instant;

public record IndexingCompletedPayload(
    String searchDocumentId,
    String sourceType,
    String sourceId,
    String resourceHref,
    int attemptCount,
    Instant indexedAt) {}
