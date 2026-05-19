package com.openecosystem.os.admin.health;

import java.time.Instant;
import java.util.Map;

public record ReadinessResponse(
    String status,
    String readinessState,
    Map<String, String> checks,
    Instant checkedAt,
    String correlationId) {}
