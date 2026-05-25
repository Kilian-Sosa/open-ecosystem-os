package com.openecosystem.os.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuditRecordRepository {

  private static final RowMapper<AuditRecord> ROW_MAPPER = JdbcAuditRecordRepository::mapRow;

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

  public List<AuditRecord> listByWorkspace(String workspaceId, String correlationId) {
    if (correlationId == null || correlationId.isBlank()) {
      return jdbcTemplate.query(
          """
          select *
          from audit_records
          where workspace_id = ?
          order by occurred_at desc
          limit 100
          """,
          ROW_MAPPER,
          workspaceId);
    }
    return jdbcTemplate.query(
        """
        select *
        from audit_records
        where workspace_id = ? and correlation_id = ?
        order by occurred_at desc
        limit 100
        """,
        ROW_MAPPER,
        workspaceId,
        correlationId);
  }

  public List<AuditRecord> listByCorrelationId(String workspaceId, String correlationId) {
    return listByWorkspace(workspaceId, correlationId);
  }

  private String attributesJson(AuditRecord record) {
    try {
      return objectMapper.writeValueAsString(record.attributes());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Audit attributes could not be serialized", exception);
    }
  }

  private static AuditRecord mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
    return new AuditRecord(
        resultSet.getString("audit_id"),
        resultSet.getString("action"),
        resultSet.getString("resource_type"),
        resultSet.getString("resource_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("actor_id"),
        resultSet.getString("correlation_id"),
        resultSet.getTimestamp("occurred_at").toInstant(),
        AuditOutcome.valueOf(resultSet.getString("outcome")),
        attributes(resultSet.getString("attributes_json")));
  }

  private static Map<String, String> attributes(String json) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      Map<?, ?> raw = mapper.readValue(json, LinkedHashMap.class);
      Map<String, String> attributes = new LinkedHashMap<>();
      raw.forEach((key, value) -> attributes.put(String.valueOf(key), String.valueOf(value)));
      return attributes;
    } catch (JsonProcessingException exception) {
      return Map.of();
    }
  }
}
