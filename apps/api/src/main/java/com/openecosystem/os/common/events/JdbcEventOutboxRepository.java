package com.openecosystem.os.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEventOutboxRepository {

  private static final RowMapper<OutboxEvent> ROW_MAPPER = JdbcEventOutboxRepository::mapRow;

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

  public List<OutboxEvent> findUnpublished(int limit) {
    return jdbcTemplate.query(
        """
        select event_id, event_type, envelope_json
        from event_outbox
        where published_at is null
        order by created_at
        limit ?
        """,
        ROW_MAPPER,
        limit);
  }

  public void markPublished(String eventId, Instant publishedAt) {
    jdbcTemplate.update(
        """
        update event_outbox
        set published_at = ?
        where event_id = ? and published_at is null
        """,
        Timestamp.from(publishedAt),
        eventId);
  }

  private String json(Object value, String message) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(message, exception);
    }
  }

  private static OutboxEvent mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
    return new OutboxEvent(
        resultSet.getString("event_id"),
        resultSet.getString("event_type"),
        resultSet.getString("envelope_json"));
  }

  public record OutboxEvent(String eventId, String eventType, String envelopeJson) {}
}
