package com.openecosystem.os.media;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OcrJobLifecycleQueryRepository {

  private final JdbcTemplate jdbcTemplate;
  private final NamedParameterJdbcTemplate namedJdbcTemplate;

  public OcrJobLifecycleQueryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
    this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
  }

  public List<EventRow> findEvents(String workspaceId, String correlationId) {
    return jdbcTemplate.query(
        """
        select
          e.event_id,
          e.event_type,
          e.version,
          e.occurred_at,
          e.correlation_id,
          e.causation_id,
          e.source,
          e.published_at,
          c.consumer_name,
          c.consumed_at
        from event_outbox e
        left join event_consumptions c on c.event_id = e.event_id
        where e.workspace_id = ? and e.correlation_id = ?
        order by e.occurred_at, e.event_id, c.consumed_at, c.consumer_name
        """,
        OcrJobLifecycleQueryRepository::mapEvent,
        workspaceId,
        correlationId);
  }

  public List<WorkflowExecutionRow> findWorkflowExecutions(
      String workspaceId, Set<String> sourceEventIds) {
    if (sourceEventIds.isEmpty()) return List.of();
    return namedJdbcTemplate.query(
        """
        select
          execution_id,
          workflow_id,
          workflow_version_id,
          workflow_version_number,
          source_event_id,
          status,
          retry_count,
          started_at,
          completed_at,
          failed_at
        from workflow_executions
        where workspace_id = :workspaceId and source_event_id in (:sourceEventIds)
        order by started_at, execution_id
        """,
        new MapSqlParameterSource(
                Map.of("workspaceId", workspaceId, "sourceEventIds", sourceEventIds))
            .getValues(),
        OcrJobLifecycleQueryRepository::mapWorkflowExecution);
  }

  public List<WorkflowStepRow> findWorkflowSteps(String workspaceId, Set<String> executionIds) {
    if (executionIds.isEmpty()) return List.of();
    return namedJdbcTemplate.query(
        """
        select
          step_execution_id,
          execution_id,
          workflow_id,
          step_key,
          action_type,
          status,
          retry_count,
          started_at,
          completed_at,
          failed_at
        from workflow_step_executions
        where workspace_id = :workspaceId and execution_id in (:executionIds)
        order by started_at, step_execution_id
        """,
        new MapSqlParameterSource(Map.of("workspaceId", workspaceId, "executionIds", executionIds))
            .getValues(),
        OcrJobLifecycleQueryRepository::mapWorkflowStep);
  }

  public List<AuditRow> findAudits(
      String workspaceId, String correlationId, Set<String> resourceIds) {
    if (resourceIds.isEmpty()) return List.of();
    return namedJdbcTemplate.query(
        """
        select
          audit_id,
          action,
          resource_type,
          resource_id,
          occurred_at,
          outcome
        from audit_records
        where workspace_id = :workspaceId
          and correlation_id = :correlationId
          and resource_id in (:resourceIds)
        order by occurred_at, audit_id
        """,
        new MapSqlParameterSource(
                Map.of(
                    "workspaceId", workspaceId,
                    "correlationId", correlationId,
                    "resourceIds", resourceIds))
            .getValues(),
        OcrJobLifecycleQueryRepository::mapAudit);
  }

  private static EventRow mapEvent(ResultSet resultSet, int rowNumber) throws SQLException {
    return new EventRow(
        resultSet.getString("event_id"),
        resultSet.getString("event_type"),
        resultSet.getInt("version"),
        resultSet.getTimestamp("occurred_at").toInstant(),
        resultSet.getString("correlation_id"),
        resultSet.getString("causation_id"),
        resultSet.getString("source"),
        instantOrNull(resultSet, "published_at"),
        resultSet.getString("consumer_name"),
        instantOrNull(resultSet, "consumed_at"));
  }

  private static WorkflowExecutionRow mapWorkflowExecution(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new WorkflowExecutionRow(
        resultSet.getString("execution_id"),
        resultSet.getString("workflow_id"),
        resultSet.getString("workflow_version_id"),
        resultSet.getInt("workflow_version_number"),
        resultSet.getString("source_event_id"),
        resultSet.getString("status"),
        resultSet.getInt("retry_count"),
        resultSet.getTimestamp("started_at").toInstant(),
        instantOrNull(resultSet, "completed_at"),
        instantOrNull(resultSet, "failed_at"));
  }

  private static WorkflowStepRow mapWorkflowStep(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new WorkflowStepRow(
        resultSet.getString("step_execution_id"),
        resultSet.getString("execution_id"),
        resultSet.getString("workflow_id"),
        resultSet.getString("step_key"),
        resultSet.getString("action_type"),
        resultSet.getString("status"),
        resultSet.getInt("retry_count"),
        resultSet.getTimestamp("started_at").toInstant(),
        instantOrNull(resultSet, "completed_at"),
        instantOrNull(resultSet, "failed_at"));
  }

  private static AuditRow mapAudit(ResultSet resultSet, int rowNumber) throws SQLException {
    return new AuditRow(
        resultSet.getString("audit_id"),
        resultSet.getString("action"),
        resultSet.getString("resource_type"),
        resultSet.getString("resource_id"),
        resultSet.getTimestamp("occurred_at").toInstant(),
        resultSet.getString("outcome"));
  }

  private static Instant instantOrNull(ResultSet resultSet, String column) throws SQLException {
    Timestamp timestamp = resultSet.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  public record EventRow(
      String eventId,
      String eventType,
      int version,
      Instant occurredAt,
      String correlationId,
      String causationId,
      String source,
      Instant publishedAt,
      String consumerName,
      Instant consumedAt) {}

  public record WorkflowExecutionRow(
      String executionId,
      String workflowId,
      String workflowVersionId,
      int workflowVersionNumber,
      String sourceEventId,
      String status,
      int retryCount,
      Instant startedAt,
      Instant completedAt,
      Instant failedAt) {}

  public record WorkflowStepRow(
      String stepExecutionId,
      String executionId,
      String workflowId,
      String stepKey,
      String actionType,
      String status,
      int retryCount,
      Instant startedAt,
      Instant completedAt,
      Instant failedAt) {}

  public record AuditRow(
      String auditId,
      String action,
      String resourceType,
      String resourceId,
      Instant occurredAt,
      String outcome) {}
}
