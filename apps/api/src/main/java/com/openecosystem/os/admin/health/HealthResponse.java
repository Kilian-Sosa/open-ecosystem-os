package com.openecosystem.os.admin.health;

import java.time.Instant;

public record HealthResponse(
    String status, String service, Instant checkedAt, String correlationId) {}
