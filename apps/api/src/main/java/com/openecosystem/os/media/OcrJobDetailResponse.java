package com.openecosystem.os.media;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

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
    @JsonInclude(JsonInclude.Include.ALWAYS) OcrResultResponse ocrResult,
    @JsonInclude(JsonInclude.Include.ALWAYS) OcrExtractionResponse extraction) {}
