package com.openecosystem.os.media;

import java.time.Instant;

public record OcrJobSummaryResponse(
    String jobId,
    String fileId,
    String fileName,
    String contentType,
    String status,
    String provider,
    int attemptCount,
    int maxAttempts,
    Integer extractedTextLength,
    String failureCode,
    String failureMessage,
    String correlationId,
    Instant queuedAt,
    Instant processingStartedAt,
    Instant completedAt,
    Instant failedAt,
    Instant updatedAt) {}
