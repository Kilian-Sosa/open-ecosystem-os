package com.openecosystem.os.invoice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcInvoiceExtractionRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcInvoiceExtractionRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public InvoiceExtraction save(InvoiceExtraction extraction) {
    lockWorkflowExecution(extraction.workflowExecutionId(), extraction.workspaceId());
    Optional<InvoiceExtraction> existing =
        findByWorkflowExecutionIdForWorkspace(
            extraction.workflowExecutionId(), extraction.workspaceId());
    if (existing.isPresent()) return existing.get();
    jdbcTemplate.update(
        """
        insert into invoice_extractions (
          extraction_id, workflow_execution_id, ocr_result_id, job_id, file_id,
          workspace_id, extractor_name, extractor_version, status, warnings_json,
          aggregate_confidence, field_count, warning_count, created_at, updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        extraction.extractionId(),
        extraction.workflowExecutionId(),
        extraction.ocrResultId(),
        extraction.jobId(),
        extraction.fileId(),
        extraction.workspaceId(),
        extraction.extractorName(),
        extraction.extractorVersion(),
        extraction.status().value(),
        warningsJson(extraction.warnings()),
        extraction.aggregateConfidence(),
        extraction.fields().size(),
        extraction.warnings().size(),
        Timestamp.from(extraction.createdAt()),
        Timestamp.from(extraction.updatedAt()));
    extraction.fields().forEach(field -> saveField(extraction, field));
    return extraction;
  }

  private void lockWorkflowExecution(String workflowExecutionId, String workspaceId) {
    jdbcTemplate.queryForObject(
        """
        select execution_id
        from workflow_executions
        where execution_id = ? and workspace_id = ?
        for update
        """,
        String.class,
        workflowExecutionId,
        workspaceId);
  }

  public Optional<InvoiceExtraction> findByWorkflowExecutionIdForWorkspace(
      String workflowExecutionId, String workspaceId) {
    List<InvoiceExtraction> results =
        jdbcTemplate.query(
            """
            select *
            from invoice_extractions
            where workflow_execution_id = ? and workspace_id = ?
            """,
            (resultSet, rowNumber) -> mapExtraction(resultSet),
            workflowExecutionId,
            workspaceId);
    return results.stream().findFirst();
  }

  public Optional<InvoiceExtraction> findByOcrJobIdForWorkspace(String jobId, String workspaceId) {
    List<InvoiceExtraction> results =
        jdbcTemplate.query(
            """
            select *
            from invoice_extractions
            where job_id = ? and workspace_id = ?
            order by created_at desc
            """,
            (resultSet, rowNumber) -> mapExtraction(resultSet),
            jobId,
            workspaceId);
    return results.stream().findFirst();
  }

  public Map<String, InvoiceExtractionSummary> findSummariesByOcrJobIdsForWorkspace(
      String workspaceId, List<String> jobIds) {
    if (jobIds.isEmpty()) return Map.of();
    String placeholders = jobIds.stream().map(ignored -> "?").collect(Collectors.joining(", "));
    List<Object> arguments = new ArrayList<>();
    arguments.add(workspaceId);
    arguments.addAll(jobIds);
    Map<String, InvoiceExtractionSummary> byJobId = new LinkedHashMap<>();
    jdbcTemplate
        .query(
            """
            select job_id, extraction_id, status
            from invoice_extractions
            where workspace_id = ? and job_id in (%s)
            order by job_id, created_at desc
            """
                .formatted(placeholders),
            (resultSet, rowNumber) ->
                Map.entry(
                    resultSet.getString("job_id"),
                    new InvoiceExtractionSummary(
                        resultSet.getString("extraction_id"),
                        invoiceStatus(resultSet.getString("status")))),
            arguments.toArray())
        .forEach(entry -> byJobId.putIfAbsent(entry.getKey(), entry.getValue()));
    return Map.copyOf(byJobId);
  }

  public Optional<InvoiceExtractionSummary> findSummaryByWorkflowExecutionIdForWorkspace(
      String workflowExecutionId, String workspaceId) {
    return jdbcTemplate
        .query(
            """
            select extraction_id, status
            from invoice_extractions
            where workflow_execution_id = ? and workspace_id = ?
            """,
            (resultSet, rowNumber) ->
                new InvoiceExtractionSummary(
                    resultSet.getString("extraction_id"),
                    invoiceStatus(resultSet.getString("status"))),
            workflowExecutionId,
            workspaceId)
        .stream()
        .findFirst();
  }

  private void saveField(InvoiceExtraction extraction, InvoiceExtractionField field) {
    requireFieldLineage(extraction, field);
    jdbcTemplate.update(
        """
        insert into invoice_extraction_fields (
          field_id, extraction_id, ocr_result_id, workspace_id, field_key, display_value,
          normalized_value, status, confidence, source_page_number, source_block_number,
          source_paragraph_number, source_line_number, created_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        field.fieldId(),
        field.extractionId(),
        field.ocrResultId(),
        field.workspaceId(),
        field.fieldKey(),
        field.displayValue(),
        field.normalizedValue(),
        field.status().value(),
        field.confidence(),
        field.sourcePageNumber(),
        field.sourceBlockNumber(),
        field.sourceParagraphNumber(),
        field.sourceLineNumber(),
        Timestamp.from(field.createdAt()));
    field.sources().forEach(source -> saveSource(field, source));
  }

  private void saveSource(InvoiceExtractionField field, InvoiceExtractionFieldSource source) {
    if (!field.fieldId().equals(source.fieldId())
        || !field.ocrResultId().equals(source.ocrResultId())
        || !field.workspaceId().equals(source.workspaceId())) {
      throw new IllegalArgumentException("Invoice field source lineage must match its field");
    }
    jdbcTemplate.update(
        """
        insert into invoice_extraction_field_sources (
          field_id, ocr_result_id, workspace_id, ocr_word_id, source_role, source_order
        ) values (?, ?, ?, ?, ?, ?)
        """,
        source.fieldId(),
        source.ocrResultId(),
        source.workspaceId(),
        source.ocrWordId(),
        source.sourceRole(),
        source.sourceOrder());
  }

  private void requireFieldLineage(InvoiceExtraction extraction, InvoiceExtractionField field) {
    if (!extraction.extractionId().equals(field.extractionId())
        || !extraction.ocrResultId().equals(field.ocrResultId())
        || !extraction.workspaceId().equals(field.workspaceId())) {
      throw new IllegalArgumentException("Invoice field lineage must match its extraction");
    }
  }

  private InvoiceExtraction mapExtraction(ResultSet resultSet) throws SQLException {
    String extractionId = resultSet.getString("extraction_id");
    String workspaceId = resultSet.getString("workspace_id");
    return new InvoiceExtraction(
        extractionId,
        resultSet.getString("workflow_execution_id"),
        resultSet.getString("ocr_result_id"),
        resultSet.getString("job_id"),
        resultSet.getString("file_id"),
        workspaceId,
        resultSet.getString("extractor_name"),
        resultSet.getString("extractor_version"),
        invoiceStatus(resultSet.getString("status")),
        warnings(resultSet.getString("warnings_json")),
        resultSet.getBigDecimal("aggregate_confidence"),
        fields(extractionId, workspaceId),
        timestamp(resultSet, "created_at"),
        timestamp(resultSet, "updated_at"));
  }

  private List<InvoiceExtractionField> fields(String extractionId, String workspaceId) {
    return jdbcTemplate.query(
        """
        select *
        from invoice_extraction_fields
        where extraction_id = ? and workspace_id = ?
        order by field_key
        """,
        (resultSet, rowNumber) -> mapField(resultSet),
        extractionId,
        workspaceId);
  }

  private InvoiceExtractionField mapField(ResultSet resultSet) throws SQLException {
    String fieldId = resultSet.getString("field_id");
    String workspaceId = resultSet.getString("workspace_id");
    String ocrResultId = resultSet.getString("ocr_result_id");
    return new InvoiceExtractionField(
        fieldId,
        resultSet.getString("extraction_id"),
        ocrResultId,
        workspaceId,
        resultSet.getString("field_key"),
        resultSet.getString("display_value"),
        resultSet.getString("normalized_value"),
        fieldStatus(resultSet.getString("status")),
        resultSet.getBigDecimal("confidence"),
        resultSet.getInt("source_page_number"),
        resultSet.getInt("source_block_number"),
        resultSet.getInt("source_paragraph_number"),
        resultSet.getInt("source_line_number"),
        timestamp(resultSet, "created_at"),
        sources(fieldId, ocrResultId, workspaceId));
  }

  private List<InvoiceExtractionFieldSource> sources(
      String fieldId, String ocrResultId, String workspaceId) {
    return jdbcTemplate.query(
        """
        select *
        from invoice_extraction_field_sources
        where field_id = ? and ocr_result_id = ? and workspace_id = ?
        order by source_order
        """,
        (resultSet, rowNumber) ->
            new InvoiceExtractionFieldSource(
                resultSet.getString("field_id"),
                resultSet.getString("ocr_result_id"),
                resultSet.getString("workspace_id"),
                resultSet.getString("ocr_word_id"),
                resultSet.getString("source_role"),
                resultSet.getInt("source_order")),
        fieldId,
        ocrResultId,
        workspaceId);
  }

  private String warningsJson(List<InvoiceExtractionWarning> warnings) {
    try {
      return objectMapper.writeValueAsString(warnings);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Invoice extraction warnings could not be stored", exception);
    }
  }

  private List<InvoiceExtractionWarning> warnings(String warningsJson) {
    try {
      return objectMapper.readValue(
          warningsJson, new TypeReference<List<InvoiceExtractionWarning>>() {});
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Invoice extraction warnings could not be read", exception);
    }
  }

  private InvoiceExtractionStatus invoiceStatus(String value) {
    return switch (value) {
      case "completed" -> InvoiceExtractionStatus.COMPLETED;
      case "review_required" -> InvoiceExtractionStatus.REVIEW_REQUIRED;
      default -> throw new IllegalStateException("Unknown invoice extraction status");
    };
  }

  private InvoiceFieldStatus fieldStatus(String value) {
    return switch (value) {
      case "extracted" -> InvoiceFieldStatus.EXTRACTED;
      case "low_confidence" -> InvoiceFieldStatus.LOW_CONFIDENCE;
      default -> throw new IllegalStateException("Unknown invoice field status");
    };
  }

  private Instant timestamp(ResultSet resultSet, String column) throws SQLException {
    return resultSet.getTimestamp(column).toInstant();
  }
}
