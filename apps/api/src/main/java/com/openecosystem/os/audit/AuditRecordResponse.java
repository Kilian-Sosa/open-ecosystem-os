package com.openecosystem.os.audit;

import java.time.Instant;
import java.util.Map;

public record AuditRecordResponse(
    String auditId,
    String action,
    String resourceType,
    String resourceId,
    String actorId,
    String correlationId,
    String outcome,
    Map<String, String> attributes,
    Instant occurredAt) {}
