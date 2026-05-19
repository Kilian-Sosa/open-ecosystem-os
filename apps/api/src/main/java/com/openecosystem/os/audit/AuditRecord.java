package com.openecosystem.os.audit;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record AuditRecord(
    String auditId,
    String action,
    String resourceType,
    String resourceId,
    String workspaceId,
    String actorId,
    String correlationId,
    Instant occurredAt,
    AuditOutcome outcome,
    Map<String, String> attributes) {

  public AuditRecord {
    auditId = requireText(auditId, "auditId");
    action = requireText(action, "action");
    resourceType = requireText(resourceType, "resourceType");
    workspaceId = requireText(workspaceId, "workspaceId");
    actorId = requireText(actorId, "actorId");
    correlationId = requireText(correlationId, "correlationId");
    occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    outcome = Objects.requireNonNull(outcome, "outcome must not be null");
    attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(fieldName + " must not be blank");
    return value;
  }
}
