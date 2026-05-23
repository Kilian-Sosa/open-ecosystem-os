package com.openecosystem.os.worker.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEventOutboxRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcEventOutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public void save(EventEnvelope<?> envelope) {
    String payloadJson = json(envelope.payload(), "Event payload could not be serialized");
    String envelopeJson = json(envelope, "Event envelope could not be serialized");

    jdbcTemplate.update(
        """
        insert into event_outbox (
          event_id,
          event_type,
          version,
          occurred_at,
          workspace_id,
          actor_id,
          correlation_id,
          causation_id,
          source,
          idempotency_key,
          payload_json,
          envelope_json,
          published_at,
          created_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, null, ?)
        """,
        envelope.eventId(),
        envelope.eventType(),
        envelope.version(),
        Timestamp.from(envelope.occurredAt()),
        envelope.workspaceId(),
        envelope.actorId(),
        envelope.correlationId(),
        envelope.causationId(),
        envelope.source(),
        envelope.idempotencyKey(),
        payloadJson,
        envelopeJson,
        Timestamp.from(envelope.occurredAt()));
  }

  private String json(Object value, String message) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(message, exception);
    }
  }
}
