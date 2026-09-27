package com.openecosystem.os.media;

import java.time.Instant;

public record OcrJobSummary(
    String jobId,
    String fileId,
    String workspaceId,
    String contentType,
    OcrJobStatus status,
    String provider,
    int attemptCount,
    int maxAttempts,
    Integer extractedTextLength,
    String failureCode,
    String correlationId,
    Instant queuedAt,
    Instant processingStartedAt,
    Instant completedAt,
    Instant failedAt,
    Instant updatedAt) {}
