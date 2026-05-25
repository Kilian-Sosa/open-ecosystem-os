package com.openecosystem.os.flows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openecosystem.os.audit.AuditOutcome;
import com.openecosystem.os.audit.AuditRecord;
import com.openecosystem.os.audit.JdbcAuditRecordRepository;
import com.openecosystem.os.common.events.EventEnvelope;
import com.openecosystem.os.common.events.JdbcEventOutboxRepository;
import com.openecosystem.os.common.ids.Ids;
import com.openecosystem.os.demo.DemoInvoiceExtraction;
import com.openecosystem.os.demo.DemoInvoiceRun;
import com.openecosystem.os.demo.JdbcDemoInvoiceRepository;
import com.openecosystem.os.knowledge.JdbcKnowledgeItemRepository;
import com.openecosystem.os.knowledge.KnowledgeItem;
import com.openecosystem.os.media.OcrJob;
import com.openecosystem.os.media.OcrJobRepository;
import com.openecosystem.os.notifications.JdbcNotificationRepository;
import com.openecosystem.os.notifications.NotificationCreatedPayload;
import com.openecosystem.os.notifications.NotificationRecord;
import com.openecosystem.os.notifications.NotificationsModule;
import com.openecosystem.os.search.IndexingRequestedPayload;
import com.openecosystem.os.search.JdbcSearchDocumentRepository;
import com.openecosystem.os.search.SearchDocument;
import com.openecosystem.os.search.SearchDocumentStatus;
import com.openecosystem.os.search.SearchModule;
import com.openecosystem.os.search.SearchProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WorkflowRunner {

  private static final String WORKFLOW_TRIGGERED = "WorkflowTriggered";
  private static final String WORKFLOW_EXECUTION_STARTED = "WorkflowExecutionStarted";
  private static final String WORKFLOW_STEP_COMPLETED = "WorkflowStepCompleted";
  private static final String WORKFLOW_STEP_FAILED = "WorkflowStepFailed";
  private static final String WORKFLOW_EXECUTION_COMPLETED = "WorkflowExecutionCompleted";
  private static final String WORKFLOW_EXECUTION_FAILED = "WorkflowExecutionFailed";
  private static final String NOTIFICATION_CREATED = "NotificationCreated";
  private static final String INDEXING_REQUESTED = "IndexingRequested";
  private static final String RESOURCE_TYPE_WORKFLOW_EXECUTION = "workflow_execution";
  private static final String SEARCH_SOURCE_TYPE_DEMO_INVOICE_EXTRACTION =
      "demo_invoice_extraction";

  private final WorkflowDefinitionValidator definitionValidator;
  private final JdbcWorkflowExecutionRepository executionRepository;
  private final JdbcNotificationRepository notificationRepository;
  private final JdbcKnowledgeItemRepository knowledgeItemRepository;
  private final JdbcDemoInvoiceRepository demoInvoiceRepository;
  private final OcrJobRepository ocrJobRepository;
  private final JdbcSearchDocumentRepository searchDocumentRepository;
  private final JdbcAuditRecordRepository auditRecordRepository;
  private final JdbcEventOutboxRepository eventOutboxRepository;
  private final SearchProperties searchProperties;
  private final ObjectMapper objectMapper;
  private final TransactionTemplate transactionTemplate;

  public WorkflowRunner(
      WorkflowDefinitionValidator definitionValidator,
      JdbcWorkflowExecutionRepository executionRepository,
      JdbcNotificationRepository notificationRepository,
      JdbcKnowledgeItemRepository knowledgeItemRepository,
      JdbcDemoInvoiceRepository demoInvoiceRepository,
      OcrJobRepository ocrJobRepository,
      JdbcSearchDocumentRepository searchDocumentRepository,
      JdbcAuditRecordRepository auditRecordRepository,
      JdbcEventOutboxRepository eventOutboxRepository,
      SearchProperties searchProperties,
      ObjectMapper objectMapper,
      TransactionTemplate transactionTemplate) {
    this.definitionValidator = definitionValidator;
    this.executionRepository = executionRepository;
    this.notificationRepository = notificationRepository;
    this.knowledgeItemRepository = knowledgeItemRepository;
    this.demoInvoiceRepository = demoInvoiceRepository;
    this.ocrJobRepository = ocrJobRepository;
    this.searchDocumentRepository = searchDocumentRepository;
    this.auditRecordRepository = auditRecordRepository;
    this.eventOutboxRepository = eventOutboxRepository;
    this.searchProperties = searchProperties;
    this.objectMapper = objectMapper;
    this.transactionTemplate = transactionTemplate;
  }

  public WorkflowExecution run(WorkflowRunCommand command) {
    WorkflowWithVersion workflowWithVersion = command.workflowWithVersion();
    Workflow workflow = workflowWithVersion.workflow();
    return transactionTemplate.execute(
        status -> {
          WorkflowExecution existing =
              executionRepository
                  .findByTriggerIdempotencyKey(
                      workflow.workspaceId(), command.triggerIdempotencyKey())
                  .orElse(null);
          if (existing != null) return existing;

          WorkflowDefinition definition =
              definitionValidator.validate(workflowWithVersion.version().definition());
          Instant now = Instant.now();
          WorkflowExecution execution = runningExecution(command, now);
          executionRepository.insertExecution(execution);
          eventOutboxRepository.save(workflowTriggeredEnvelope(command, execution, now));
          eventOutboxRepository.save(workflowExecutionStartedEnvelope(command, execution, now));

          int completedSteps = 0;
          for (WorkflowStepDefinition step : definition.steps()) {
            String stepExecutionId = Ids.newId("wfs");
            executionRepository.insertStep(
                runningStep(command, execution, step, stepExecutionId, now));
            try {
              JsonNode output = executeStep(command, execution, step, stepExecutionId, now);
              Instant completedAt = Instant.now();
              executionRepository.completeStep(stepExecutionId, output, completedAt);
              eventOutboxRepository.save(
                  workflowStepCompletedEnvelope(command, execution, step, completedAt));
              completedSteps++;
            } catch (RuntimeException exception) {
              String failureReason = sanitizedMessage(exception);
              Instant failedAt = Instant.now();
              executionRepository.failStep(stepExecutionId, failureReason, failedAt);
              executionRepository.failExecution(execution.executionId(), failureReason, failedAt);
              eventOutboxRepository.save(
                  workflowStepFailedEnvelope(command, execution, step, failureReason, failedAt));
              eventOutboxRepository.save(
                  workflowExecutionFailedEnvelope(command, execution, failureReason, failedAt));
              return executionRepository
                  .findByIdForWorkspace(execution.executionId(), execution.workspaceId())
                  .orElse(execution);
            }
          }

          Instant completedAt = Instant.now();
          executionRepository.completeExecution(execution.executionId(), completedAt);
          eventOutboxRepository.save(
              workflowExecutionCompletedEnvelope(command, execution, completedSteps, completedAt));
          return executionRepository
              .findByIdForWorkspace(execution.executionId(), execution.workspaceId())
              .orElse(execution);
        });
  }

  private WorkflowExecution runningExecution(WorkflowRunCommand command, Instant now) {
    WorkflowWithVersion workflowWithVersion = command.workflowWithVersion();
    Workflow workflow = workflowWithVersion.workflow();
    WorkflowVersion version = workflowWithVersion.version();
    return new WorkflowExecution(
        Ids.newId("wfe"),
        workflow.workflowId(),
        version.versionId(),
        version.versionNumber(),
        workflow.workspaceId(),
        command.actorId(),
        command.correlationId(),
        command.triggerType(),
        command.sourceEventId(),
        command.sourceEventType(),
        command.triggerIdempotencyKey(),
        WorkflowExecutionStatus.RUNNING,
        0,
        null,
        now,
        null,
        null,
        now,
        now);
  }

  private WorkflowStepExecution runningStep(
      WorkflowRunCommand command,
      WorkflowExecution execution,
      WorkflowStepDefinition step,
      String stepExecutionId,
      Instant now) {
    return new WorkflowStepExecution(
        stepExecutionId,
        execution.executionId(),
        execution.workflowId(),
        execution.workspaceId(),
        step.id(),
        step.name(),
        step.actionType(),
        WorkflowStepExecutionStatus.RUNNING,
        0,
        null,
        stepInput(command),
        objectMapper.createObjectNode(),
        now,
        null,
        null,
        now,
        now);
  }

  private JsonNode executeStep(
      WorkflowRunCommand command,
      WorkflowExecution execution,
      WorkflowStepDefinition step,
      String stepExecutionId,
      Instant now) {
    return switch (step.actionType()) {
      case WorkflowDefinitionValidator.ACTION_EXTRACT_INVOICE_FIELDS ->
          extractInvoiceFields(command, execution, step, now);
      case WorkflowDefinitionValidator.ACTION_CREATE_NOTIFICATION ->
          createNotification(command, execution, step, stepExecutionId, now);
      case WorkflowDefinitionValidator.ACTION_CREATE_AUDIT_ENTRY ->
          createAuditEntry(command, execution, step, now);
      case WorkflowDefinitionValidator.ACTION_CREATE_KNOWLEDGE_ITEM_PLACEHOLDER ->
          createKnowledgeItem(command, execution, step, stepExecutionId, now);
      case WorkflowDefinitionValidator.ACTION_REQUEST_SEARCH_INDEXING ->
          requestSearchIndexing(command, execution, step, now);
      default ->
          throw new IllegalArgumentException("Unsupported workflow action: " + step.actionType());
    };
  }

  private JsonNode extractInvoiceFields(
      WorkflowRunCommand command,
      WorkflowExecution execution,
      WorkflowStepDefinition step,
      Instant now) {
    OcrCompletedEvent ocrEvent = requiredOcrEvent(command);
    Optional<DemoInvoiceExtraction> existing =
        demoInvoiceRepository.findExtractionByWorkflowExecutionId(execution.executionId());
    if (existing.isPresent())
      return extractionOutput(existing.get());

    OcrJob ocrJob =
        ocrJobRepository
            .findByIdForWorkspace(ocrEvent.jobId(), execution.workspaceId())
            .orElseThrow(() -> new IllegalStateException("Completed OCR job was not found"));
    if (ocrJob.extractedText() == null || ocrJob.extractedText().isBlank())
      throw new IllegalStateException("Completed OCR job does not contain extracted text");

    DemoInvoiceExtraction extraction = demoExtraction(ocrJob, execution, now);
    demoInvoiceRepository.saveExtraction(extraction);
    return extractionOutput(extraction);
  }

  private JsonNode requestSearchIndexing(
      WorkflowRunCommand command,
      WorkflowExecution execution,
      WorkflowStepDefinition step,
      Instant now) {
    DemoInvoiceExtraction extraction =
        demoInvoiceRepository
            .findExtractionByWorkflowExecutionId(execution.executionId())
            .orElseThrow(() -> new IllegalStateException("Demo invoice extraction was not found"));

    Optional<SearchDocument> existing =
        searchDocumentRepository.findBySource(
            execution.workspaceId(),
            SEARCH_SOURCE_TYPE_DEMO_INVOICE_EXTRACTION,
            extraction.extractionId());
    SearchDocument document = existing.orElseGet(() -> searchDocument(extraction, execution, now));
    if (existing.isEmpty()) {
      searchDocumentRepository.save(document);
      eventOutboxRepository.save(indexingRequestedEnvelope(command, execution, document, now));
    }

    ObjectNode output = objectMapper.createObjectNode();
    output.put("searchDocumentId", document.searchDocumentId());
    output.put("status", document.status().value());
    output.put("sourceType", document.sourceType());
    return output;
  }

  private JsonNode createNotification(
      WorkflowRunCommand command,
      WorkflowExecution execution,
      WorkflowStepDefinition step,
      String stepExecutionId,
      Instant now) {
    String title = requiredActionText(step.action(), "title", "Notification title is required");
    String body = optionalActionText(step.action(), "body", "Workflow notification created.");
    String severity = optionalActionText(step.action(), "severity", "info");
    if (!severity.equals("info") && !severity.equals("warning") && !severity.equals("danger")) {
      severity = "info";
    }

    String notificationId = Ids.newId("ntf");
    notificationRepository.save(
        new NotificationRecord(
            notificationId,
            execution.workspaceId(),
            execution.actorId(),
            title,
            body,
            severity,
            "unread",
            RESOURCE_TYPE_WORKFLOW_EXECUTION,
            execution.executionId(),
            execution.correlationId(),
            idempotencyKey(execution, stepExecutionId, "notification"),
            now,
            null));
    eventOutboxRepository.save(
        notificationCreatedEnvelope(command, execution, notificationId, title, severity, now));

    ObjectNode output = objectMapper.createObjectNode();
    output.put("notificationId", notificationId);
    output.put("severity", severity);
    return output;
  }

  private JsonNode createAuditEntry(
      WorkflowRunCommand command,
      WorkflowExecution execution,
      WorkflowStepDefinition step,
      Instant now) {
    String action =
        optionalActionText(step.action(), "action", "flows.workflow.audit_entry_created");
    String resourceType =
        optionalActionText(step.action(), "resourceType", RESOURCE_TYPE_WORKFLOW_EXECUTION);
    Map<String, String> attributes = actionAttributes(command, execution, step);
    String auditId = Ids.newId("aud");
    auditRecordRepository.save(
        new AuditRecord(
            auditId,
            action,
            resourceType,
            execution.executionId(),
            execution.workspaceId(),
            execution.actorId(),
            execution.correlationId(),
            now,
            AuditOutcome.SUCCESS,
            attributes));

    ObjectNode output = objectMapper.createObjectNode();
    output.put("auditId", auditId);
    output.put("action", action);
    return output;
  }

  private JsonNode createKnowledgeItem(
      WorkflowRunCommand command,
      WorkflowExecution execution,
      WorkflowStepDefinition step,
      String stepExecutionId,
      Instant now) {
    String title = requiredActionText(step.action(), "title", "Knowledge item title is required");
    String summary =
        optionalActionText(
            step.action(), "summary", "A Knowledge placeholder was created by a workflow.");
    OcrCompletedEvent ocrEvent = command.ocrCompletedEvent();
    String knowledgeItemId = Ids.newId("knw");
    knowledgeItemRepository.save(
        new KnowledgeItem(
            knowledgeItemId,
            execution.workspaceId(),
            title,
            summary,
            ocrEvent == null ? null : ocrEvent.fileId(),
            ocrEvent == null ? null : ocrEvent.jobId(),
            execution.executionId(),
            execution.sourceEventId(),
            knowledgeMetadata(command, execution),
            execution.actorId(),
            execution.correlationId(),
            idempotencyKey(execution, stepExecutionId, "knowledge"),
            now,
            now));

    ObjectNode output = objectMapper.createObjectNode();
    output.put("knowledgeItemId", knowledgeItemId);
    return output;
  }

  private DemoInvoiceExtraction demoExtraction(
      OcrJob ocrJob, WorkflowExecution execution, Instant now) {
    Optional<DemoInvoiceRun> run =
        demoInvoiceRepository.findRunByFileIdForWorkspace(ocrJob.fileId(), execution.workspaceId());
    ObjectNode metadata = objectMapper.createObjectNode();
    metadata.put("isTestData", true);
    metadata.put("source", "mock_invoice_extractor");
    metadata.put("testDataNotice", "All invoice fields are fake/test data.");
    metadata.put(
        "ocrTextLength", ocrJob.extractedTextLength() == null ? 0 : ocrJob.extractedTextLength());
    metadata.put("workflowExecutionId", execution.executionId());

    return new DemoInvoiceExtraction(
        Ids.newId("invx"),
        run.map(DemoInvoiceRun::runId).orElse(null),
        execution.workspaceId(),
        execution.actorId(),
        ocrJob.fileId(),
        ocrJob.jobId(),
        execution.executionId(),
        "TEST-INV-2026-0001",
        "Demo Supplies S.L. (fake/test data)",
        "Test NIF: B00000000 (test data)",
        "Test IBAN: ES00 0000 0000 0000 0000 0000 (test data)",
        new BigDecimal("124.00"),
        "EUR",
        LocalDate.parse("2026-06-15"),
        true,
        metadata,
        now);
  }

  private JsonNode extractionOutput(DemoInvoiceExtraction extraction) {
    ObjectNode output = objectMapper.createObjectNode();
    output.put("extractionId", extraction.extractionId());
    output.put("invoiceNumber", extraction.invoiceNumber());
    output.put("supplierName", extraction.supplierName());
    output.put("supplierTestNif", extraction.supplierTestNif());
    output.put("supplierTestIban", extraction.supplierTestIban());
    output.put("totalAmount", extraction.totalAmount());
    output.put("currency", extraction.currency());
    output.put("dueDate", extraction.dueDate().toString());
    output.put("isTestData", extraction.testData());
    return output;
  }

  private SearchDocument searchDocument(
      DemoInvoiceExtraction extraction, WorkflowExecution execution, Instant now) {
    ObjectNode metadata = objectMapper.createObjectNode();
    metadata.put("isTestData", true);
    metadata.put("invoiceNumber", extraction.invoiceNumber());
    metadata.put("supplierName", extraction.supplierName());
    metadata.put("currency", extraction.currency());
    metadata.put("totalAmount", extraction.totalAmount());
    metadata.put("dueDate", extraction.dueDate().toString());
    metadata.put("runId", extraction.runId());
    metadata.put("ocrJobId", extraction.ocrJobId());
    metadata.put("fileId", extraction.fileId());

    return new SearchDocument(
        Ids.newId("srch"),
        execution.workspaceId(),
        SEARCH_SOURCE_TYPE_DEMO_INVOICE_EXTRACTION,
        extraction.extractionId(),
        "Fake/test invoice " + extraction.invoiceNumber(),
        "Fake/test invoice extraction from " + extraction.supplierName() + ".",
        searchContent(extraction),
        extraction.runId() == null
            ? "/app/search?q=" + extraction.invoiceNumber()
            : "/app/demo/invoice-automation?runId=" + extraction.runId(),
        execution.correlationId(),
        SearchDocumentStatus.PENDING,
        0,
        searchProperties.maxAttempts(),
        null,
        null,
        metadata,
        now,
        now,
        null,
        null);
  }

  private String searchContent(DemoInvoiceExtraction extraction) {
    return """
    Fake/test invoice result.
    Invoice number: %s
    Supplier: %s
    %s
    %s
    Total: %s %s
    Due date: %s
    All values are fake/test data.
    """
        .formatted(
            extraction.invoiceNumber(),
            extraction.supplierName(),
            extraction.supplierTestNif(),
            extraction.supplierTestIban(),
            extraction.totalAmount(),
            extraction.currency(),
            extraction.dueDate())
        .trim();
  }

  private OcrCompletedEvent requiredOcrEvent(WorkflowRunCommand command) {
    OcrCompletedEvent ocrEvent = command.ocrCompletedEvent();
    if (ocrEvent == null)
      throw new IllegalStateException("Invoice extraction requires an OcrCompleted event");
    return ocrEvent;
  }

  private ObjectNode stepInput(WorkflowRunCommand command) {
    ObjectNode input = objectMapper.createObjectNode();
    input.put("triggerType", command.triggerType().value());
    if (command.sourceEventId() != null) input.put("sourceEventId", command.sourceEventId());
    if (command.sourceEventType() != null) input.put("sourceEventType", command.sourceEventType());
    OcrCompletedEvent ocrEvent = command.ocrCompletedEvent();
    if (ocrEvent != null) {
      input.put("ocrJobId", ocrEvent.jobId());
      input.put("fileId", ocrEvent.fileId());
      input.put("extractedTextLength", ocrEvent.extractedTextLength());
    }
    return input;
  }

  private ObjectNode knowledgeMetadata(WorkflowRunCommand command, WorkflowExecution execution) {
    ObjectNode metadata = objectMapper.createObjectNode();
    metadata.put("workflowId", execution.workflowId());
    metadata.put("executionId", execution.executionId());
    metadata.put("triggerType", command.triggerType().value());
    if (command.sourceEventId() != null) metadata.put("sourceEventId", command.sourceEventId());
    OcrCompletedEvent ocrEvent = command.ocrCompletedEvent();
    if (ocrEvent != null) {
      metadata.put("ocrJobId", ocrEvent.jobId());
      metadata.put("fileId", ocrEvent.fileId());
      metadata.put("extractedTextLength", ocrEvent.extractedTextLength());
    }
    return metadata;
  }

  private Map<String, String> actionAttributes(
      WorkflowRunCommand command, WorkflowExecution execution, WorkflowStepDefinition step) {
    Map<String, String> attributes = new LinkedHashMap<>();
    JsonNode configured = step.action().get("attributes");
    if (configured != null && configured.isObject()) {
      configured
          .fields()
          .forEachRemaining(
              entry -> {
                if (entry.getValue().isValueNode()) {
                  attributes.put(entry.getKey(), entry.getValue().asText());
                }
              });
    }
    attributes.put("workflowId", execution.workflowId());
    attributes.put("executionId", execution.executionId());
    attributes.put("stepId", step.id());
    attributes.put("triggerType", command.triggerType().value());
    if (command.sourceEventId() != null) attributes.put("sourceEventId", command.sourceEventId());
    OcrCompletedEvent ocrEvent = command.ocrCompletedEvent();
    if (ocrEvent != null) {
      attributes.put("ocrJobId", ocrEvent.jobId());
      attributes.put("fileId", ocrEvent.fileId());
      attributes.put("extractedTextLength", Integer.toString(ocrEvent.extractedTextLength()));
    }
    return attributes;
  }

  private EventEnvelope<WorkflowTriggeredPayload> workflowTriggeredEnvelope(
      WorkflowRunCommand command, WorkflowExecution execution, Instant now) {
    return new EventEnvelope<>(
        Ids.newId("evt"),
        WORKFLOW_TRIGGERED,
        1,
        now,
        execution.workspaceId(),
        execution.actorId(),
        execution.correlationId(),
        command.sourceEventId(),
        FlowsModule.NAME,
        "flows:execution:" + execution.executionId() + ":triggered:v1",
        new WorkflowTriggeredPayload(
            execution.workflowId(),
            execution.workflowVersionId(),
            execution.executionId(),
            command.triggerType().value(),
            command.sourceEventType(),
            command.sourceEventId(),
            now));
  }

  private EventEnvelope<WorkflowExecutionStartedPayload> workflowExecutionStartedEnvelope(
      WorkflowRunCommand command, WorkflowExecution execution, Instant now) {
    return new EventEnvelope<>(
        Ids.newId("evt"),
        WORKFLOW_EXECUTION_STARTED,
        1,
        now,
        execution.workspaceId(),
        execution.actorId(),
        execution.correlationId(),
        command.sourceEventId(),
        FlowsModule.NAME,
        "flows:execution:" + execution.executionId() + ":started:v1",
        new WorkflowExecutionStartedPayload(
            execution.workflowId(),
            execution.executionId(),
            execution.workflowVersionNumber(),
            now));
  }

  private EventEnvelope<WorkflowStepCompletedPayload> workflowStepCompletedEnvelope(
      WorkflowRunCommand command,
      WorkflowExecution execution,
      WorkflowStepDefinition step,
      Instant completedAt) {
    return new EventEnvelope<>(
        Ids.newId("evt"),
        WORKFLOW_STEP_COMPLETED,
        1,
        completedAt,
        execution.workspaceId(),
        execution.actorId(),
        execution.correlationId(),
        command.sourceEventId(),
        FlowsModule.NAME,
        "flows:execution:" + execution.executionId() + ":step:" + step.id() + ":completed:v1",
        new WorkflowStepCompletedPayload(
            execution.workflowId(),
            execution.executionId(),
            step.id(),
            step.actionType(),
            completedAt));
  }

  private EventEnvelope<WorkflowStepFailedPayload> workflowStepFailedEnvelope(
      WorkflowRunCommand command,
      WorkflowExecution execution,
      WorkflowStepDefinition step,
      String failureReason,
      Instant failedAt) {
    return new EventEnvelope<>(
        Ids.newId("evt"),
        WORKFLOW_STEP_FAILED,
        1,
        failedAt,
        execution.workspaceId(),
        execution.actorId(),
        execution.correlationId(),
        command.sourceEventId(),
        FlowsModule.NAME,
        "flows:execution:" + execution.executionId() + ":step:" + step.id() + ":failed:v1",
        new WorkflowStepFailedPayload(
            execution.workflowId(),
            execution.executionId(),
            step.id(),
            step.actionType(),
            failureReason,
            failedAt));
  }

  private EventEnvelope<WorkflowExecutionCompletedPayload> workflowExecutionCompletedEnvelope(
      WorkflowRunCommand command,
      WorkflowExecution execution,
      int completedStepCount,
      Instant completedAt) {
    return new EventEnvelope<>(
        Ids.newId("evt"),
        WORKFLOW_EXECUTION_COMPLETED,
        1,
        completedAt,
        execution.workspaceId(),
        execution.actorId(),
        execution.correlationId(),
        command.sourceEventId(),
        FlowsModule.NAME,
        "flows:execution:" + execution.executionId() + ":completed:v1",
        new WorkflowExecutionCompletedPayload(
            execution.workflowId(), execution.executionId(), completedStepCount, completedAt));
  }

  private EventEnvelope<WorkflowExecutionFailedPayload> workflowExecutionFailedEnvelope(
      WorkflowRunCommand command,
      WorkflowExecution execution,
      String failureReason,
      Instant failedAt) {
    return new EventEnvelope<>(
        Ids.newId("evt"),
        WORKFLOW_EXECUTION_FAILED,
        1,
        failedAt,
        execution.workspaceId(),
        execution.actorId(),
        execution.correlationId(),
        command.sourceEventId(),
        FlowsModule.NAME,
        "flows:execution:" + execution.executionId() + ":failed:v1",
        new WorkflowExecutionFailedPayload(
            execution.workflowId(), execution.executionId(), failureReason, failedAt));
  }

  private EventEnvelope<NotificationCreatedPayload> notificationCreatedEnvelope(
      WorkflowRunCommand command,
      WorkflowExecution execution,
      String notificationId,
      String title,
      String severity,
      Instant now) {
    return new EventEnvelope<>(
        Ids.newId("evt"),
        NOTIFICATION_CREATED,
        1,
        now,
        execution.workspaceId(),
        execution.actorId(),
        execution.correlationId(),
        command.sourceEventId(),
        NotificationsModule.NAME,
        "notifications:" + notificationId + ":created:v1",
        new NotificationCreatedPayload(
            notificationId,
            title,
            severity,
            RESOURCE_TYPE_WORKFLOW_EXECUTION,
            execution.executionId(),
            now));
  }

  private EventEnvelope<IndexingRequestedPayload> indexingRequestedEnvelope(
      WorkflowRunCommand command,
      WorkflowExecution execution,
      SearchDocument document,
      Instant now) {
    return new EventEnvelope<>(
        Ids.newId("evt"),
        INDEXING_REQUESTED,
        1,
        now,
        execution.workspaceId(),
        execution.actorId(),
        execution.correlationId(),
        command.sourceEventId(),
        SearchModule.NAME,
        "search:document:" + document.searchDocumentId() + ":requested:v1",
        new IndexingRequestedPayload(
            document.searchDocumentId(),
            document.sourceType(),
            document.sourceId(),
            document.resourceHref(),
            document.attemptCount(),
            document.maxAttempts(),
            now));
  }

  private String requiredActionText(JsonNode action, String fieldName, String message) {
    JsonNode value = action.get(fieldName);
    if (value == null || !value.isTextual() || value.asText().isBlank())
      throw new IllegalArgumentException(message);
    return value.asText().trim();
  }

  private String optionalActionText(JsonNode action, String fieldName, String fallback) {
    JsonNode value = action.get(fieldName);
    if (value == null || !value.isTextual() || value.asText().isBlank()) return fallback;
    return value.asText().trim();
  }

  private String idempotencyKey(
      WorkflowExecution execution, String stepExecutionId, String actionName) {
    return "flows:execution:"
        + execution.executionId()
        + ":step:"
        + stepExecutionId
        + ":"
        + actionName
        + ":v1";
  }

  private String sanitizedMessage(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) return "Workflow action failed";
    return message.length() > 512 ? message.substring(0, 512) : message;
  }
}
