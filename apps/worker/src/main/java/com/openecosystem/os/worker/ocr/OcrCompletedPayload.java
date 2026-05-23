package com.openecosystem.os.worker.ocr;

import java.time.Instant;

public record OcrCompletedPayload(
    String jobId,
    String fileId,
    String provider,
    int attemptCount,
    int extractedTextLength,
    Instant completedAt) {}
