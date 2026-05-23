package com.openecosystem.os.media;

import java.time.Instant;

public record OcrRequestedPayload(
    String jobId,
    String fileId,
    String contentType,
    String storageKey,
    int attemptCount,
    int maxAttempts,
    Instant requestedAt) {}
