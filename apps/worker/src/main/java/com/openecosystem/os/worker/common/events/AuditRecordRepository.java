package com.openecosystem.os.worker.common.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditRecordRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public AuditRecordRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public void save(
      String auditId,
      String action,
      String resourceType,
      String resourceId,
      String workspaceId,
      String actorId,
      String correlationId,
      Instant occurredAt,
      String outcome,
      Map<String, String> attributes) {
    jdbcTemplate.update(
        """
        insert into audit_records (
          audit_id,
          action,
          resource_type,
          resource_id,
          workspace_id,
          actor_id,
          correlation_id,
          occurred_at,
          outcome,
          attributes_json
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        auditId,
        action,
        resourceType,
        resourceId,
        workspaceId,
        actorId,
        correlationId,
        Timestamp.from(occurredAt),
        outcome,
        attributesJson(attributes));
  }

  private String attributesJson(Map<String, String> attributes) {
    try {
      return objectMapper.writeValueAsString(attributes == null ? Map.of() : attributes);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Audit attributes could not be serialized", exception);
    }
  }
}
