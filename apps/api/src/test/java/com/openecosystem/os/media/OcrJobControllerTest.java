package com.openecosystem.os.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openecosystem.os.OpenEcosystemApiApplication;
import com.openecosystem.os.common.security.PlaceholderAuthenticationContext;
import com.openecosystem.os.drive.DriveFileMetadata;
import com.openecosystem.os.drive.DriveFileRepository;
import com.openecosystem.os.drive.DriveFileVisibility;
import com.openecosystem.os.drive.crypto.EncryptedText;
import com.openecosystem.os.drive.crypto.FileEncryptionService;
import com.openecosystem.os.invoice.InvoiceExtraction;
import com.openecosystem.os.invoice.InvoiceExtractionField;
import com.openecosystem.os.invoice.InvoiceExtractionFieldSource;
import com.openecosystem.os.invoice.InvoiceExtractionStatus;
import com.openecosystem.os.invoice.InvoiceExtractionWarning;
import com.openecosystem.os.invoice.InvoiceFieldStatus;
import com.openecosystem.os.invoice.JdbcInvoiceExtractionRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    classes = OpenEcosystemApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OcrJobControllerTest {

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DriveFileRepository driveFileRepository;
  @Autowired private FileEncryptionService encryptionService;
  @Autowired private OcrJobRepository ocrJobRepository;
  @Autowired private JdbcOcrResultRepository ocrResultRepository;
  @Autowired private JdbcInvoiceExtractionRepository invoiceExtractionRepository;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from event_consumptions");
    jdbcTemplate.update("delete from invoice_extraction_field_sources");
    jdbcTemplate.update("delete from invoice_extraction_fields");
    jdbcTemplate.update("delete from invoice_extractions");
    jdbcTemplate.update("delete from search_documents");
    jdbcTemplate.update("delete from knowledge_items");
    jdbcTemplate.update("delete from notifications");
    jdbcTemplate.update("delete from workflow_step_executions");
    jdbcTemplate.update("delete from workflow_executions");
    jdbcTemplate.update("update workflows set current_version_id = null");
    jdbcTemplate.update("delete from workflow_versions");
    jdbcTemplate.update("delete from workflows");
    jdbcTemplate.update("delete from ocr_result_words");
    jdbcTemplate.update("delete from ocr_result_pages");
    jdbcTemplate.update("delete from ocr_results");
    jdbcTemplate.update("delete from ocr_jobs");
    jdbcTemplate.update("delete from event_outbox");
    jdbcTemplate.update("delete from audit_records");
    jdbcTemplate.update("delete from drive_files");
  }

  @Test
  void listsWorkspaceJobsWithoutFullExtractedTextAndShowsDetailText() throws Exception {
    saveDriveFile(
        "file_invoice",
        PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID,
        "Invoice_2026_05.pdf",
        "application/pdf");
    saveDriveFile("file_other", "wrk_other", "Other.pdf", "application/pdf");
    ocrJobRepository.saveQueued(
        job(
            "ocr_invoice",
            "file_invoice",
            PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID,
            OcrJobStatus.COMPLETED));
    ocrJobRepository.saveQueued(
        job("ocr_other", "file_other", "wrk_other", OcrJobStatus.COMPLETED));

    HttpResponse<String> listResponse =
        httpClient.send(
            request("/api/media/ocr-jobs")
                .header(
                    PlaceholderAuthenticationContext.WORKSPACE_HEADER,
                    PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID)
                .build(),
            BodyHandlers.ofString());

    assertThat(listResponse.statusCode()).isEqualTo(200);
    assertThat(listResponse.body()).contains("ocr_invoice");
    assertThat(listResponse.body()).contains("Invoice_2026_05.pdf");
    assertThat(listResponse.body()).doesNotContain("ocr_other");
    assertThat(listResponse.body()).doesNotContain("Fake extracted invoice total");

    HttpResponse<String> detailResponse =
        httpClient.send(
            request("/api/media/ocr-jobs/ocr_invoice")
                .header(
                    PlaceholderAuthenticationContext.WORKSPACE_HEADER,
                    PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID)
                .build(),
            BodyHandlers.ofString());

    assertThat(detailResponse.statusCode()).isEqualTo(200);
    assertThat(detailResponse.body()).contains("Fake extracted invoice total");
  }

  @Test
  void returnsOrderedWorkspaceScopedLifecycleWithWorkflowExtractionAndSafeEvidence()
      throws Exception {
    String workspaceId = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    saveDriveFile("file_trace", workspaceId, "Trace.pdf", "application/pdf");
    ocrJobRepository.saveQueued(completedJob("ocr_trace", "file_trace", workspaceId));

    saveEvent(
        "evt_upload",
        "FileUploaded",
        "2026-05-22T10:00:00Z",
        workspaceId,
        "corr_trace",
        null,
        "drive",
        "2026-05-22T10:00:01Z");
    saveEvent(
        "evt_requested",
        "OcrRequested",
        "2026-05-22T10:01:00Z",
        workspaceId,
        "corr_trace",
        "evt_upload",
        "media",
        "2026-05-22T10:01:01Z");
    saveEvent(
        "evt_started_1",
        "OcrStarted",
        "2026-05-22T10:02:00Z",
        workspaceId,
        "corr_trace",
        "evt_requested",
        "media",
        "2026-05-22T10:02:01Z");
    saveEvent(
        "evt_started_2",
        "OcrStarted",
        "2026-05-22T10:03:00Z",
        workspaceId,
        "corr_trace",
        "evt_requested",
        "media",
        "2026-05-22T10:03:01Z");
    saveEvent(
        "evt_completed",
        "OcrCompleted",
        "2026-05-22T10:04:00Z",
        workspaceId,
        "corr_trace",
        "evt_requested",
        "media",
        "2026-05-22T10:04:01Z");
    saveWorkflowDefinition(workspaceId);
    saveWorkflowExecution(workspaceId);
    saveWorkflowStep(workspaceId);
    saveEvent(
        "evt_notification",
        "NotificationCreated",
        "2026-05-22T10:06:00Z",
        workspaceId,
        "corr_trace",
        "evt_completed",
        "notifications",
        "2026-05-22T10:06:01Z");
    saveAudit(workspaceId);
    saveEvent(
        "evt_indexing_requested",
        "IndexingRequested",
        "2026-05-22T10:07:00Z",
        workspaceId,
        "corr_trace",
        "evt_completed",
        "search",
        "2026-05-22T10:07:01Z");
    saveEvent(
        "evt_indexing_completed",
        "IndexingCompleted",
        "2026-05-22T10:08:00Z",
        workspaceId,
        "corr_trace",
        "evt_indexing_requested",
        "search",
        "2026-05-22T10:08:01Z");
    saveConsumption("media.file-uploaded.ocr-job", "evt_upload", "2026-05-22T10:01:00Z");
    saveConsumption("worker.ocr-requested.processor", "evt_requested", "2026-05-22T10:04:00Z");
    saveConsumption(
        "flows.ocr-completed.workflow-trigger", "evt_completed", "2026-05-22T10:06:30Z");
    saveConsumption(
        "worker.search-indexing.processor", "evt_indexing_requested", "2026-05-22T10:08:00Z");

    saveEvent(
        "evt_unrelated_same_correlation",
        "OcrStarted",
        "2026-05-22T10:02:30Z",
        workspaceId,
        "corr_trace",
        "evt_other_request",
        "media",
        "2026-05-22T10:02:31Z");
    saveEvent(
        "evt_other_workspace",
        "IndexingFailed",
        "2026-05-22T10:08:30Z",
        "wrk_other",
        "corr_trace",
        "evt_indexing_requested",
        "search",
        "2026-05-22T10:08:31Z");

    JsonNode detail = getJobDetail("ocr_trace", workspaceId);
    JsonNode lifecycle = detail.path("lifecycle");

    assertThat(lifecycle.path("state").asText()).isEqualTo("complete");
    assertThat(lifecycle.path("outcome").asText()).isEqualTo("completed");
    assertThat(entryIds(lifecycle))
        .containsSubsequence(
            "evt_upload",
            "evt_requested",
            "evt_started_1",
            "evt_started_2",
            "evt_completed",
            "wfe_trace",
            "wfs_extract",
            "evt_notification",
            "aud_trace",
            "evt_indexing_requested",
            "evt_indexing_completed")
        .doesNotContain("evt_unrelated_same_correlation", "evt_other_workspace");
    assertThat(lifecycle.toString())
        .contains("extract_invoice_fields")
        .contains("publish_recorded")
        .contains("consumption_recorded")
        .doesNotContain("payload_secret")
        .doesNotContain("envelope_secret")
        .doesNotContain("step_input_secret")
        .doesNotContain("step_output_secret")
        .doesNotContain("audit_attribute_secret")
        .doesNotContain("workspaces/wrk_dev_placeholder/drive/file_trace/original")
        .doesNotContain("Fake extracted invoice total");
  }

  @Test
  void returnsActiveRetryAndOutboxPendingWithoutInventingBrokerEvidence() throws Exception {
    String workspaceId = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    saveDriveFile("file_retry", workspaceId, "Retry.pdf", "application/pdf");
    ocrJobRepository.saveQueued(retryingJob("ocr_retry", "file_retry", workspaceId));
    saveEvent(
        "evt_retry_upload",
        "FileUploaded",
        "2026-05-22T11:00:00Z",
        workspaceId,
        "corr_retry",
        null,
        "drive",
        "2026-05-22T11:00:01Z");
    saveEvent(
        "evt_retry_requested",
        "OcrRequested",
        "2026-05-22T11:01:00Z",
        workspaceId,
        "corr_retry",
        "evt_retry_upload",
        "media",
        "2026-05-22T11:01:01Z");
    saveEvent(
        "evt_retry_started",
        "OcrStarted",
        "2026-05-22T11:02:00Z",
        workspaceId,
        "corr_retry",
        "evt_retry_requested",
        "media",
        null);
    saveConsumption("media.file-uploaded.ocr-job", "evt_retry_upload", "2026-05-22T11:01:00Z");

    JsonNode detail = getJobDetail("ocr_retry", workspaceId);
    JsonNode lifecycle = detail.path("lifecycle");

    assertThat(detail.path("attemptCount").asInt()).isEqualTo(1);
    assertThat(detail.path("maxAttempts").asInt()).isEqualTo(3);
    assertThat(detail.path("nextAttemptAt").asText()).isEqualTo("2026-05-22T11:10:00Z");
    assertThat(detail.path("failureMessage").asText())
        .isEqualTo(
            "OCR processing did not complete. Use the correlation ID for permitted diagnostics.");
    assertThat(detail.toString()).doesNotContain("private invoice line and secret://token");
    assertThat(lifecycle.path("state").asText()).isEqualTo("active");
    assertThat(lifecycle.path("outcome").asText()).isEqualTo("in_progress");
    assertThat(lifecycle.toString())
        .contains("outbox_pending")
        .contains("Awaiting scheduled OCR retry")
        .doesNotContain("delivered")
        .doesNotContain("dead_lettered")
        .doesNotContain("broker_acknowledged");
  }

  @Test
  void returnsPartialSanitizedFallbackForFailedJobWithoutEventHistory() throws Exception {
    String workspaceId = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    saveDriveFile("file_failed", workspaceId, "Failed.pdf", "application/pdf");
    ocrJobRepository.saveQueued(failedJob("ocr_failed", "file_failed", workspaceId));

    JsonNode detail = getJobDetail("ocr_failed", workspaceId);
    JsonNode lifecycle = detail.path("lifecycle");

    assertThat(lifecycle.path("state").asText()).isEqualTo("partial");
    assertThat(lifecycle.path("outcome").asText()).isEqualTo("failed");
    assertThat(lifecycle.toString())
        .contains("Some durable lifecycle evidence is unavailable")
        .contains("OCR processing failed")
        .doesNotContain("raw OCR text: secret invoice")
        .doesNotContain("storage-key-secret");
    assertThat(detail.path("failureCode").asText()).isEqualTo("PROCESSING_FAILED");
  }

  @Test
  void doesNotReturnLifecycleForAJobOutsideTheAuthenticatedWorkspace() throws Exception {
    saveDriveFile("file_other_detail", "wrk_other", "Other.pdf", "application/pdf");
    ocrJobRepository.saveQueued(completedJob("ocr_other_detail", "file_other_detail", "wrk_other"));

    HttpResponse<String> response =
        httpClient.send(
            request("/api/media/ocr-jobs/ocr_other_detail")
                .header(
                    PlaceholderAuthenticationContext.WORKSPACE_HEADER,
                    PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID)
                .build(),
            BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.body()).doesNotContain("corr_trace");
  }

  @Test
  void hidesPrivateOcrDetailFromAnUnsharedWorkspaceMember() throws Exception {
    String workspaceId = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    seedWorkspaceMember("usr_ocr_unshared", workspaceId, "VIEWER");
    saveDriveFile("file_private", workspaceId, "Private.pdf", "application/pdf");
    ocrJobRepository.saveQueued(completedJob("ocr_private", "file_private", workspaceId));

    HttpResponse<String> response =
        httpClient.send(
            request("/api/media/ocr-jobs/ocr_private")
                .header(PlaceholderAuthenticationContext.ACTOR_HEADER, "usr_ocr_unshared")
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, workspaceId)
                .build(),
            BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.body())
        .doesNotContain("Private.pdf")
        .doesNotContain("Fake extracted invoice total");
  }

  @Test
  void returnsNestedCompletedExtractionDetailsWithAllProvenanceAndMetadataOnlyList()
      throws Exception {
    String workspaceId = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    saveDriveFile("file_structured", workspaceId, "Structured.pdf", "application/pdf");
    ocrJobRepository.saveQueued(completedJob("ocr_structured", "file_structured", workspaceId));
    saveWorkflowDefinition(workspaceId);
    saveWorkflowExecution(workspaceId);
    saveStructuredResultAndExtraction(
        "ocr_structured",
        "file_structured",
        workspaceId,
        InvoiceExtractionStatus.COMPLETED,
        List.of(),
        true);

    JsonNode detail = getJobDetail("ocr_structured", workspaceId);
    HttpResponse<String> listResponse =
        httpClient.send(
            request("/api/media/ocr-jobs")
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, workspaceId)
                .build(),
            BodyHandlers.ofString());

    assertThat(detail.path("provider").asText()).isEqualTo("tesseract");
    assertThat(detail.path("extractedText").asText()).isEqualTo("STRUCTURED_OCR_TEXT");
    JsonNode ocrResult = detail.path("ocrResult");
    assertThat(ocrResult.path("ocrResultId").asText()).isEqualTo("ocrr_structured");
    assertThat(ocrResult.path("provider").asText()).isEqualTo("tesseract");
    assertThat(ocrResult.path("providerVersion").asText()).isEqualTo("5.5.0");
    assertThat(ocrResult.path("pageCount").asInt()).isEqualTo(1);
    assertThat(ocrResult.path("wordCount").asInt()).isEqualTo(2);

    JsonNode extraction = detail.path("extraction");
    assertThat(extraction.path("extractionId").asText()).isEqualTo("invx_structured");
    assertThat(extraction.path("extractor").asText()).isEqualTo("heuristic_invoice");
    assertThat(extraction.path("extractorVersion").asText()).isEqualTo("1");
    assertThat(extraction.path("status").asText()).isEqualTo("completed");
    assertThat(extraction.path("reviewRequired").asBoolean()).isFalse();
    assertThat(extraction.path("confidence").decimalValue()).isEqualByComparingTo("94.00");
    assertThat(extraction.path("fieldCount").asInt()).isEqualTo(1);
    assertThat(extraction.path("warningCount").asInt()).isZero();
    JsonNode field = extraction.path("fields").get(0);
    assertThat(field.path("fieldKey").asText()).isEqualTo("invoice_number");
    assertThat(field.path("label").asText()).isEqualTo("Invoice Number");
    assertThat(field.path("displayValue").asText()).isEqualTo("INV-42");
    assertThat(field.path("normalizedValue").asText()).isEqualTo("INV-42");
    assertThat(field.path("status").asText()).isEqualTo("extracted");
    assertThat(field.path("confidence").decimalValue()).isEqualByComparingTo("94.00");
    assertThat(field.path("provenance")).hasSize(2);
    assertThat(field.path("provenance").get(0).path("sourceRole").asText()).isEqualTo("label");
    assertThat(field.path("provenance").get(0).path("ocrWordId").asText())
        .isEqualTo("ocrw_invoice_label");
    assertThat(field.path("provenance").get(0).path("pageNumber").asInt()).isEqualTo(1);
    assertThat(field.path("provenance").get(0).path("blockNumber").asInt()).isZero();
    assertThat(field.path("provenance").get(0).path("paragraphNumber").asInt()).isZero();
    assertThat(field.path("provenance").get(0).path("lineNumber").asInt()).isEqualTo(1);
    assertThat(field.path("provenance").get(0).path("wordNumber").asInt()).isEqualTo(0);
    assertThat(field.path("provenance").get(0).path("readingOrder").asInt()).isEqualTo(0);
    assertThat(field.path("provenance").get(0).path("sourceKind").asText())
        .isEqualTo("tesseract_tsv");
    assertThat(field.path("provenance").get(1).path("sourceRole").asText()).isEqualTo("value");
    assertThat(field.path("provenance").get(1).path("ocrWordId").asText())
        .isEqualTo("ocrw_invoice_number");
    assertThat(listResponse.statusCode()).isEqualTo(200);
    assertThat(listResponse.body())
        .contains(
            "\"ocrResultPresent\":true",
            "\"extractionPresent\":true",
            "\"extractionStatus\":\"completed\"")
        .doesNotContain(
            "INV-42",
            "STRUCTURED_OCR_TEXT",
            "displayValue",
            "normalizedValue",
            "warnings",
            "provenance",
            "\"extraction\":{");
  }

  @Test
  void returnsReviewRequiredWarningsWithoutValues() throws Exception {
    String workspaceId = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    saveDriveFile("file_review", workspaceId, "Review.pdf", "application/pdf");
    ocrJobRepository.saveQueued(completedJob("ocr_review", "file_review", workspaceId));
    saveWorkflowDefinition(workspaceId);
    saveWorkflowExecution(workspaceId);
    saveStructuredResultAndExtraction(
        "ocr_review",
        "file_review",
        workspaceId,
        InvoiceExtractionStatus.REVIEW_REQUIRED,
        List.of(
            new InvoiceExtractionWarning(
                "missing_due_date", "A required invoice field is missing.")),
        false);

    JsonNode warning =
        getJobDetail("ocr_review", workspaceId).path("extraction").path("warnings").get(0);

    assertThat(warning.path("code").asText()).isEqualTo("missing_due_date");
    assertThat(warning.path("fieldKey").isNull()).isTrue();
    assertThat(warning.path("message").asText()).isEqualTo("A required invoice field is missing.");
    assertThat(warning.size()).isEqualTo(3);
  }

  @Test
  void returnsStructuredOcrResultWithoutFabricatingAnExtraction() throws Exception {
    String workspaceId = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    saveDriveFile("file_result_only", workspaceId, "Result-only.pdf", "application/pdf");
    ocrJobRepository.saveQueued(completedJob("ocr_result_only", "file_result_only", workspaceId));
    saveStructuredResult("ocr_result_only", "file_result_only", workspaceId);

    JsonNode detail = getJobDetail("ocr_result_only", workspaceId);

    assertThat(detail.path("ocrResult").path("ocrResultId").asText()).isEqualTo("ocrr_structured");
    assertThat(detail.path("extraction").isNull()).isTrue();
  }

  @Test
  void preservesLegacyExtractedTextAndReturnsNullForAbsentStructuredRecords() throws Exception {
    String workspaceId = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    saveDriveFile("file_legacy", workspaceId, "Legacy.pdf", "application/pdf");
    ocrJobRepository.saveQueued(completedJob("ocr_legacy", "file_legacy", workspaceId));

    JsonNode detail = getJobDetail("ocr_legacy", workspaceId);

    assertThat(detail.path("extractedText").asText())
        .isEqualTo("Fake extracted invoice total and lifecycle_text_secret");
    assertThat(detail.path("ocrResult").isNull()).isTrue();
    assertThat(detail.path("extraction").isNull()).isTrue();
  }

  @Test
  void doesNotExposeAWorkspaceJobWhoseSourceDriveFileIsMissing() throws Exception {
    String workspaceId = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    saveDriveFile("file_missing", workspaceId, "Deleted.pdf", "application/pdf");
    ocrJobRepository.saveQueued(completedJob("ocr_orphaned", "file_missing", workspaceId));
    jdbcTemplate.execute("set referential_integrity false");
    jdbcTemplate.update("delete from drive_files where file_id = 'file_missing'");
    jdbcTemplate.execute("set referential_integrity true");

    HttpResponse<String> detailResponse =
        httpClient.send(
            request("/api/media/ocr-jobs/ocr_orphaned")
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, workspaceId)
                .build(),
            BodyHandlers.ofString());
    HttpResponse<String> listResponse =
        httpClient.send(
            request("/api/media/ocr-jobs")
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, workspaceId)
                .build(),
            BodyHandlers.ofString());

    assertThat(detailResponse.statusCode()).isEqualTo(404);
    assertThat(listResponse.statusCode()).isEqualTo(200);
    assertThat(listResponse.body()).doesNotContain("ocr_orphaned");
  }

  private void saveStructuredResultAndExtraction(
      String jobId,
      String fileId,
      String workspaceId,
      InvoiceExtractionStatus status,
      List<InvoiceExtractionWarning> warnings,
      boolean includeField) {
    OcrWord invoiceNumberWord = saveStructuredResult(jobId, fileId, workspaceId);
    Instant now = Instant.parse("2026-07-11T12:00:00Z");
    List<InvoiceExtractionField> fields =
        includeField
            ? List.of(
                new InvoiceExtractionField(
                    "invf_invoice_number",
                    "invx_structured",
                    "ocrr_structured",
                    workspaceId,
                    "invoice_number",
                    "INV-42",
                    "INV-42",
                    InvoiceFieldStatus.EXTRACTED,
                    new BigDecimal("94.00"),
                    1,
                    0,
                    0,
                    1,
                    now,
                    List.of(
                        new InvoiceExtractionFieldSource(
                            "invf_invoice_number",
                            "ocrr_structured",
                            workspaceId,
                            "ocrw_invoice_label",
                            "label",
                            0),
                        new InvoiceExtractionFieldSource(
                            "invf_invoice_number",
                            "ocrr_structured",
                            workspaceId,
                            invoiceNumberWord.ocrWordId(),
                            "value",
                            1))))
            : List.of();
    invoiceExtractionRepository.save(
        new InvoiceExtraction(
            "invx_structured",
            "wfe_trace",
            "ocrr_structured",
            jobId,
            fileId,
            workspaceId,
            "heuristic_invoice",
            "1",
            status,
            warnings,
            new BigDecimal("94.00"),
            fields,
            now,
            now));
  }

  private OcrWord saveStructuredResult(String jobId, String fileId, String workspaceId) {
    Instant now = Instant.parse("2026-07-11T12:00:00Z");
    OcrWord invoiceLabelWord =
        new OcrWord(
            "ocrw_invoice_label",
            "ocrp_structured",
            "ocrr_structured",
            workspaceId,
            0,
            1,
            0,
            0,
            0,
            1,
            0,
            "Invoice Number",
            new BigDecimal("99.00"),
            10,
            10,
            100,
            18,
            "tesseract_tsv",
            now);
    OcrWord invoiceNumberWord =
        new OcrWord(
            "ocrw_invoice_number",
            "ocrp_structured",
            "ocrr_structured",
            workspaceId,
            1,
            1,
            1,
            0,
            0,
            1,
            1,
            "INV-42",
            new BigDecimal("94.00"),
            120,
            10,
            80,
            18,
            "tesseract_tsv",
            now);
    OcrPageResult page =
        new OcrPageResult(
            "ocrp_structured",
            "ocrr_structured",
            workspaceId,
            1,
            "tesseract_tsv",
            "STRUCTURED_OCR_TEXT",
            1,
            now,
            List.of(invoiceLabelWord, invoiceNumberWord));
    ocrResultRepository.save(
        new OcrDocumentResult(
            "ocrr_structured",
            jobId,
            fileId,
            workspaceId,
            "tesseract",
            "5.5.0",
            "STRUCTURED_OCR_TEXT",
            1,
            2,
            now,
            now,
            List.of(page)));
    return invoiceNumberWord;
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
  }

  private JsonNode getJobDetail(String jobId, String workspaceId) throws Exception {
    HttpResponse<String> response =
        httpClient.send(
            request("/api/media/ocr-jobs/" + jobId)
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, workspaceId)
                .build(),
            BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return objectMapper.readTree(response.body());
  }

  private List<String> entryIds(JsonNode lifecycle) {
    List<String> ids = new ArrayList<>();
    lifecycle.path("entries").forEach(entry -> ids.add(entry.path("entryId").asText()));
    return ids;
  }

  private void saveDriveFile(
      String fileId, String workspaceId, String filename, String contentType) {
    Instant now = Instant.parse("2026-05-22T10:00:00Z");
    EncryptedText encryptedName = encryptionService.encryptText(filename);
    driveFileRepository.save(
        new DriveFileMetadata(
            fileId,
            workspaceId,
            PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
            DriveFileVisibility.PRIVATE,
            encryptedName.ciphertextBase64(),
            contentType,
            1024,
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "workspaces/" + workspaceId + "/drive/" + fileId + "/original",
            FileEncryptionService.ALGORITHM,
            "test-key",
            "content-iv",
            encryptedName.ivBase64(),
            now,
            now));
  }

  private void seedWorkspaceMember(String actorId, String workspaceId, String role) {
    jdbcTemplate.update(
        """
        insert into identity_users (
          user_id, display_name, email, avatar_initials, status, is_seeded, created_at, updated_at
        ) values (?, ?, ?, ?, 'active', false, current_timestamp, current_timestamp)
        """,
        actorId,
        actorId,
        actorId + "@example.test",
        "TS");
    jdbcTemplate.update(
        """
        insert into workspace_memberships (
          workspace_id, user_id, role, is_default, created_at, updated_at
        ) values (?, ?, ?, false, current_timestamp, current_timestamp)
        """,
        workspaceId,
        actorId,
        role);
  }

  private OcrJob job(String jobId, String fileId, String workspaceId, OcrJobStatus status) {
    Instant now = Instant.parse("2026-05-22T10:00:00Z");
    String extractedText = "Fake extracted invoice total 124.00 EUR";
    return new OcrJob(
        jobId,
        fileId,
        workspaceId,
        PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
        "evt_uploaded",
        "corr_123",
        "application/pdf",
        "workspaces/" + workspaceId + "/drive/" + fileId + "/original",
        status,
        "mock",
        1,
        3,
        extractedText,
        extractedText.length(),
        null,
        null,
        now,
        now,
        now,
        null,
        null,
        now,
        now);
  }

  private OcrJob completedJob(String jobId, String fileId, String workspaceId) {
    Instant queuedAt = Instant.parse("2026-05-22T10:01:00Z");
    Instant completedAt = Instant.parse("2026-05-22T10:04:00Z");
    String extractedText = "Fake extracted invoice total and lifecycle_text_secret";
    return new OcrJob(
        jobId,
        fileId,
        workspaceId,
        PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
        "evt_upload",
        "corr_trace",
        "application/pdf",
        "workspaces/" + workspaceId + "/drive/" + fileId + "/original",
        OcrJobStatus.COMPLETED,
        "mock",
        2,
        3,
        extractedText,
        extractedText.length(),
        null,
        null,
        queuedAt,
        Instant.parse("2026-05-22T10:03:00Z"),
        completedAt,
        null,
        null,
        queuedAt,
        completedAt);
  }

  private OcrJob retryingJob(String jobId, String fileId, String workspaceId) {
    Instant queuedAt = Instant.parse("2026-05-22T11:01:00Z");
    return new OcrJob(
        jobId,
        fileId,
        workspaceId,
        PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
        "evt_retry_upload",
        "corr_retry",
        "application/pdf",
        "storage-key-secret",
        OcrJobStatus.QUEUED,
        "mock",
        1,
        3,
        null,
        null,
        "MOCK_OCR_FAILED",
        "private invoice line and secret://token",
        queuedAt,
        Instant.parse("2026-05-22T11:02:00Z"),
        null,
        null,
        Instant.parse("2026-05-22T11:10:00Z"),
        queuedAt,
        Instant.parse("2026-05-22T11:03:00Z"));
  }

  private OcrJob failedJob(String jobId, String fileId, String workspaceId) {
    Instant queuedAt = Instant.parse("2026-05-22T12:00:00Z");
    Instant failedAt = Instant.parse("2026-05-22T12:03:00Z");
    return new OcrJob(
        jobId,
        fileId,
        workspaceId,
        PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
        "evt_missing_upload",
        "corr_failed",
        "application/pdf",
        "storage-key-secret",
        OcrJobStatus.FAILED,
        "mock",
        3,
        3,
        null,
        null,
        "raw OCR text: secret invoice",
        "raw OCR text: secret invoice",
        queuedAt,
        Instant.parse("2026-05-22T12:02:00Z"),
        null,
        failedAt,
        null,
        queuedAt,
        failedAt);
  }

  private void saveEvent(
      String eventId,
      String eventType,
      String occurredAt,
      String workspaceId,
      String correlationId,
      String causationId,
      String source,
      String publishedAt) {
    jdbcTemplate.update(
        """
        insert into event_outbox (
          event_id, event_type, version, occurred_at, workspace_id, actor_id,
          correlation_id, causation_id, source, idempotency_key, payload_json,
          envelope_json, published_at, created_at
        ) values (?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        eventId,
        eventType,
        Instant.parse(occurredAt),
        workspaceId,
        PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
        correlationId,
        causationId,
        source,
        "test:" + eventId,
        "{\"value\":\"payload_secret\"}",
        "{\"value\":\"envelope_secret\"}",
        publishedAt == null ? null : Instant.parse(publishedAt),
        Instant.parse(occurredAt));
  }

  private void saveConsumption(String consumer, String eventId, String consumedAt) {
    jdbcTemplate.update(
        """
        insert into event_consumptions (consumer_name, idempotency_key, event_id, consumed_at)
        values (?, ?, ?, ?)
        """,
        consumer,
        "test-consumption:" + consumer + ":" + eventId,
        eventId,
        Instant.parse(consumedAt));
  }

  private void saveWorkflowExecution(String workspaceId) {
    jdbcTemplate.update(
        """
        insert into workflow_executions (
          execution_id, workflow_id, workflow_version_id, workflow_version_number,
          workspace_id, actor_id, correlation_id, trigger_type, source_event_id,
          source_event_type, trigger_idempotency_key, status, retry_count,
          failure_reason, started_at, completed_at, failed_at, created_at, updated_at
        ) values (?, ?, ?, 2, ?, ?, ?, 'event', ?, 'OcrCompleted', ?, 'completed', 0,
          null, ?, ?, null, ?, ?)
        """,
        "wfe_trace",
        "flow_lifecycle_trace",
        "wfv_lifecycle_trace_v2",
        workspaceId,
        PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
        "corr_trace",
        "evt_completed",
        "test:workflow:trace",
        Instant.parse("2026-05-22T10:05:00Z"),
        Instant.parse("2026-05-22T10:06:00Z"),
        Instant.parse("2026-05-22T10:05:00Z"),
        Instant.parse("2026-05-22T10:06:00Z"));
  }

  private void saveWorkflowStep(String workspaceId) {
    jdbcTemplate.update(
        """
        insert into workflow_step_executions (
          step_execution_id, execution_id, workflow_id, workspace_id, step_key,
          step_name, action_type, status, retry_count, failure_reason, input_json,
          output_json, started_at, completed_at, failed_at, created_at, updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, 'completed', 0, null, ?, ?, ?, ?, null, ?, ?)
        """,
        "wfs_extract",
        "wfe_trace",
        "flow_lifecycle_trace",
        workspaceId,
        "extract-invoice-fields",
        "Extract invoice fields",
        "extract_invoice_fields",
        "{\"secret\":\"step_input_secret\"}",
        "{\"secret\":\"step_output_secret\"}",
        Instant.parse("2026-05-22T10:05:10Z"),
        Instant.parse("2026-05-22T10:05:30Z"),
        Instant.parse("2026-05-22T10:05:10Z"),
        Instant.parse("2026-05-22T10:05:30Z"));
  }

  private void saveWorkflowDefinition(String workspaceId) {
    Instant createdAt = Instant.parse("2026-05-22T10:04:30Z");
    jdbcTemplate.update(
        """
        insert into workflows (
          workflow_id, workspace_id, name, description, status, current_version_id,
          current_version_number, created_by, updated_by, created_at, updated_at
        ) values (?, ?, ?, ?, 'active', null, 2, ?, ?, ?, ?)
        """,
        "flow_lifecycle_trace",
        workspaceId,
        "Lifecycle trace workflow",
        "Test workflow for the OCR lifecycle projection.",
        PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
        PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
        createdAt,
        createdAt);
    jdbcTemplate.update(
        """
        insert into workflow_versions (
          version_id, workflow_id, workspace_id, version_number, definition_json,
          created_by, created_at, published_at
        ) values (?, ?, ?, 2, ?, ?, ?, ?)
        """,
        "wfv_lifecycle_trace_v2",
        "flow_lifecycle_trace",
        workspaceId,
        "{\"trigger\":{\"type\":\"event\",\"eventType\":\"OcrCompleted\"},\"steps\":[]}",
        PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
        createdAt,
        createdAt);
  }

  private void saveAudit(String workspaceId) {
    jdbcTemplate.update(
        """
        insert into audit_records (
          audit_id, action, resource_type, resource_id, workspace_id, actor_id,
          correlation_id, occurred_at, outcome, attributes_json
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        "aud_trace",
        "flows.invoice_automation.completed",
        "workflow_execution",
        "wfe_trace",
        workspaceId,
        PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
        "corr_trace",
        Instant.parse("2026-05-22T10:06:30Z"),
        "SUCCESS",
        "{\"secret\":\"audit_attribute_secret\"}");
  }
}
