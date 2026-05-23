package com.openecosystem.os.worker.ocr;

import java.time.Instant;

public record OcrStartedPayload(
    String jobId,
    String fileId,
    String provider,
    int attemptCount,
    int maxAttempts,
    Instant startedAt) {}
