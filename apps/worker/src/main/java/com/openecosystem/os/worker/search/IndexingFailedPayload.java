package com.openecosystem.os.worker.search;

import java.time.Instant;

public record IndexingFailedPayload(
    String searchDocumentId,
    String sourceType,
    String sourceId,
    String resourceHref,
    int attemptCount,
    int maxAttempts,
    String errorCode,
    String errorMessage,
    Instant failedAt) {}
