package com.openecosystem.os.media;

import com.openecosystem.os.media.OcrJobLifecycleQueryRepository.AuditRow;
import com.openecosystem.os.media.OcrJobLifecycleQueryRepository.EventRow;
import com.openecosystem.os.media.OcrJobLifecycleQueryRepository.WorkflowExecutionRow;
import com.openecosystem.os.media.OcrJobLifecycleQueryRepository.WorkflowStepRow;
import com.openecosystem.os.media.OcrJobLifecycleResponse.Consumption;
import com.openecosystem.os.media.OcrJobLifecycleResponse.Entry;
import com.openecosystem.os.media.OcrJobLifecycleResponse.Event;
import com.openecosystem.os.media.OcrJobLifecycleResponse.Failure;
import com.openecosystem.os.media.OcrJobLifecycleResponse.Resource;
import com.openecosystem.os.media.OcrJobLifecycleResponse.Retry;
import com.openecosystem.os.media.OcrJobLifecycleResponse.Workflow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;

@Service
public class OcrJobLifecycleProjectionService {

  private static final String FILE_UPLOADED_CONSUMER = "media.file-uploaded.ocr-job";
  private static final String OCR_REQUESTED_CONSUMER = "worker.ocr-requested.processor";
  private static final String OCR_COMPLETED_CONSUMER = "flows.ocr-completed.workflow-trigger";
  private static final String INDEXING_REQUESTED_CONSUMER = "worker.search-indexing.processor";

  private static final Set<String> OCR_EVENT_TYPES =
      Set.of("OcrStarted", "OcrCompleted", "OcrFailed");
  private static final Set<String> OCR_DOWNSTREAM_EVENT_TYPES =
      Set.of("NotificationCreated", "IndexingRequested");
  private static final Set<String> SEARCH_TERMINAL_EVENT_TYPES =
      Set.of("IndexingCompleted", "IndexingFailed");
  private static final Set<String> NOTIFICATION_TERMINAL_EVENT_TYPES =
      Set.of("NotificationSent", "NotificationFailed");

