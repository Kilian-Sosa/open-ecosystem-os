package com.openecosystem.os.worker.health;

import java.time.Instant;
import java.util.Map;

public record WorkerReadinessResponse(
    String status, String readiness, Map<String, String> dependencies, Instant checkedAt) {}
