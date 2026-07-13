package com.openecosystem.os.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openecosystem.os.OpenEcosystemApiApplication;
import com.openecosystem.os.media.JdbcOcrResultRepository;
import com.openecosystem.os.media.OcrDocumentResult;
import com.openecosystem.os.media.OcrPageResult;
import com.openecosystem.os.media.OcrWord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = OpenEcosystemApiApplication.class)
class JdbcInvoiceExtractionRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-07-11T12:00:00Z");

  @Autowired private JdbcInvoiceExtractionRepository repository;
  @Autowired private JdbcOcrResultRepository ocrResultRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from invoice_extraction_field_sources");
    jdbcTemplate.update("delete from invoice_extraction_fields");
    jdbcTemplate.update("delete from invoice_extractions");
    jdbcTemplate.update("delete from ocr_result_words");
    jdbcTemplate.update("delete from ocr_result_pages");
    jdbcTemplate.update("delete from ocr_results");
    jdbcTemplate.update("delete from workflow_step_executions");
    jdbcTemplate.update("delete from workflow_executions");
    jdbcTemplate.update("update workflows set current_version_id = null");
    jdbcTemplate.update("delete from workflow_versions");
    jdbcTemplate.update("delete from workflows");
    jdbcTemplate.update("delete from ocr_jobs");
    jdbcTemplate.update("delete from drive_files");
  }

  @Test
  void savesFieldsAndProvenanceIdempotentlyWithinTheWorkspace() {
    insertParents("wrk_test", "file_test", "ocr_test", "wfe_test");
    ocrResultRepository.save(result());
    InvoiceExtraction extraction = extraction("wrk_test", "wfe_test");

    repository.save(extraction);
    repository.save(extraction);

    InvoiceExtraction stored =
        repository.findByWorkflowExecutionIdForWorkspace("wfe_test", "wrk_test").orElseThrow();
    assertThat(stored.fields())
        .singleElement()
        .satisfies(
            field -> {
              assertThat(field.normalizedValue()).isEqualTo("INV-2026-42");
              assertThat(field.sources())
                  .singleElement()
                  .satisfies(
                      source -> {
                        assertThat(source.ocrWordId()).isEqualTo("ocrw_test");
                        assertThat(source.sourceRole()).isEqualTo("value");
                      });
            });
    assertThat(repository.findByWorkflowExecutionIdForWorkspace("wfe_test", "wrk_other")).isEmpty();
    assertThat(repository.findByOcrJobIdForWorkspace("ocr_test", "wrk_other")).isEmpty();
    assertThat(count("invoice_extractions")).isEqualTo(1);
    assertThat(count("invoice_extraction_fields")).isEqualTo(1);
    assertThat(count("invoice_extraction_field_sources")).isEqualTo(1);
  }

  @Test
  void enforcesExtractionLineageAtTheDatabaseBoundary() {
    insertParents("wrk_test", "file_test", "ocr_test", "wfe_test");
    ocrResultRepository.save(result());

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    insert into invoice_extractions (
                      extraction_id, workflow_execution_id, ocr_result_id, job_id, file_id,
                      workspace_id, extractor_name, extractor_version, status, warnings_json,
                      aggregate_confidence, field_count, warning_count, created_at, updated_at
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    "invx_invalid",
                    "wfe_test",
                    "ocrr_test",
                    "ocr_test",
                    "file_other",
                    "wrk_test",
                    "heuristic_invoice",
                    "1",
                    "completed",
                    "[]",
                    null,
                    0,
                    0,
                    NOW,
                    NOW))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rollsBackTheExtractionWhenSourcePersistenceFails() {
    insertParents("wrk_test", "file_test", "ocr_test", "wfe_test");
    ocrResultRepository.save(result());
    InvoiceExtraction extraction = extraction("wrk_test", "wfe_test");
    InvoiceExtractionField field = extraction.fields().getFirst();
    InvoiceExtraction invalidExtraction =
        new InvoiceExtraction(
            extraction.extractionId(),
            extraction.workflowExecutionId(),
            extraction.ocrResultId(),
            extraction.jobId(),
            extraction.fileId(),
            extraction.workspaceId(),
            extraction.extractorName(),
            extraction.extractorVersion(),
            extraction.status(),
            extraction.warnings(),
            extraction.aggregateConfidence(),
            List.of(
                new InvoiceExtractionField(
                    field.fieldId(),
                    field.extractionId(),
                    field.ocrResultId(),
                    field.workspaceId(),
                    field.fieldKey(),
                    field.displayValue(),
                    field.normalizedValue(),
                    field.status(),
                    field.confidence(),
                    field.sourcePageNumber(),
                    field.sourceBlockNumber(),
                    field.sourceParagraphNumber(),
                    field.sourceLineNumber(),
                    field.createdAt(),
                    List.of(
                        new InvoiceExtractionFieldSource(
                            field.fieldId(),
                            field.ocrResultId(),
                            field.workspaceId(),
                            "ocrw_missing",
                            "value",
                            0)))),
            extraction.createdAt(),
            extraction.updatedAt());

    assertThatThrownBy(() -> repository.save(invalidExtraction))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(count("invoice_extractions")).isZero();
    assertThat(count("invoice_extraction_fields")).isZero();
    assertThat(count("invoice_extraction_field_sources")).isZero();
  }

  @Test
  void findsWorkspaceScopedExtractionSummariesWithoutLoadingFieldsOrWarnings() {
    insertParents("wrk_test", "file_test", "ocr_test", "wfe_test");
    ocrResultRepository.save(result());
    repository.save(extraction("wrk_test", "wfe_test"));

    assertThat(
            repository.findSummariesByOcrJobIdsForWorkspace(
                "wrk_test", List.of("ocr_test", "ocr_missing")))
        .containsEntry(
            "ocr_test",
            new InvoiceExtractionSummary("invx_test", InvoiceExtractionStatus.COMPLETED));
    assertThat(repository.findSummariesByOcrJobIdsForWorkspace("wrk_other", List.of("ocr_test")))
        .isEmpty();
  }

  private InvoiceExtraction extraction(String workspaceId, String executionId) {
    String fieldId = "invf_test";
    return new InvoiceExtraction(
        "invx_test",
        executionId,
        "ocrr_test",
        "ocr_test",
        "file_test",
        workspaceId,
        "heuristic_invoice",
        "1",
        InvoiceExtractionStatus.COMPLETED,
        List.of(),
        new BigDecimal("95.00"),
        List.of(
            new InvoiceExtractionField(
                fieldId,
                "invx_test",
                "ocrr_test",
                workspaceId,
                "invoice_number",
                "INV-2026-42",
                "INV-2026-42",
                InvoiceFieldStatus.EXTRACTED,
                new BigDecimal("95.00"),
                1,
                0,
                0,
                1,
                NOW,
                List.of(
                    new InvoiceExtractionFieldSource(
                        fieldId, "ocrr_test", workspaceId, "ocrw_test", "value", 0)))),
        NOW,
        NOW);
  }

  private OcrDocumentResult result() {
    OcrWord word =
        new OcrWord(
            "ocrw_test",
            "ocrp_test",
            "ocrr_test",
            "wrk_test",
            1,
            1,
            1,
            0,
            0,
            1,
            1,
            "INV-2026-42",
            new BigDecimal("95.00"),
            20,
            20,
            90,
            18,
            "tesseract_tsv",
            NOW);
    return new OcrDocumentResult(
        "ocrr_test",
        "ocr_test",
        "file_test",
        "wrk_test",
        "tesseract",
        "5.5.0",
        "INV-2026-42",
        1,
        1,
        NOW,
        NOW,
        List.of(
            new OcrPageResult(
                "ocrp_test",
                "ocrr_test",
                "wrk_test",
                1,
                "tesseract_tsv",
                "INV-2026-42",
                1,
                NOW,
                List.of(word))));
  }

  private void insertParents(String workspaceId, String fileId, String jobId, String executionId) {
    jdbcTemplate.update(
        """
        insert into drive_files (
          file_id, workspace_id, owner_id, encrypted_name, content_type, size_bytes,
          checksum_sha256, storage_key, encryption_algorithm, encryption_key_id,
          content_iv, name_iv, created_at, updated_at
        ) values (?, ?, 'usr_test', 'encrypted-name', 'application/pdf', 10,
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', ?,
          'AES-256-GCM', 'test-key', 'content-iv', 'name-iv', ?, ?)
        """,
        fileId,
        workspaceId,
        "workspaces/" + workspaceId + "/drive/" + fileId + "/original",
        NOW,
        NOW);
    jdbcTemplate.update(
        """
        insert into ocr_jobs (
          job_id, file_id, workspace_id, actor_id, source_event_id, correlation_id,
          content_type, storage_key, status, provider, attempt_count, max_attempts,
          extracted_text, extracted_text_length, failure_code, failure_message, queued_at,
          processing_started_at, completed_at, failed_at, next_attempt_at, created_at, updated_at
        ) values (?, ?, ?, 'usr_test', 'evt_test', 'corr_test', 'application/pdf', ?,
          'completed', 'tesseract', 1, 3, null, null, null, null, ?, ?, ?, null, null, ?, ?)
        """,
        jobId,
        fileId,
        workspaceId,
        "workspaces/" + workspaceId + "/drive/" + fileId + "/original",
        NOW,
        NOW,
        NOW,
        NOW,
        NOW);
    jdbcTemplate.update(
        """
        insert into workflows (
          workflow_id, workspace_id, name, description, status, current_version_id,
          current_version_number, created_by, updated_by, created_at, updated_at
        ) values ('flow_test', ?, 'Test', 'Test', 'active', null, 1, 'usr_test', 'usr_test', ?, ?)
        """,
        workspaceId,
        NOW,
        NOW);
    jdbcTemplate.update(
        """
        insert into workflow_versions (
          version_id, workflow_id, workspace_id, version_number, definition_json,
          created_by, created_at, published_at
        ) values ('wfv_test', 'flow_test', ?, 1, '{"trigger":{"type":"event","eventType":"OcrCompleted"},"steps":[]}', 'usr_test', ?, ?)
        """,
        workspaceId,
        NOW,
        NOW);
    jdbcTemplate.update(
        """
        insert into workflow_executions (
          execution_id, workflow_id, workflow_version_id, workflow_version_number,
          workspace_id, actor_id, correlation_id, trigger_type, source_event_id,
          source_event_type, trigger_idempotency_key, status, retry_count,
          failure_reason, started_at, completed_at, failed_at, created_at, updated_at
        ) values (?, 'flow_test', 'wfv_test', 1, ?, 'usr_test', 'corr_test', 'event',
          'evt_test', 'OcrCompleted', ?, 'completed', 0, null, ?, ?, null, ?, ?)
        """,
        executionId,
        workspaceId,
        "test:" + executionId,
        NOW,
        NOW,
        NOW,
        NOW);
  }

  private int count(String table) {
    Integer value = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    return value == null ? 0 : value;
  }
}
