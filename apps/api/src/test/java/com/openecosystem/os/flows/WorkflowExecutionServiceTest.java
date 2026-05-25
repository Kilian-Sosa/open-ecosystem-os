package com.openecosystem.os.flows;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openecosystem.os.OpenEcosystemApiApplication;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = OpenEcosystemApiApplication.class)
class WorkflowExecutionServiceTest {

  @Autowired private WorkflowService workflowService;
  @Autowired private OcrCompletedWorkflowTriggerService triggerService;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from event_consumptions");
    jdbcTemplate.update("delete from search_documents");
    jdbcTemplate.update("delete from demo_invoice_extractions");
    jdbcTemplate.update("delete from demo_invoice_runs");
    jdbcTemplate.update("delete from knowledge_items");
    jdbcTemplate.update("delete from notifications");
    jdbcTemplate.update("delete from workflow_step_executions");
    jdbcTemplate.update("delete from workflow_executions");
    jdbcTemplate.update("update workflows set current_version_id = null");
    jdbcTemplate.update("delete from workflow_versions");
    jdbcTemplate.update("delete from workflows");
    jdbcTemplate.update("delete from event_outbox");
    jdbcTemplate.update("delete from audit_records");
    jdbcTemplate.update("delete from ocr_jobs");
    jdbcTemplate.update("delete from drive_files");
  }

  @Test
  void manualRunCreatesExecutionStepsNotificationAuditKnowledgeAndOutboxEvents() throws Exception {
    String workflowId = createWorkflow("manual", fullDefinition("manual", null));

    WorkflowExecutionDetailResponse execution = workflowService.runWorkflowManually(workflowId);

    assertThat(execution.status()).isEqualTo("completed");
    assertThat(execution.triggerType()).isEqualTo("manual");
    assertThat(execution.steps()).hasSize(3);
    assertThat(execution.steps()).allMatch(step -> step.status().equals("completed"));

    assertThat(count("notifications")).isEqualTo(1);
    assertThat(count("knowledge_items")).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from audit_records where action = 'flows.test.audit'",
                Integer.class))
        .isEqualTo(1);
    assertThat(outboxCount("WorkflowTriggered")).isEqualTo(1);
    assertThat(outboxCount("WorkflowExecutionStarted")).isEqualTo(1);
    assertThat(outboxCount("WorkflowStepCompleted")).isEqualTo(3);
    assertThat(outboxCount("WorkflowExecutionCompleted")).isEqualTo(1);
    assertThat(outboxCount("NotificationCreated")).isEqualTo(1);
  }

  @Test
  void ocrCompletedTriggerRunsActiveWorkflowOnceForDuplicateDelivery() throws Exception {
    createWorkflow("event", fullDefinition("event", "OcrCompleted"));
    OcrCompletedEvent event = ocrCompletedEvent("evt_ocr_completed", "ocr_123", "file_123");

    triggerService.trigger(event);
    triggerService.trigger(event);

    assertThat(count("workflow_executions")).isEqualTo(1);
    assertThat(count("workflow_step_executions")).isEqualTo(3);
    assertThat(count("notifications")).isEqualTo(1);
    assertThat(count("knowledge_items")).isEqualTo(1);
    assertThat(count("event_consumptions")).isEqualTo(1);

    Map<String, Object> knowledgeItem =
        jdbcTemplate.queryForMap("select * from knowledge_items limit 1");
    assertThat(knowledgeItem.get("source_file_id")).isEqualTo("file_123");
    assertThat(knowledgeItem.get("source_ocr_job_id")).isEqualTo("ocr_123");
    assertThat(knowledgeItem.get("metadata_json"))
        .asString()
        .contains("\"extractedTextLength\":2048");
    assertThat(knowledgeItem.get("metadata_json")).asString().doesNotContain("Test OCR text");
  }

  @Test
  void ocrCompletedTriggerExtractsFakeInvoiceFieldsAndRequestsSearchIndexing() throws Exception {
    createWorkflow("event", invoiceDemoDefinition());
    insertCompletedOcrJob("ocr_invoice_demo", "file_invoice_demo");
    OcrCompletedEvent event =
        ocrCompletedEvent("evt_ocr_invoice_demo", "ocr_invoice_demo", "file_invoice_demo");

    triggerService.trigger(event);

    assertThat(count("workflow_executions")).isEqualTo(1);
    assertThat(count("demo_invoice_extractions")).isEqualTo(1);
    assertThat(count("search_documents")).isEqualTo(1);
    assertThat(outboxCount("IndexingRequested")).isEqualTo(1);

    Map<String, Object> extraction =
        jdbcTemplate.queryForMap("select * from demo_invoice_extractions limit 1");
    assertThat(extraction.get("invoice_number")).isEqualTo("TEST-INV-2026-0001");
    assertThat(extraction.get("is_test_data")).isEqualTo(true);

    Map<String, Object> indexingEvent =
        jdbcTemplate.queryForMap(
            "select * from event_outbox where event_type = 'IndexingRequested'");
    assertThat(indexingEvent.get("payload_json")).asString().contains("searchDocumentId");
    assertThat(indexingEvent.get("payload_json")).asString().doesNotContain("ES00 0000");
    assertThat(indexingEvent.get("payload_json")).asString().doesNotContain("B00000000");
  }

  @Test
  void actionFailureRecordsStepAndExecutionFailureReason() throws Exception {
    String workflowId =
        createWorkflow(
            "failing",
            """
            {
              "trigger": { "type": "manual" },
              "steps": [
                {
                  "id": "notify",
                  "name": "Notify",
                  "action": { "type": "create_notification" }
                }
              ]
            }
            """);

    WorkflowExecutionDetailResponse execution = workflowService.runWorkflowManually(workflowId);

    assertThat(execution.status()).isEqualTo("failed");
    assertThat(execution.retryCount()).isEqualTo(1);
    assertThat(execution.failureReason()).contains("Notification title is required");
    assertThat(execution.steps()).hasSize(1);
    assertThat(execution.steps().getFirst().status()).isEqualTo("failed");
    assertThat(execution.steps().getFirst().retryCount()).isEqualTo(1);
    assertThat(count("notifications")).isZero();
    assertThat(outboxCount("WorkflowStepFailed")).isEqualTo(1);
    assertThat(outboxCount("WorkflowExecutionFailed")).isEqualTo(1);
  }

  private String createWorkflow(String name, String definitionJson) throws Exception {
    WorkflowService.CreatedWorkflowResponse response =
        workflowService.createWorkflow(
            new WorkflowSaveRequest(
                name, "Test workflow", "active", objectMapper.readTree(definitionJson)));
    return response.workflow().workflowId();
  }

  private String fullDefinition(String triggerType, String eventType) {
    String trigger =
        eventType == null
            ? """
            { "type": "%s" }
            """
                .formatted(triggerType)
            : """
            { "type": "%s", "eventType": "%s" }
            """
                .formatted(triggerType, eventType);
    return """
    {
      "trigger": %s,
      "steps": [
        {
          "id": "notify",
          "name": "Create notification",
          "action": {
            "type": "create_notification",
            "title": "OCR completed",
            "body": "Ready for review",
            "severity": "info"
          }
        },
        {
          "id": "audit",
          "name": "Create audit entry",
          "action": {
            "type": "create_audit_entry",
            "action": "flows.test.audit",
            "resourceType": "workflow_execution",
            "attributes": { "test": "true" }
          }
        },
        {
          "id": "knowledge",
          "name": "Create Knowledge item",
          "action": {
            "type": "create_knowledge_item_placeholder",
            "title": "Knowledge placeholder",
            "summary": "Created from workflow"
          }
        }
      ]
    }
    """
        .formatted(trigger);
  }

  private String invoiceDemoDefinition() {
    return """
    {
      "trigger": { "type": "event", "eventType": "OcrCompleted" },
      "steps": [
        {
          "id": "extract",
          "name": "Extract fake/test invoice fields",
          "action": { "type": "extract_invoice_fields" }
        },
        {
          "id": "index",
          "name": "Request search indexing",
          "action": { "type": "request_search_indexing" }
        }
      ]
    }
    """;
  }

  private OcrCompletedEvent ocrCompletedEvent(String eventId, String jobId, String fileId) {
    Instant now = Instant.parse("2026-05-22T10:00:00Z");
    return new OcrCompletedEvent(
        eventId,
        1,
        now,
        "wrk_dev_placeholder",
        "usr_dev_placeholder",
        "corr_123",
        "evt_ocr_requested",
        "media:ocr:" + jobId + ":completed:v1",
        jobId,
        fileId,
        "mock",
        1,
        2048,
        now);
  }

  private void insertCompletedOcrJob(String jobId, String fileId) {
    Instant now = Instant.parse("2026-05-22T10:00:00Z");
    String extractedText =
        """
        Mock OCR result - fake/test data only
        Invoice number: TEST-INV-2026-0001
        Supplier: Demo Supplies S.L. (fake/test data)
        Test NIF: B00000000 (test data)
        Test IBAN: ES00 0000 0000 0000 0000 0000 (test data)
        Total: 124.00 EUR
        Due date: 2026-06-15
        """
            .trim();
    jdbcTemplate.update(
        """
        insert into ocr_jobs (
          job_id,
          file_id,
          workspace_id,
          actor_id,
          source_event_id,
          correlation_id,
          content_type,
          storage_key,
          status,
          provider,
          attempt_count,
          max_attempts,
          extracted_text,
          extracted_text_length,
          failure_code,
          failure_message,
          queued_at,
          processing_started_at,
          completed_at,
          failed_at,
          next_attempt_at,
          created_at,
          updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, null, null, ?, ?, ?, null, null, ?, ?)
        """,
        jobId,
        fileId,
        "wrk_dev_placeholder",
        "usr_dev_placeholder",
        "evt_uploaded",
        "corr_123",
        "application/pdf",
        "workspaces/wrk_dev_placeholder/drive/" + fileId + "/original",
        "completed",
        "mock",
        1,
        3,
        extractedText,
        extractedText.length(),
        now,
        now,
        now,
        now,
        now);
  }

  private int count(String table) {
    Integer count = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    return count == null ? 0 : count;
  }

  private int outboxCount(String eventType) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from event_outbox where event_type = ?", Integer.class, eventType);
    return count == null ? 0 : count;
  }
}
