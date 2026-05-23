package com.openecosystem.os.worker.ocr;

import java.time.Instant;

public record OcrFailedPayload(
    String jobId,
    String fileId,
    String provider,
    int attemptCount,
    int maxAttempts,
    String errorCode,
    String errorMessage,
    Instant failedAt) {}
