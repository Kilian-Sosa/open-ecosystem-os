package com.openecosystem.os.worker.health;

import java.time.Instant;

public record WorkerHealthResponse(
    String status, String service, Instant checkedAt, String correlationId) {}
