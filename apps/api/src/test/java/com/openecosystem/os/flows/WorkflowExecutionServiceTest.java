package com.openecosystem.os.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openecosystem.os.OpenEcosystemApiApplication;
import com.openecosystem.os.media.JdbcOcrResultRepository;
import com.openecosystem.os.media.OcrDocumentResult;
import com.openecosystem.os.media.OcrPageResult;
import com.openecosystem.os.media.OcrWord;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(classes = OpenEcosystemApiApplication.class)
class WorkflowExecutionServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-11T12:00:00Z");
  private static final Timestamp SQL_NOW = Timestamp.from(NOW);
  private static final String PRIVATE_OCR_TEXT = "PRIVATE_OCR_TEXT";
  private static final String PRIVATE_IBAN = "ES9121000418450200051332";
  private static final String PRIVATE_TAX_ID = "B12345678";
  private static final String PRIVATE_FILE_NAME = "private-invoice.pdf";

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Autowired private WorkflowService workflowService;
  @Autowired private OcrCompletedWorkflowTriggerService triggerService;
  @Autowired private JdbcOcrResultRepository ocrResultRepository;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from event_consumptions");
    jdbcTemplate.update("delete from search_documents");
    jdbcTemplate.update("delete from invoice_extraction_field_sources");
    jdbcTemplate.update("delete from invoice_extraction_fields");
    jdbcTemplate.update("delete from invoice_extractions");
    jdbcTemplate.update("delete from knowledge_items");
    jdbcTemplate.update("delete from notifications");
    jdbcTemplate.update("delete from workflow_step_executions");
    jdbcTemplate.update("delete from workflow_executions");
    jdbcTemplate.update("update workflows set current_version_id = null");
    jdbcTemplate.update("delete from workflow_versions");
    jdbcTemplate.update("delete from workflows");
    jdbcTemplate.update("delete from event_outbox");
    jdbcTemplate.update("delete from audit_records");
    jdbcTemplate.update("delete from ocr_result_words");
    jdbcTemplate.update("delete from ocr_result_pages");
    jdbcTemplate.update("delete from ocr_results");
    jdbcTemplate.update("delete from ocr_jobs");
    jdbcTemplate.update("delete from drive_files");
  }

  @Test
  void duplicateOcrDeliveryPersistsOneRealExtractionAndOnlySafeDownstreamData() throws Exception {
    createWorkflow(invoiceDefinition());
    insertCompletedStructuredOcr("ocr_invoice", "file_invoice");
    OcrCompletedEvent event = ocrCompletedEvent("evt_invoice", "ocr_invoice", "file_invoice");

    triggerService.trigger(event);
    triggerService.trigger(event);

    assertThat(count("workflow_executions")).isEqualTo(1);
    assertThat(count("invoice_extractions")).isEqualTo(1);
    assertThat(count("invoice_extraction_fields")).isEqualTo(10);
    assertThat(count("invoice_extraction_field_sources")).isGreaterThan(10);
    assertThat(count("notifications")).isEqualTo(1);
    assertThat(count("audit_records")).isEqualTo(1);
    assertThat(count("search_documents")).isEqualTo(1);
    assertThat(count("event_consumptions")).isEqualTo(1);
    assertThat(outboxCount("IndexingRequested")).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("select status from invoice_extractions", String.class))
        .isEqualTo("completed");
    String extractionId =
        jdbcTemplate.queryForObject("select extraction_id from invoice_extractions", String.class);
    String auditAttributes =
        jdbcTemplate.queryForObject("select attributes_json from audit_records", String.class);
    assertThat(auditAttributes)
        .contains(
            "\"extractionId\":\"" + extractionId + "\"",
            "\"status\":\"completed\"",
            "\"reviewRequired\":\"false\"")
        .doesNotContain(PRIVATE_OCR_TEXT, PRIVATE_IBAN, PRIVATE_TAX_ID, "TEST-INV-2026-42");

    String extractionOutput =
        jdbcTemplate.queryForObject(
            "select output_json from workflow_step_executions where action_type ="
                + " 'extract_invoice_fields'",
            String.class);
    assertThat(extractionOutput)
        .contains("extractionId", "fieldCount", "warningCount", "completed")
        .doesNotContain("TEST-INV", PRIVATE_IBAN, PRIVATE_TAX_ID, PRIVATE_OCR_TEXT);

    allStrings("select payload_json from event_outbox").forEach(this::assertSafeDownstreamText);
    allStrings("select envelope_json from event_outbox").forEach(this::assertSafeDownstreamText);
    allStrings("select attributes_json from audit_records").forEach(this::assertSafeDownstreamText);
    allStrings("select title from notifications").forEach(this::assertSafeDownstreamText);
    allStrings("select body from notifications").forEach(this::assertSafeDownstreamText);

    Map<String, Object> searchDocument =
        jdbcTemplate.queryForMap("select * from search_documents limit 1");
    assertThat(searchDocument.get("source_type")).isEqualTo("invoice_extraction");
    assertThat(searchDocument.get("resource_href")).isEqualTo("/app/media?jobId=ocr_invoice");
    assertThat(searchDocument.get("content").toString())
        .contains("TEST-INV-2026-42", "Example Supplies", "121.00", "EUR")
        .doesNotContain(PRIVATE_IBAN, PRIVATE_TAX_ID, PRIVATE_OCR_TEXT);
  }

  @Test
  void persistsAFixedSafeFailureSummaryInsteadOfTheActionExceptionMessage() throws Exception {
    String workflowId =
        createWorkflow(
            """
            {
              "trigger": { "type": "manual" },
              "steps": [
                { "id": "notify", "name": "Notify", "action": { "type": "create_notification" } }
              ]
            }
            """);

    WorkflowExecutionDetailResponse execution = workflowService.runWorkflowManually(workflowId);

    assertThat(execution.status()).isEqualTo("failed");
    assertThat(execution.failureReason()).isEqualTo("Workflow action failed.");
    assertThat(allStrings("select payload_json from event_outbox"))
        .allSatisfy(this::assertSafeDownstreamText);
    assertThat(allStrings("select envelope_json from event_outbox"))
        .allSatisfy(this::assertSafeDownstreamText);
  }

  @Test
  void createsOneReviewRequiredExtractionWhenStructuredWordsCannotSupportFields() throws Exception {
    createWorkflow(invoiceDefinition());
    insertCompletedStructuredOcr("ocr_review", "file_review");
    jdbcTemplate.update("delete from ocr_result_words where ocr_result_id = 'ocrr_invoice'");
    OcrCompletedEvent event = ocrCompletedEvent("evt_review", "ocr_review", "file_review");

    triggerService.trigger(event);
    triggerService.trigger(event);

    assertThat(jdbcTemplate.queryForObject("select status from invoice_extractions", String.class))
        .isEqualTo("review_required");
    assertThat(count("invoice_extraction_fields")).isZero();
    assertThat(count("invoice_extraction_field_sources")).isZero();
    assertThat(count("notifications")).isEqualTo(1);
    assertThat(count("search_documents")).isEqualTo(1);
    assertThat(count("event_consumptions")).isEqualTo(1);
    String extractionId =
        jdbcTemplate.queryForObject("select extraction_id from invoice_extractions", String.class);
    String auditAttributes =
        jdbcTemplate.queryForObject("select attributes_json from audit_records", String.class);
    assertThat(auditAttributes)
        .contains(
            "\"extractionId\":\"" + extractionId + "\"",
            "\"status\":\"review_required\"",
            "\"reviewRequired\":\"true\"")
        .doesNotContain(PRIVATE_OCR_TEXT, PRIVATE_IBAN, PRIVATE_TAX_ID, "TEST-INV-2026-42");
  }

  private String createWorkflow(String definitionJson) throws Exception {
    return workflowService
        .createWorkflow(
            new WorkflowSaveRequest(
                "Invoice workflow",
                "Test workflow",
                "active",
                objectMapper.readTree(definitionJson)))
        .workflow()
        .workflowId();
  }

  private String invoiceDefinition() {
    return """
    {
      "trigger": { "type": "event", "eventType": "OcrCompleted" },
      "steps": [
        { "id": "extract", "name": "Extract invoice fields", "action": { "type": "extract_invoice_fields" } },
        { "id": "notify", "name": "Notify", "action": { "type": "create_notification", "title": "Invoice extraction completed", "body": "An invoice extraction is ready for review.", "severity": "info" } },
        { "id": "audit", "name": "Audit", "action": { "type": "create_audit_entry", "action": "flows.invoice_automation.completed", "resourceType": "workflow_execution", "attributes": { "workflow": "invoice_automation" } } },
        { "id": "knowledge", "name": "Knowledge", "action": { "type": "create_knowledge_item_placeholder", "title": "Invoice knowledge placeholder", "summary": "A Knowledge placeholder was created from an invoice automation event." } },
        { "id": "index", "name": "Index", "action": { "type": "request_search_indexing" } }
      ]
    }
    """;
  }

  private OcrCompletedEvent ocrCompletedEvent(String eventId, String jobId, String fileId) {
    return new OcrCompletedEvent(
        eventId,
        1,
        NOW,
        "wrk_dev_placeholder",
        "usr_dev_placeholder",
        "corr_test",
        "evt_requested",
        "media:ocr:" + jobId + ":completed:v1",
        jobId,
        fileId,
        "tesseract",
        1,
        PRIVATE_OCR_TEXT.length(),
        NOW);
  }

  private void insertCompletedStructuredOcr(String jobId, String fileId) {
    jdbcTemplate.update(
        """
        insert into drive_files (
          file_id, workspace_id, owner_id, encrypted_name, content_type, size_bytes,
          checksum_sha256, storage_key, encryption_algorithm, encryption_key_id,
          content_iv, name_iv, created_at, updated_at
        ) values (?, 'wrk_dev_placeholder', 'usr_dev_placeholder', ?, 'application/pdf', 10,
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', ?,
          'AES-256-GCM', 'test-key', 'content-iv', 'name-iv', ?, ?)
        """,
        fileId,
        PRIVATE_FILE_NAME,
        "workspaces/wrk_dev_placeholder/drive/" + fileId + "/original",
        SQL_NOW,
        SQL_NOW);
    jdbcTemplate.update(
        """
        insert into ocr_jobs (
          job_id, file_id, workspace_id, actor_id, source_event_id, correlation_id,
          content_type, storage_key, status, provider, attempt_count, max_attempts,
          extracted_text, extracted_text_length, failure_code, failure_message, queued_at,
          processing_started_at, completed_at, failed_at, next_attempt_at, created_at, updated_at
        ) values (?, ?, 'wrk_dev_placeholder', 'usr_dev_placeholder', 'evt_uploaded', 'corr_test',
          'application/pdf', ?, 'completed', 'tesseract', 1, 3, ?, ?, null, null, ?, ?, ?, null, null, ?, ?)
        """,
        jobId,
        fileId,
        "workspaces/wrk_dev_placeholder/drive/" + fileId + "/original",
        PRIVATE_OCR_TEXT,
        PRIVATE_OCR_TEXT.length(),
        SQL_NOW,
        SQL_NOW,
        SQL_NOW,
        SQL_NOW,
        SQL_NOW);
    ocrResultRepository.save(structuredResult(jobId, fileId));
  }

  private OcrDocumentResult structuredResult(String jobId, String fileId) {
    String[][] lines = {
      {"Invoice", "TEST-INV-2026-42"},
      {"Supplier", "Example", "Supplies"},
      {"Tax", "ID", PRIVATE_TAX_ID},
      {"IBAN", PRIVATE_IBAN},
      {"Subtotal", "100.00"},
      {"Tax", "21.00"},
      {"Total", "121.00"},
      {"Currency", "EUR"},
      {"Issue", "date", "2026-07-01"},
      {"Due", "date", "2026-07-31"}
    };
    List<OcrWord> words = new ArrayList<>();
    int readingOrder = 1;
    for (int lineNumber = 0; lineNumber < lines.length; lineNumber++) {
      for (int wordNumber = 0; wordNumber < lines[lineNumber].length; wordNumber++) {
        words.add(
            new OcrWord(
                "ocrw_" + lineNumber + "_" + wordNumber,
                "ocrp_invoice",
                "ocrr_invoice",
                "wrk_dev_placeholder",
                readingOrder++,
                1,
                wordNumber,
                0,
                0,
                lineNumber + 1,
                wordNumber,
                lines[lineNumber][wordNumber],
                new BigDecimal("92.00"),
                wordNumber * 120,
                lineNumber * 24,
                100,
                18,
                "tesseract_tsv",
                NOW));
      }
    }
    OcrPageResult page =
        new OcrPageResult(
            "ocrp_invoice",
            "ocrr_invoice",
            "wrk_dev_placeholder",
            1,
            "tesseract_tsv",
            PRIVATE_OCR_TEXT,
            words.size(),
            NOW,
            words);
    return new OcrDocumentResult(
        "ocrr_invoice",
        jobId,
        fileId,
        "wrk_dev_placeholder",
        "tesseract",
        "5.5.0",
        PRIVATE_OCR_TEXT,
        1,
        words.size(),
        NOW,
        NOW,
        List.of(page));
  }

  private void assertSafeDownstreamText(String value) {
    assertThat(value)
        .doesNotContain(
            PRIVATE_OCR_TEXT,
            "TEST-INV-2026-42",
            PRIVATE_IBAN,
            PRIVATE_TAX_ID,
            PRIVATE_FILE_NAME,
            "private invoice");
  }

  private List<String> allStrings(String sql) {
    return jdbcTemplate.query(sql, (resultSet, rowNumber) -> resultSet.getString(1));
  }

  private int count(String table) {
    Integer value = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    return value == null ? 0 : value;
  }

  private int outboxCount(String eventType) {
    Integer value =
        jdbcTemplate.queryForObject(
            "select count(*) from event_outbox where event_type = ?", Integer.class, eventType);
    return value == null ? 0 : value;
  }
}
