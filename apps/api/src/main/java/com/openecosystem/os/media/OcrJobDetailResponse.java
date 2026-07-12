package com.openecosystem.os.media;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OcrJobDetailResponse(
    String jobId,
    String fileId,
    String fileName,
    String contentType,
    String status,
    String provider,
    int attemptCount,
    int maxAttempts,
    String extractedText,
    Integer extractedTextLength,
    String failureCode,
    String failureMessage,
    String correlationId,
    Instant queuedAt,
    Instant processingStartedAt,
    Instant completedAt,
    Instant failedAt,
    Instant nextAttemptAt,
    Instant updatedAt,
    OcrJobLifecycleResponse lifecycle,
    String extractorName,
    String extractorVersion,
    String extractionStatus,
    BigDecimal extractionAggregateConfidence,
    List<OcrExtractionWarningResponse> extractionWarnings,
    List<OcrExtractionFieldResponse> extractionFields) {}
