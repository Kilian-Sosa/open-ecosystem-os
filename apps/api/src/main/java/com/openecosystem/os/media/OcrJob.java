package com.openecosystem.os.media;

import java.time.Instant;

public record OcrJob(
    String jobId,
    String fileId,
    String workspaceId,
    String actorId,
    String sourceEventId,
    String correlationId,
    String contentType,
    String storageKey,
    OcrJobStatus status,
    String provider,
    int attemptCount,
    int maxAttempts,
    String extractedText,
    Integer extractedTextLength,
    String failureCode,
    String failureMessage,
    Instant queuedAt,
    Instant processingStartedAt,
    Instant completedAt,
    Instant failedAt,
    Instant nextAttemptAt,
    Instant createdAt,
    Instant updatedAt) {}
