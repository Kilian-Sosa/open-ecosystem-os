package com.openecosystem.os.demo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDemoInvoiceRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcDemoInvoiceRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public void saveRun(DemoInvoiceRun run) {
    jdbcTemplate.update(
        """
        insert into demo_invoice_runs (
          run_id,
          workspace_id,
          actor_id,
          correlation_id,
          file_id,
          created_at,
          updated_at
        ) values (?, ?, ?, ?, ?, ?, ?)
        """,
        run.runId(),
        run.workspaceId(),
        run.actorId(),
        run.correlationId(),
        run.fileId(),
        Timestamp.from(run.createdAt()),
        Timestamp.from(run.updatedAt()));
  }

  public Optional<DemoInvoiceRun> findRunByIdForWorkspace(String runId, String workspaceId) {
    List<DemoInvoiceRun> results =
        jdbcTemplate.query(
            """
            select *
            from demo_invoice_runs
            where run_id = ? and workspace_id = ?
            """,
            this::mapRun,
            runId,
            workspaceId);
    return results.stream().findFirst();
  }

  public Optional<DemoInvoiceRun> findRunByFileIdForWorkspace(String fileId, String workspaceId) {
    List<DemoInvoiceRun> results =
        jdbcTemplate.query(
            """
            select *
            from demo_invoice_runs
            where file_id = ? and workspace_id = ?
            order by created_at desc
            limit 1
            """,
            this::mapRun,
            fileId,
            workspaceId);
    return results.stream().findFirst();
  }

  public void saveExtraction(DemoInvoiceExtraction extraction) {
    jdbcTemplate.update(
        """
        insert into demo_invoice_extractions (
          extraction_id,
          run_id,
          workspace_id,
          actor_id,
          file_id,
          ocr_job_id,
          workflow_execution_id,
          invoice_number,
          supplier_name,
          supplier_test_nif,
          supplier_test_iban,
          total_amount,
          currency,
          due_date,
          is_test_data,
          metadata_json,
          created_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        extraction.extractionId(),
        extraction.runId(),
        extraction.workspaceId(),
        extraction.actorId(),
        extraction.fileId(),
        extraction.ocrJobId(),
        extraction.workflowExecutionId(),
        extraction.invoiceNumber(),
        extraction.supplierName(),
        extraction.supplierTestNif(),
        extraction.supplierTestIban(),
        extraction.totalAmount(),
        extraction.currency(),
        Date.valueOf(extraction.dueDate()),
        extraction.testData(),
        json(extraction.metadata()),
        Timestamp.from(extraction.createdAt()));
  }

  public Optional<DemoInvoiceExtraction> findExtractionByWorkflowExecutionId(
      String workflowExecutionId) {
    List<DemoInvoiceExtraction> results =
        jdbcTemplate.query(
            """
            select *
            from demo_invoice_extractions
            where workflow_execution_id = ?
            """,
            this::mapExtraction,
            workflowExecutionId);
    return results.stream().findFirst();
  }

  public Optional<DemoInvoiceExtraction> findExtractionByRunIdForWorkspace(
      String runId, String workspaceId) {
    List<DemoInvoiceExtraction> results =
        jdbcTemplate.query(
            """
            select *
            from demo_invoice_extractions
            where run_id = ? and workspace_id = ?
            order by created_at desc
            limit 1
            """,
            this::mapExtraction,
            runId,
            workspaceId);
    return results.stream().findFirst();
  }

  public List<String> listDemoStorageKeys(String workspaceId) {
    return jdbcTemplate.queryForList(
        """
        select d.storage_key
        from drive_files d
        join demo_invoice_runs r on r.file_id = d.file_id
        where r.workspace_id = ?
        """,
        String.class,
        workspaceId);
  }

  public List<String> listDemoCorrelationIds(String workspaceId) {
    return jdbcTemplate.queryForList(
        """
        select correlation_id
        from demo_invoice_runs
        where workspace_id = ?
        """,
        String.class,
        workspaceId);
  }

  public int countRuns(String workspaceId) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from demo_invoice_runs where workspace_id = ?",
            Integer.class,
            workspaceId);
    return count == null ? 0 : count;
  }

  private DemoInvoiceRun mapRun(ResultSet resultSet, int rowNumber) throws SQLException {
    return new DemoInvoiceRun(
        resultSet.getString("run_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("actor_id"),
        resultSet.getString("correlation_id"),
        resultSet.getString("file_id"),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant());
  }

  private DemoInvoiceExtraction mapExtraction(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new DemoInvoiceExtraction(
        resultSet.getString("extraction_id"),
        resultSet.getString("run_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("actor_id"),
        resultSet.getString("file_id"),
        resultSet.getString("ocr_job_id"),
        resultSet.getString("workflow_execution_id"),
        resultSet.getString("invoice_number"),
        resultSet.getString("supplier_name"),
        resultSet.getString("supplier_test_nif"),
        resultSet.getString("supplier_test_iban"),
        resultSet.getBigDecimal("total_amount"),
        resultSet.getString("currency"),
        resultSet.getDate("due_date").toLocalDate(),
        resultSet.getBoolean("is_test_data"),
        jsonNode(resultSet.getString("metadata_json")),
        resultSet.getTimestamp("created_at").toInstant());
  }

  private String json(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Demo invoice metadata could not be serialized", exception);
    }
  }

  private JsonNode jsonNode(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Demo invoice metadata could not be parsed", exception);
    }
  }
}