  private static final Comparator<Entry> ENTRY_ORDER =
      Comparator.comparing(Entry::occurredAt, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparingInt(OcrJobLifecycleProjectionService::kindRank)
          .thenComparing(Entry::entryId);

  private final OcrJobLifecycleQueryRepository repository;

  public OcrJobLifecycleProjectionService(OcrJobLifecycleQueryRepository repository) {
    this.repository = repository;
  }

  public OcrJobLifecycleResponse project(OcrJob job) {
    List<EventFact> allEvents =
        groupEvents(repository.findEvents(job.workspaceId(), job.correlationId()));
    EventFact uploaded =
        allEvents.stream()
            .filter(event -> event.eventId().equals(job.sourceEventId()))
            .filter(event -> event.eventType().equals("FileUploaded"))
            .findFirst()
            .orElse(null);

    List<EventFact> requested =
        uploaded == null
            ? List.of()
            : filterEvents(
                allEvents,
                event ->
                    event.eventType().equals("OcrRequested")
                        && uploaded.eventId().equals(event.causationId()));
    Set<String> requestedIds = eventIds(requested);
    List<EventFact> ocrEvents =
        filterEvents(
            allEvents,
            event ->
                OCR_EVENT_TYPES.contains(event.eventType())
                    && requestedIds.contains(event.causationId()));
    List<EventFact> completedEvents =
        filterEvents(ocrEvents, event -> event.eventType().equals("OcrCompleted"));
    Set<String> completedIds = eventIds(completedEvents);

    List<EventFact> downstreamEvents =
        filterEvents(
            allEvents,
            event ->
                OCR_DOWNSTREAM_EVENT_TYPES.contains(event.eventType())
                    && completedIds.contains(event.causationId()));
    Set<String> indexingRequestedIds =
        eventIds(
            filterEvents(downstreamEvents, event -> event.eventType().equals("IndexingRequested")));
    Set<String> notificationCreatedIds =
        eventIds(
            filterEvents(
                downstreamEvents, event -> event.eventType().equals("NotificationCreated")));
    List<EventFact> terminalDownstreamEvents =
        filterEvents(
            allEvents,
            event ->
                (SEARCH_TERMINAL_EVENT_TYPES.contains(event.eventType())
                        && indexingRequestedIds.contains(event.causationId()))
                    || (NOTIFICATION_TERMINAL_EVENT_TYPES.contains(event.eventType())
                        && notificationCreatedIds.contains(event.causationId())));

    LinkedHashMap<String, EventFact> includedEvents = new LinkedHashMap<>();
    addEvent(includedEvents, uploaded);
    requested.forEach(event -> addEvent(includedEvents, event));
    ocrEvents.forEach(event -> addEvent(includedEvents, event));
    downstreamEvents.forEach(event -> addEvent(includedEvents, event));
    terminalDownstreamEvents.forEach(event -> addEvent(includedEvents, event));

    List<WorkflowExecutionRow> executions =
        repository.findWorkflowExecutions(job.workspaceId(), completedIds);
    Set<String> executionIds = new LinkedHashSet<>();
    executions.forEach(execution -> executionIds.add(execution.executionId()));
    List<WorkflowStepRow> steps = repository.findWorkflowSteps(job.workspaceId(), executionIds);
    Map<String, WorkflowExecutionRow> executionsById = new LinkedHashMap<>();
    executions.forEach(execution -> executionsById.put(execution.executionId(), execution));

    Set<String> relevantResourceIds = new LinkedHashSet<>();
    relevantResourceIds.add(job.fileId());
    relevantResourceIds.add(job.jobId());
    relevantResourceIds.addAll(executionIds);
    steps.forEach(step -> relevantResourceIds.add(step.stepExecutionId()));
    List<AuditRow> audits =
        repository.findAudits(job.workspaceId(), job.correlationId(), relevantResourceIds);

    List<Entry> entries = new ArrayList<>();
    includedEvents.values().forEach(event -> entries.add(toEventEntry(event, job)));
    addJobFallbackEntries(entries, job, uploaded, requested, ocrEvents);
    executions.forEach(execution -> addWorkflowExecutionEntries(entries, execution));
    steps.forEach(
        step -> entries.add(toWorkflowStepEntry(step, executionsById.get(step.executionId()))));
    audits.forEach(audit -> entries.add(toAuditEntry(audit)));

    boolean active =
        job.status() == OcrJobStatus.QUEUED
            || job.status() == OcrJobStatus.PROCESSING
            || job.nextAttemptAt() != null
            || executions.stream().anyMatch(execution -> execution.status().equals("running"))
            || includedEvents.values().stream().anyMatch(event -> event.publishedAt() == null);
    boolean partial =
        hasMissingCoreEvidence(job, uploaded, requested, ocrEvents)
            || hasMissingConsumptionEvidence(
                job, uploaded, requested, completedEvents, downstreamEvents);
    boolean failed =
        job.status() == OcrJobStatus.FAILED
            || executions.stream().anyMatch(execution -> execution.status().equals("failed"))
            || steps.stream().anyMatch(step -> step.status().equals("failed"))
            || includedEvents.values().stream()
                .anyMatch(event -> event.eventType().endsWith("Failed"));

    String state = active ? "active" : partial ? "partial" : "complete";
    String outcome = failed ? "failed" : active ? "in_progress" : "completed";
    if (active) addPendingEntries(entries, job, executions, includedEvents.values());
    if (!active && partial) entries.add(unknownEntry(job));

    entries.sort(ENTRY_ORDER);
    return new OcrJobLifecycleResponse(state, outcome, List.copyOf(entries));
  }

  private static List<EventFact> groupEvents(List<EventRow> rows) {
    LinkedHashMap<String, EventFactBuilder> builders = new LinkedHashMap<>();
    for (EventRow row : rows) {
      EventFactBuilder builder =
          builders.computeIfAbsent(
              row.eventId(),
              ignored ->
                  new EventFactBuilder(
                      row.eventId(),
                      row.eventType(),
                      row.version(),
                      row.occurredAt(),
                      row.correlationId(),
                      row.causationId(),
                      row.source(),
                      row.publishedAt()));
      if (row.consumerName() != null && row.consumedAt() != null) {
        builder.consumptions.add(
            new Consumption(row.consumerName(), "consumption_recorded", row.consumedAt()));
      }
    }
    return builders.values().stream().map(EventFactBuilder::build).toList();
  }

  private static Entry toEventEntry(EventFact fact, OcrJob job) {
    Event event =
        new Event(
            fact.eventId(),
            fact.eventType(),
            fact.version(),
            fact.correlationId(),
            fact.causationId(),
            fact.publishedAt() == null ? "outbox_pending" : "publish_recorded",
            fact.publishedAt(),
            fact.consumptions());
    Failure failure =
        fact.eventType().equals("OcrFailed")
            ? new Failure(
                DiagnosticFailureSanitizer.code(job.failureCode()),
                DiagnosticFailureSanitizer.ocrReason(job))
            : fact.eventType().equals("IndexingFailed")
                    || fact.eventType().equals("NotificationFailed")
                ? new Failure(
                    fact.eventType().equals("IndexingFailed")
                        ? "SEARCH_INDEXING_FAILED"
                        : "NOTIFICATION_FAILED",
                    fact.eventType().equals("IndexingFailed")
                        ? "Search indexing failed. Use the correlation ID for permitted"
                            + " diagnostics."
                        : "Notification processing failed. Use the correlation ID for permitted"
                            + " diagnostics.")
                : null;
    Resource resource =
        fact.eventType().equals("FileUploaded")
            ? new Resource("file", job.fileId())
            : fact.eventType().startsWith("Ocr") ? new Resource("ocr_job", job.jobId()) : null;
    return new Entry(
        fact.eventId(),
        phase(fact.eventType()),
        "event",
        label(fact.eventType()),
        eventStatus(fact.eventType()),
        true,
        fact.occurredAt(),
        fact.source(),
        event,
        null,
        null,
        failure,
        resource);
  }

  private static void addJobFallbackEntries(
      List<Entry> entries,
      OcrJob job,
      EventFact uploaded,
      List<EventFact> requested,
      List<EventFact> ocrEvents) {
    if (uploaded == null || requested.isEmpty()) {
      entries.add(
          jobEntry(job.jobId() + ":queued", "OCR job queued", "queued", job.queuedAt(), job, null));
    }
    boolean hasStarted =
        ocrEvents.stream().anyMatch(event -> event.eventType().equals("OcrStarted"));
    if (!hasStarted && job.processingStartedAt() != null) {
      entries.add(
          jobEntry(
              job.jobId() + ":processing",
              "OCR processing recorded",
              "processing",
              job.processingStartedAt(),
              job,
              null));
    }
    boolean hasCompleted =
        ocrEvents.stream().anyMatch(event -> event.eventType().equals("OcrCompleted"));
    if (!hasCompleted && job.completedAt() != null) {
      entries.add(
          jobEntry(
              job.jobId() + ":completed",
              "OCR processing completed",
              "completed",
              job.completedAt(),
              job,
              null));
    }
    boolean hasFailed = ocrEvents.stream().anyMatch(event -> event.eventType().equals("OcrFailed"));
    if (!hasFailed && job.failedAt() != null) {
      entries.add(
          jobEntry(
              job.jobId() + ":failed",
              "OCR processing failed",
              "failed",
              job.failedAt(),
              job,
              new Failure(
                  DiagnosticFailureSanitizer.code(job.failureCode()),
                  DiagnosticFailureSanitizer.ocrReason(job))));
    }
    if (job.nextAttemptAt() != null) {
      entries.add(
          jobEntry(
              job.jobId() + ":retry-scheduled",
              "OCR retry scheduled",
              "retrying",
              job.updatedAt(),
              job,
              new Failure(
                  DiagnosticFailureSanitizer.code(job.failureCode()),
                  DiagnosticFailureSanitizer.ocrReason(job))));
    }
  }

  private static Entry jobEntry(
      String id, String label, String status, Instant occurredAt, OcrJob job, Failure failure) {
    return new Entry(
        id,
        "ocr",
        "job",
        label,
        status,
        true,
        occurredAt,
        "media",
        null,
        null,
        status.equals("retrying")
            ? null
            : new Retry(job.attemptCount(), job.maxAttempts(), job.nextAttemptAt()),
        failure,
        new Resource("ocr_job", job.jobId()));
  }

  private static void addWorkflowExecutionEntries(
      List<Entry> entries, WorkflowExecutionRow execution) {
    Workflow workflow = workflow(execution, null, null);
    entries.add(
        new Entry(
            execution.executionId(),
            "workflow",
            "workflow_execution",
            "Workflow execution started",
            "running",
            true,
            execution.startedAt(),
            "flows",
            null,
            workflow,
            null,
            null,
            new Resource("workflow_execution", execution.executionId())));
    if (execution.completedAt() != null) {
      entries.add(
          new Entry(
              execution.executionId() + ":completed",
              "workflow",
              "workflow_execution",
              "Workflow execution completed",
              "completed",
              true,
              execution.completedAt(),
              "flows",
              null,
              workflow,
              null,
              null,
              new Resource("workflow_execution", execution.executionId())));
    } else if (execution.failedAt() != null) {
      entries.add(
          new Entry(
              execution.executionId() + ":failed",
              "workflow",
              "workflow_execution",
              "Workflow execution failed",
              "failed",
              true,
              execution.failedAt(),
              "flows",
              null,
              workflow,
              null,
              new Failure("WORKFLOW_FAILED", DiagnosticFailureSanitizer.workflowReason()),
              new Resource("workflow_execution", execution.executionId())));
    }
  }

  private static Entry toWorkflowStepEntry(WorkflowStepRow step, WorkflowExecutionRow execution) {
    Instant occurredAt =
        step.completedAt() != null
            ? step.completedAt()
            : step.failedAt() != null ? step.failedAt() : step.startedAt();
    Failure failure =
        step.status().equals("failed")
            ? new Failure("WORKFLOW_STEP_FAILED", DiagnosticFailureSanitizer.workflowReason())
            : null;
    return new Entry(
        step.stepExecutionId(),
        "workflow",
        "workflow_step",
        actionLabel(step.actionType()),
        step.status(),
        true,
        occurredAt,
        "flows",
        null,
        new Workflow(
            step.executionId(),
            step.workflowId(),
            execution.workflowVersionId(),
            execution.workflowVersionNumber(),
            step.stepKey(),
            step.actionType(),
            step.retryCount()),
        null,
        failure,
        new Resource("workflow_step", step.stepExecutionId()));
  }

  private static Entry toAuditEntry(AuditRow audit) {
    return new Entry(
        audit.auditId(),
        "audit",
        "audit",
        audit.action(),
        audit.outcome().toLowerCase(),
        true,
        audit.occurredAt(),
        "audit",
        null,
        null,
        null,
        null,
        new Resource(audit.resourceType(), audit.resourceId()));
  }

  private static void addPendingEntries(
      List<Entry> entries,
      OcrJob job,
      List<WorkflowExecutionRow> executions,
      java.util.Collection<EventFact> events) {
    if (job.status() == OcrJobStatus.QUEUED) {
      String label =
          job.attemptCount() > 0 && job.nextAttemptAt() != null
              ? "Awaiting scheduled OCR retry"
              : "Awaiting OCR processing";
      entries.add(pendingEntry(job.jobId() + ":awaiting-ocr", "ocr", label, job));
    } else if (job.status() == OcrJobStatus.PROCESSING) {
      entries.add(
          pendingEntry(job.jobId() + ":awaiting-outcome", "ocr", "Awaiting OCR outcome", job));
    }
    executions.stream()
        .filter(execution -> execution.status().equals("running"))
        .forEach(
            execution ->
                entries.add(
                    new Entry(
                        execution.executionId() + ":awaiting",
                        "workflow",
                        "pending",
                        "Awaiting workflow completion",
                        "awaiting",
                        false,
                        null,
                        "flows",
                        null,
                        workflow(execution, null, null),
                        null,
                        null,
                        new Resource("workflow_execution", execution.executionId()))));
    events.stream()
        .filter(event -> event.publishedAt() == null)
        .forEach(
            event ->
                entries.add(
                    new Entry(
                        event.eventId() + ":awaiting-publication",
                        phase(event.eventType()),
                        "pending",
                        "Awaiting outbox publication",
                        "awaiting",
                        false,
                        null,
                        event.source(),
                        null,
                        null,
                        null,
                        null,
                        null)));
  }

  private static Entry pendingEntry(String id, String phase, String label, OcrJob job) {
    return new Entry(
        id,
        phase,
        "pending",
        label,
        "awaiting",
        false,
        null,
        "media",
        null,
        null,
        new Retry(job.attemptCount(), job.maxAttempts(), job.nextAttemptAt()),
        null,
        new Resource("ocr_job", job.jobId()));
  }

  private static Entry unknownEntry(OcrJob job) {
    return new Entry(
        job.jobId() + ":unknown",
        "ocr",
        "unknown",
        "Some durable lifecycle evidence is unavailable",
        "unknown",
        false,
        null,
        "media",
        null,
        null,
        new Retry(job.attemptCount(), job.maxAttempts(), job.nextAttemptAt()),
        null,
        new Resource("ocr_job", job.jobId()));
  }

  private static boolean hasMissingCoreEvidence(
      OcrJob job, EventFact uploaded, List<EventFact> requested, List<EventFact> ocrEvents) {
    if (uploaded == null || requested.isEmpty()) return true;
    long started =
        ocrEvents.stream().filter(event -> event.eventType().equals("OcrStarted")).count();
    if (started < job.attemptCount()) return true;
    if (job.status() == OcrJobStatus.COMPLETED) {
      return ocrEvents.stream().noneMatch(event -> event.eventType().equals("OcrCompleted"));
    }
    if (job.status() == OcrJobStatus.FAILED) {
      return ocrEvents.stream().noneMatch(event -> event.eventType().equals("OcrFailed"));
    }
    return false;
  }

  private static boolean hasMissingConsumptionEvidence(
      OcrJob job,
      EventFact uploaded,
      List<EventFact> requested,
      List<EventFact> completed,
      List<EventFact> downstream) {
    if (uploaded != null && !hasConsumption(uploaded, FILE_UPLOADED_CONSUMER)) return true;
    if (job.status() == OcrJobStatus.COMPLETED || job.status() == OcrJobStatus.FAILED) {
      if (requested.stream().noneMatch(event -> hasConsumption(event, OCR_REQUESTED_CONSUMER))) {
        return true;
      }
    }
    if (job.status() == OcrJobStatus.COMPLETED
        && completed.stream().noneMatch(event -> hasConsumption(event, OCR_COMPLETED_CONSUMER))) {
      return true;
    }
    return downstream.stream()
        .filter(event -> event.eventType().equals("IndexingRequested"))
        .anyMatch(event -> !hasConsumption(event, INDEXING_REQUESTED_CONSUMER));
  }

  private static boolean hasConsumption(EventFact event, String consumer) {
    return event.consumptions().stream().anyMatch(record -> record.consumer().equals(consumer));
  }

  private static Workflow workflow(
      WorkflowExecutionRow execution, String stepKey, String actionType) {
    return new Workflow(
        execution.executionId(),
        execution.workflowId(),
        execution.workflowVersionId(),
        execution.workflowVersionNumber(),
        stepKey,
        actionType,
        execution.retryCount());
  }

  private static List<EventFact> filterEvents(
      List<EventFact> events, Predicate<EventFact> predicate) {
    return events.stream().filter(predicate).toList();
  }

  private static Set<String> eventIds(List<EventFact> events) {
    Set<String> ids = new LinkedHashSet<>();
    events.forEach(event -> ids.add(event.eventId()));
    return ids;
  }

  private static void addEvent(Map<String, EventFact> events, EventFact event) {
    if (event != null) events.putIfAbsent(event.eventId(), event);
  }

  private static int kindRank(Entry entry) {
    return switch (entry.kind()) {
      case "event" -> 0;
      case "job" -> 1;
      case "workflow_execution" -> 2;
      case "workflow_step" -> 3;
      case "audit" -> 4;
      case "pending" -> 5;
      default -> 6;
    };
  }

  private static String phase(String eventType) {
    if (eventType.equals("FileUploaded")) return "upload";
    if (eventType.startsWith("Ocr")) return "ocr";
    if (eventType.startsWith("Notification")) return "notification";
    return "search";
  }

  private static String label(String eventType) {
    return switch (eventType) {
      case "FileUploaded" -> "File uploaded";
      case "OcrRequested" -> "OCR queued";
      case "OcrStarted" -> "OCR processing started";
      case "OcrCompleted" -> "OCR completed";
      case "OcrFailed" -> "OCR failed";
      case "NotificationCreated" -> "Notification created";
      case "NotificationSent" -> "Notification sent";
      case "NotificationFailed" -> "Notification failed";
      case "IndexingRequested" -> "Search indexing requested";
      case "IndexingCompleted" -> "Search indexing completed";
      case "IndexingFailed" -> "Search indexing failed";
      default -> "Lifecycle event";
    };
  }

  private static String eventStatus(String eventType) {
    if (eventType.endsWith("Failed")) return "failed";
    if (eventType.equals("OcrStarted")) return "processing";
    if (eventType.equals("OcrRequested") || eventType.equals("IndexingRequested")) return "queued";
    return "completed";
  }

  private static String actionLabel(String actionType) {
    if (actionType == null || actionType.isBlank()) return "Workflow step";
    return switch (actionType) {
      case "extract_invoice_fields" -> "Extract invoice fields";
      case "create_notification" -> "Create notification";
      case "create_audit_entry" -> "Create audit entry";
      case "request_search_indexing" -> "Request search indexing";
      case "create_knowledge_item_placeholder" -> "Create knowledge item";
      default -> "Workflow step: " + actionType.replace('_', ' ');
    };
  }

  private record EventFact(
      String eventId,
      String eventType,
      int version,
      Instant occurredAt,
      String correlationId,
      String causationId,
      String source,
      Instant publishedAt,
      List<Consumption> consumptions) {}

  private static final class EventFactBuilder {

    private final String eventId;
    private final String eventType;
    private final int version;
    private final Instant occurredAt;
    private final String correlationId;
    private final String causationId;
    private final String source;
    private final Instant publishedAt;
    private final List<Consumption> consumptions = new ArrayList<>();

    private EventFactBuilder(
        String eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String correlationId,
        String causationId,
        String source,
        Instant publishedAt) {
      this.eventId = eventId;
      this.eventType = eventType;
      this.version = version;
      this.occurredAt = occurredAt;
      this.correlationId = correlationId;
      this.causationId = causationId;
      this.source = source;
      this.publishedAt = publishedAt;
    }

    private EventFact build() {
      return new EventFact(
          eventId,
          eventType,
          version,
          occurredAt,
          correlationId,
          causationId,
          source,
          publishedAt,
          List.copyOf(consumptions));
    }
  }
}
