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
public class JdbcWorkflowRepository {

  private static final RowMapper<Workflow> WORKFLOW_ROW_MAPPER =
      JdbcWorkflowRepository::mapWorkflow;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcWorkflowRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public void insertWorkflow(Workflow workflow) {
    jdbcTemplate.update(
        """
        insert into workflows (
          workflow_id,
          workspace_id,
          name,
          description,
          status,
          current_version_id,
          current_version_number,
          created_by,
          updated_by,
          created_at,
          updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        workflow.workflowId(),
        workflow.workspaceId(),
        workflow.name(),
        workflow.description(),
        workflow.status().value(),
        workflow.currentVersionId(),
        workflow.currentVersionNumber(),
        workflow.createdBy(),
        workflow.updatedBy(),
        Timestamp.from(workflow.createdAt()),
        Timestamp.from(workflow.updatedAt()));
  }

  public void insertVersion(WorkflowVersion version) {
    jdbcTemplate.update(
        """
        insert into workflow_versions (
          version_id,
          workflow_id,
          workspace_id,
          version_number,
          definition_json,
          created_by,
          created_at,
          published_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        version.versionId(),
        version.workflowId(),
        version.workspaceId(),
        version.versionNumber(),
        json(version.definition()),
        version.createdBy(),
        Timestamp.from(version.createdAt()),
        version.publishedAt() == null ? null : Timestamp.from(version.publishedAt()));
  }

  public void updateCurrentVersion(
      String workflowId,
      String name,
      String description,
      WorkflowStatus status,
      String currentVersionId,
      int currentVersionNumber,
      String updatedBy,
      Instant updatedAt) {
    jdbcTemplate.update(
        """
        update workflows
        set name = ?,
            description = ?,
            status = ?,
            current_version_id = ?,
            current_version_number = ?,
            updated_by = ?,
            updated_at = ?
        where workflow_id = ?
        """,
        name,
        description,
        status.value(),
        currentVersionId,
        currentVersionNumber,
        updatedBy,
        Timestamp.from(updatedAt),
        workflowId);
  }

  public List<WorkflowWithVersion> listByWorkspace(String workspaceId) {
    return jdbcTemplate.query(
        """
        select
          w.workflow_id,
          w.workspace_id,
          w.name,
          w.description,
          w.status,
          w.current_version_id,
          w.current_version_number,
          w.created_by,
          w.updated_by,
          w.created_at,
          w.updated_at,
          v.version_id,
          v.version_number,
          v.definition_json,
          v.created_by as version_created_by,
          v.created_at as version_created_at,
          v.published_at
        from workflows w
        join workflow_versions v on v.version_id = w.current_version_id
        where w.workspace_id = ?
        order by w.updated_at desc
        """,
        this::mapWorkflowWithVersion,
        workspaceId);
  }

  public List<WorkflowWithVersion> listActiveByWorkspace(String workspaceId) {
    return jdbcTemplate.query(
        """
        select
          w.workflow_id,
          w.workspace_id,
          w.name,
          w.description,
          w.status,
          w.current_version_id,
          w.current_version_number,
          w.created_by,
          w.updated_by,
          w.created_at,
          w.updated_at,
          v.version_id,
          v.version_number,
          v.definition_json,
          v.created_by as version_created_by,
          v.created_at as version_created_at,
          v.published_at
        from workflows w
        join workflow_versions v on v.version_id = w.current_version_id
        where w.workspace_id = ? and w.status = ?
        order by w.updated_at desc
        """,
        this::mapWorkflowWithVersion,
        workspaceId,
        WorkflowStatus.ACTIVE.value());
  }

  public Optional<WorkflowWithVersion> findByIdForWorkspace(String workflowId, String workspaceId) {
    List<WorkflowWithVersion> results =
        jdbcTemplate.query(
            """
            select
              w.workflow_id,
              w.workspace_id,
              w.name,
              w.description,
              w.status,
              w.current_version_id,
              w.current_version_number,
              w.created_by,
              w.updated_by,
              w.created_at,
              w.updated_at,
              v.version_id,
              v.version_number,
              v.definition_json,
              v.created_by as version_created_by,
              v.created_at as version_created_at,
              v.published_at
            from workflows w
            join workflow_versions v on v.version_id = w.current_version_id
            where w.workflow_id = ? and w.workspace_id = ?
            """,
            this::mapWorkflowWithVersion,
            workflowId,
            workspaceId);
    return results.stream().findFirst();
  }

  public Optional<Workflow> findWorkflowByIdForWorkspace(String workflowId, String workspaceId) {
    List<Workflow> results =
        jdbcTemplate.query(
            """
            select *
            from workflows
            where workflow_id = ? and workspace_id = ?
            """,
            WORKFLOW_ROW_MAPPER,
            workflowId,
            workspaceId);
    return results.stream().findFirst();
  }

  private WorkflowWithVersion mapWorkflowWithVersion(ResultSet resultSet, int rowNumber)
      throws SQLException {
    Workflow workflow = mapWorkflow(resultSet, rowNumber);
    WorkflowVersion version =
        new WorkflowVersion(
            resultSet.getString("version_id"),
            workflow.workflowId(),
            workflow.workspaceId(),
            resultSet.getInt("version_number"),
            jsonNode(resultSet.getString("definition_json")),
            resultSet.getString("version_created_by"),
            resultSet.getTimestamp("version_created_at").toInstant(),
            instantOrNull(resultSet, "published_at"));
    return new WorkflowWithVersion(workflow, version);
  }

  private static Workflow mapWorkflow(ResultSet resultSet, int rowNumber) throws SQLException {
    return new Workflow(
        resultSet.getString("workflow_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("name"),
        resultSet.getString("description"),
        WorkflowStatus.fromValue(resultSet.getString("status")),
        resultSet.getString("current_version_id"),
        resultSet.getInt("current_version_number"),
        resultSet.getString("created_by"),
        resultSet.getString("updated_by"),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant());
  }

  private JsonNode jsonNode(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Workflow definition could not be parsed", exception);
    }
  }

  private String json(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Workflow definition could not be serialized", exception);
    }
  }

  private static Instant instantOrNull(ResultSet resultSet, String column) throws SQLException {
    Timestamp timestamp = resultSet.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }
}
