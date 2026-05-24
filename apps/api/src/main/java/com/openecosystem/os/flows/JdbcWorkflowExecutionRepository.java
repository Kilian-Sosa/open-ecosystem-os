package com.openecosystem.os.flows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkflowExecutionRepository {

  private static final RowMapper<WorkflowExecution> EXECUTION_ROW_MAPPER =
      JdbcWorkflowExecutionRepository::mapExecution;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcWorkflowExecutionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public void insertExecution(WorkflowExecution execution) {
    jdbcTemplate.update(
        """
        insert into workflow_executions (
          execution_id,
          workflow_id,
          workflow_version_id,
          workflow_version_number,
          workspace_id,
          actor_id,
          correlation_id,
          trigger_type,
          source_event_id,
          source_event_type,
          trigger_idempotency_key,
          status,
          retry_count,
          failure_reason,
          started_at,
          completed_at,
          failed_at,
          created_at,
          updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        execution.executionId(),
        execution.workflowId(),
        execution.workflowVersionId(),
        execution.workflowVersionNumber(),
        execution.workspaceId(),
        execution.actorId(),
        execution.correlationId(),
        execution.triggerType().value(),
        execution.sourceEventId(),
        execution.sourceEventType(),
        execution.triggerIdempotencyKey(),
        execution.status().value(),
        execution.retryCount(),
        execution.failureReason(),
        Timestamp.from(execution.startedAt()),
        execution.completedAt() == null ? null : Timestamp.from(execution.completedAt()),
        execution.failedAt() == null ? null : Timestamp.from(execution.failedAt()),
        Timestamp.from(execution.createdAt()),
        Timestamp.from(execution.updatedAt()));
  }

  public void completeExecution(String executionId, Instant completedAt) {
    jdbcTemplate.update(
        """
        update workflow_executions
        set status = ?,
            completed_at = ?,
            failed_at = null,
            failure_reason = null,
            updated_at = ?
        where execution_id = ?
        """,
        WorkflowExecutionStatus.COMPLETED.value(),
        Timestamp.from(completedAt),
        Timestamp.from(completedAt),
        executionId);
  }

  public void failExecution(String executionId, String failureReason, Instant failedAt) {
    jdbcTemplate.update(
        """
        update workflow_executions
        set status = ?,
            retry_count = retry_count + 1,
            failure_reason = ?,
            failed_at = ?,
            updated_at = ?
        where execution_id = ?
        """,
        WorkflowExecutionStatus.FAILED.value(),
        trim(failureReason),
        Timestamp.from(failedAt),
        Timestamp.from(failedAt),
        executionId);
  }

  public void insertStep(WorkflowStepExecution step) {
    jdbcTemplate.update(
        """
        insert into workflow_step_executions (
          step_execution_id,
          execution_id,
          workflow_id,
          workspace_id,
          step_key,
          step_name,
          action_type,
          status,
          retry_count,
          failure_reason,
          input_json,
          output_json,
          started_at,
          completed_at,
          failed_at,
          created_at,
          updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        step.stepExecutionId(),
        step.executionId(),
        step.workflowId(),
        step.workspaceId(),
        step.stepKey(),
        step.stepName(),
        step.actionType(),
        step.status().value(),
        step.retryCount(),
        step.failureReason(),
        json(step.input()),
        json(step.output()),
        Timestamp.from(step.startedAt()),
        step.completedAt() == null ? null : Timestamp.from(step.completedAt()),
        step.failedAt() == null ? null : Timestamp.from(step.failedAt()),
        Timestamp.from(step.createdAt()),
        Timestamp.from(step.updatedAt()));
  }

  public void completeStep(String stepExecutionId, JsonNode output, Instant completedAt) {
    jdbcTemplate.update(
        """
        update workflow_step_executions
        set status = ?,
            output_json = ?,
            completed_at = ?,
            failed_at = null,
            failure_reason = null,
            updated_at = ?
        where step_execution_id = ?
        """,
        WorkflowStepExecutionStatus.COMPLETED.value(),
        json(output),
        Timestamp.from(completedAt),
        Timestamp.from(completedAt),
        stepExecutionId);
  }

  public void failStep(String stepExecutionId, String failureReason, Instant failedAt) {
    jdbcTemplate.update(
        """
        update workflow_step_executions
        set status = ?,
            retry_count = retry_count + 1,
            failure_reason = ?,
            failed_at = ?,
            updated_at = ?
        where step_execution_id = ?
        """,
        WorkflowStepExecutionStatus.FAILED.value(),
        trim(failureReason),
        Timestamp.from(failedAt),
        Timestamp.from(failedAt),
        stepExecutionId);
  }

  public List<WorkflowExecution> listByWorkspace(String workspaceId) {
    return jdbcTemplate.query(
        """
        select *
        from workflow_executions
        where workspace_id = ?
        order by created_at desc
        """,
        EXECUTION_ROW_MAPPER,
        workspaceId);
  }

  public Optional<WorkflowExecution> findByIdForWorkspace(String executionId, String workspaceId) {
    List<WorkflowExecution> results =
        jdbcTemplate.query(
            """
            select *
            from workflow_executions
            where execution_id = ? and workspace_id = ?
            """,
            EXECUTION_ROW_MAPPER,
            executionId,
            workspaceId);
    return results.stream().findFirst();
  }

  public Optional<WorkflowExecution> findByTriggerIdempotencyKey(
      String workspaceId, String triggerIdempotencyKey) {
    List<WorkflowExecution> results =
        jdbcTemplate.query(
            """
            select *
            from workflow_executions
            where workspace_id = ? and trigger_idempotency_key = ?
            """,
            EXECUTION_ROW_MAPPER,
            workspaceId,
            triggerIdempotencyKey);
    return results.stream().findFirst();
  }

  public List<WorkflowStepExecution> listSteps(String executionId) {
    return jdbcTemplate.query(
        """
        select *
        from workflow_step_executions
        where execution_id = ?
        order by started_at
        """,
        this::mapStep,
        executionId);
  }

  private static WorkflowExecution mapExecution(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new WorkflowExecution(
        resultSet.getString("execution_id"),
        resultSet.getString("workflow_id"),
        resultSet.getString("workflow_version_id"),
        resultSet.getInt("workflow_version_number"),
        resultSet.getString("workspace_id"),
        resultSet.getString("actor_id"),
        resultSet.getString("correlation_id"),
        WorkflowTriggerType.valueOf(resultSet.getString("trigger_type").toUpperCase()),
        resultSet.getString("source_event_id"),
        resultSet.getString("source_event_type"),
        resultSet.getString("trigger_idempotency_key"),
        WorkflowExecutionStatus.fromValue(resultSet.getString("status")),
        resultSet.getInt("retry_count"),
        resultSet.getString("failure_reason"),
        resultSet.getTimestamp("started_at").toInstant(),
        instantOrNull(resultSet, "completed_at"),
        instantOrNull(resultSet, "failed_at"),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant());
  }

  private WorkflowStepExecution mapStep(ResultSet resultSet, int rowNumber) throws SQLException {
    return new WorkflowStepExecution(
        resultSet.getString("step_execution_id"),
        resultSet.getString("execution_id"),
        resultSet.getString("workflow_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("step_key"),
        resultSet.getString("step_name"),
        resultSet.getString("action_type"),
        WorkflowStepExecutionStatus.fromValue(resultSet.getString("status")),
        resultSet.getInt("retry_count"),
        resultSet.getString("failure_reason"),
        jsonNode(resultSet.getString("input_json")),
        jsonNode(resultSet.getString("output_json")),
        resultSet.getTimestamp("started_at").toInstant(),
        instantOrNull(resultSet, "completed_at"),
        instantOrNull(resultSet, "failed_at"),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant());
  }

  private JsonNode jsonNode(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Workflow execution JSON could not be parsed", exception);
    }
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Workflow execution JSON could not be serialized", exception);
    }
  }

  private static Instant instantOrNull(ResultSet resultSet, String column) throws SQLException {
    Timestamp timestamp = resultSet.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  private static String trim(String value) {
    if (value == null) return null;
    return value.length() > 512 ? value.substring(0, 512) : value;
  }
}
