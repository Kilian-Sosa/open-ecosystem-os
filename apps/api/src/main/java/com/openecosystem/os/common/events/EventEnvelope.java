package com.openecosystem.os.common.events;

import java.time.Instant;
import java.util.Objects;

public record EventEnvelope<T>(
    String eventId,
    String eventType,
    int version,
    Instant occurredAt,
    String workspaceId,
    String actorId,
    String correlationId,
    String causationId,
    String source,
    String idempotencyKey,
    T payload) {

  public EventEnvelope {
    eventId = requireText(eventId, "eventId");
    eventType = requireText(eventType, "eventType");
    if (version < 1)
      throw new IllegalArgumentException("version must be greater than zero");
    occurredAt = Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    workspaceId = requireText(workspaceId, "workspaceId");
    actorId = requireText(actorId, "actorId");
    correlationId = requireText(correlationId, "correlationId");
    source = requireText(source, "source");
    idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
    payload = Objects.requireNonNull(payload, "payload must not be null");
  }

  private static String requireText(String value, String fieldName) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(fieldName + " must not be blank");
    return value;
  }
}
