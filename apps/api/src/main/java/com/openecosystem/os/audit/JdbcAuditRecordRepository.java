package com.openecosystem.os.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditRecordRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcAuditRecordRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public void save(AuditRecord record) {
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
        record.auditId(),
        record.action(),
        record.resourceType(),
        record.resourceId(),
        record.workspaceId(),
        record.actorId(),
        record.correlationId(),
        Timestamp.from(record.occurredAt()),
        record.outcome().name(),
        attributesJson(record));
  }

  private String attributesJson(AuditRecord record) {
    try {
      return objectMapper.writeValueAsString(record.attributes());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Audit attributes could not be serialized", exception);
    }
  }
}
