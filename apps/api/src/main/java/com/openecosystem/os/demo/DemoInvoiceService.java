package com.openecosystem.os.demo;

import com.openecosystem.os.audit.AuditRecord;
import com.openecosystem.os.audit.JdbcAuditRecordRepository;
import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.common.ids.Ids;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.common.security.CorrelationContext;
import com.openecosystem.os.drive.DriveFileResponse;
import com.openecosystem.os.drive.DriveUploadService;
import com.openecosystem.os.drive.storage.FileObjectStorage;
import com.openecosystem.os.flows.JdbcWorkflowExecutionRepository;
import com.openecosystem.os.flows.WorkflowExecution;
import com.openecosystem.os.media.OcrJob;
import com.openecosystem.os.media.OcrJobRepository;
import com.openecosystem.os.media.OcrJobStatus;
import com.openecosystem.os.notifications.JdbcNotificationRepository;
import com.openecosystem.os.notifications.NotificationRecord;
import com.openecosystem.os.search.JdbcSearchDocumentRepository;
import com.openecosystem.os.search.SearchDocument;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DemoInvoiceService {

  private static final String DEMO_WORKFLOW_AUDIT_ACTION = "flows.invoice_automation.completed";

  private final AuthenticationContext authenticationContext;
  private final DriveUploadService driveUploadService;
  private final JdbcDemoInvoiceRepository demoInvoiceRepository;
  private final OcrJobRepository ocrJobRepository;
  private final JdbcWorkflowExecutionRepository workflowExecutionRepository;
  private final JdbcNotificationRepository notificationRepository;
  private final JdbcAuditRecordRepository auditRecordRepository;
  private final JdbcSearchDocumentRepository searchDocumentRepository;
  private final FileObjectStorage objectStorage;
  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;

  public DemoInvoiceService(
      AuthenticationContext authenticationContext,
      DriveUploadService driveUploadService,
      JdbcDemoInvoiceRepository demoInvoiceRepository,
      OcrJobRepository ocrJobRepository,
      JdbcWorkflowExecutionRepository workflowExecutionRepository,
      JdbcNotificationRepository notificationRepository,
      JdbcAuditRecordRepository auditRecordRepository,
      JdbcSearchDocumentRepository searchDocumentRepository,
      FileObjectStorage objectStorage,
      JdbcTemplate jdbcTemplate,
      TransactionTemplate transactionTemplate) {
    this.authenticationContext = authenticationContext;
    this.driveUploadService = driveUploadService;
    this.demoInvoiceRepository = demoInvoiceRepository;
    this.ocrJobRepository = ocrJobRepository;
    this.workflowExecutionRepository = workflowExecutionRepository;
    this.notificationRepository = notificationRepository;
    this.auditRecordRepository = auditRecordRepository;
    this.searchDocumentRepository = searchDocumentRepository;
    this.objectStorage = objectStorage;
    this.jdbcTemplate = jdbcTemplate;
    this.transactionTemplate = transactionTemplate;
  }

  public DemoInvoiceRunResponse startRun() {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    String runId = Ids.newId("demo");
    String correlationId = CorrelationContext.currentOrCreate();
    DriveFileResponse file =
        driveUploadService.upload(
            new DemoMultipartFile("Fake_Test_Invoice_" + runId + ".pdf", fakeInvoicePdf(runId)));
    Instant now = Instant.now();
    demoInvoiceRepository.saveRun(
        new DemoInvoiceRun(
            runId,
            principal.workspaceId(),
            principal.actorId(),
            correlationId,
            file.fileId(),
            now,
            now));
    return getRun(runId);
  }

  public DemoInvoiceRunResponse getRun(String runId) {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    DemoInvoiceRun run =
        demoInvoiceRepository
            .findRunByIdForWorkspace(runId, principal.workspaceId())
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND,
                        ApiErrorCode.NOT_FOUND,
                        "Demo invoice run was not found"));
    return toResponse(run);
  }

  public DemoInvoiceResetResponse reset() {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    int runCount = demoInvoiceRepository.countRuns(principal.workspaceId());
    List<String> storageKeys = demoInvoiceRepository.listDemoStorageKeys(principal.workspaceId());

    transactionTemplate.executeWithoutResult(status -> deleteDemoRows(principal.workspaceId()));

    int objectsDeleted = 0;
    for (String storageKey : storageKeys) {
      objectStorage.deleteObjectIfExists(storageKey);
      objectsDeleted++;
    }
    return new DemoInvoiceResetResponse(runCount, objectsDeleted);
  }

  private DemoInvoiceRunResponse toResponse(DemoInvoiceRun run) {
    Optional<OcrJob> ocrJob =
        ocrJobRepository.findByFileIdForWorkspace(run.fileId(), run.workspaceId());
    Optional<WorkflowExecution> workflowExecution =
        workflowExecutionRepository.findLatestByCorrelationId(
            run.workspaceId(), run.correlationId());
    Optional<NotificationRecord> notification =
        notificationRepository.findLatestByCorrelationId(run.workspaceId(), run.correlationId());
    List<AuditRecord> auditRecords =
        auditRecordRepository.listByCorrelationId(run.workspaceId(), run.correlationId());
    Optional<SearchDocument> searchDocument =
        searchDocumentRepository.findLatestByCorrelationId(run.workspaceId(), run.correlationId());
    Optional<DemoInvoiceExtraction> extraction =
        demoInvoiceRepository.findExtractionByRunIdForWorkspace(run.runId(), run.workspaceId());

    String ocrJobId = ocrJob.map(OcrJob::jobId).orElse(null);
    String workflowExecutionId = workflowExecution.map(WorkflowExecution::executionId).orElse(null);
    String notificationId = notification.map(NotificationRecord::notificationId).orElse(null);
    String searchDocumentId = searchDocument.map(SearchDocument::searchDocumentId).orElse(null);
    DemoInvoiceLinksResponse links =
        links(
            run,
            ocrJobId,
            workflowExecutionId,
            extraction.map(DemoInvoiceExtraction::invoiceNumber).orElse(null));
    List<DemoTimelineStepResponse> timeline =
        timeline(run, ocrJob, workflowExecution, notification, auditRecords, searchDocument, links);

    return new DemoInvoiceRunResponse(
        run.runId(),
        run.correlationId(),
        run.fileId(),
        ocrJobId,
        workflowExecutionId,
        notificationId,
        searchDocumentId,
        status(ocrJob, workflowExecution, searchDocument),
        links,
        timeline,
        extraction.map(this::toExtractionResponse).orElse(null),
        run.createdAt(),
        run.updatedAt());
  }

  private DemoInvoiceLinksResponse links(
      DemoInvoiceRun run, String ocrJobId, String workflowExecutionId, String invoiceNumber) {
    String searchQuery = invoiceNumber == null ? "fake test invoice" : invoiceNumber;
    return new DemoInvoiceLinksResponse(
        "/app/drive?fileId=" + encode(run.fileId()),
        "/app/media?"
            + (ocrJobId == null ? "fileId=" + encode(run.fileId()) : "jobId=" + encode(ocrJobId)),
        "/app/flows?"
            + (workflowExecutionId == null
                ? "correlationId=" + encode(run.correlationId())
                : "executionId=" + encode(workflowExecutionId)),
        "/app/notifications?correlationId=" + encode(run.correlationId()),
        "/admin/audit?correlationId=" + encode(run.correlationId()),
        "/app/search?q=" + encode(searchQuery));
  }

  private List<DemoTimelineStepResponse> timeline(
      DemoInvoiceRun run,
      Optional<OcrJob> ocrJob,
      Optional<WorkflowExecution> workflowExecution,
      Optional<NotificationRecord> notification,
      List<AuditRecord> auditRecords,
      Optional<SearchDocument> searchDocument,
      DemoInvoiceLinksResponse links) {
    List<DemoTimelineStepResponse> steps = new ArrayList<>();
    steps.add(
        new DemoTimelineStepResponse(
            "drive",
            "Drive upload",
            "completed",
            "Fake/test invoice placeholder stored in Drive.",
            links.drive(),
            run.createdAt()));

    steps.add(ocrStep(ocrJob, links.ocr()));
    steps.add(workflowStep(workflowExecution, links.flows()));
    steps.add(notificationStep(notification, links.notifications()));
    steps.add(auditStep(auditRecords, links.audit()));
    steps.add(searchStep(searchDocument, links.search()));
    return steps;
  }

  private DemoTimelineStepResponse ocrStep(Optional<OcrJob> ocrJob, String href) {
    if (ocrJob.isEmpty())
      return new DemoTimelineStepResponse(
          "ocr", "Mock OCR", "pending", "Waiting for FileUploaded to queue OCR.", href, null);

    OcrJob job = ocrJob.get();
    String status =
        job.status() == OcrJobStatus.COMPLETED
            ? "completed"
            : job.status() == OcrJobStatus.FAILED ? "failed" : "processing";
    return new DemoTimelineStepResponse(
        "ocr",
        "Mock OCR",
        status,
        "OCR job " + job.jobId() + " is " + job.status().value() + ".",
        href,
        job.updatedAt());
  }

  private DemoTimelineStepResponse workflowStep(
      Optional<WorkflowExecution> workflowExecution, String href) {
    if (workflowExecution.isEmpty())
      return new DemoTimelineStepResponse(
          "flows",
          "Automation workflow",
          "pending",
          "Waiting for OcrCompleted to trigger Flows.",
          href,
          null);

    WorkflowExecution execution = workflowExecution.get();
    return new DemoTimelineStepResponse(
        "flows",
        "Automation workflow",
        execution.status().value(),
        "Workflow execution " + execution.executionId() + " is " + execution.status().value() + ".",
        href,
        execution.updatedAt());
  }

  private DemoTimelineStepResponse notificationStep(
      Optional<NotificationRecord> notification, String href) {
    if (notification.isEmpty())
      return new DemoTimelineStepResponse(
          "notification",
          "Notification",
          "pending",
          "Waiting for the workflow notification action.",
          href,
          null);

    return new DemoTimelineStepResponse(
        "notification",
        "Notification",
        "completed",
        "Notification " + notification.get().notificationId() + " created.",
        href,
        notification.get().createdAt());
  }

  private DemoTimelineStepResponse auditStep(List<AuditRecord> auditRecords, String href) {
    Optional<AuditRecord> workflowAudit =
        auditRecords.stream()
            .filter(record -> DEMO_WORKFLOW_AUDIT_ACTION.equals(record.action()))
            .findFirst();
    if (workflowAudit.isEmpty())
      return new DemoTimelineStepResponse(
          "audit",
          "Audit records",
          "pending",
          "Drive and OCR audit records may exist; waiting for workflow audit completion.",
          href,
          null);

    return new DemoTimelineStepResponse(
        "audit",
        "Audit records",
        "completed",
        "Workflow audit record " + workflowAudit.get().auditId() + " created.",
        href,
        workflowAudit.get().occurredAt());
  }

  private DemoTimelineStepResponse searchStep(
      Optional<SearchDocument> searchDocument, String href) {
    if (searchDocument.isEmpty())
      return new DemoTimelineStepResponse(
          "search",
          "Search indexing",
          "pending",
          "Waiting for extraction to request search indexing.",
          href,
          null);

    SearchDocument document = searchDocument.get();
    String status =
        document.status().value().equals("indexed")
            ? "completed"
            : document.status().value().equals("failed") ? "failed" : "processing";
    return new DemoTimelineStepResponse(
        "search",
        "Search indexing",
        status,
        "Search document " + document.searchDocumentId() + " is " + document.status().value() + ".",
        href,
        document.updatedAt());
  }

  private String status(
      Optional<OcrJob> ocrJob,
      Optional<WorkflowExecution> workflowExecution,
      Optional<SearchDocument> searchDocument) {

    boolean ocrFailed = ocrJob.map(job -> job.status() == OcrJobStatus.FAILED).orElse(false);
    boolean workflowFailed =
        workflowExecution
            .map(execution -> execution.status().value().equals("failed"))
            .orElse(false);
    boolean searchFailed =
        searchDocument.map(document -> document.status().value().equals("failed")).orElse(false);

    if (ocrFailed || workflowFailed || searchFailed) return "failed";
    if (searchDocument.map(document -> document.status().value().equals("indexed")).orElse(false))
      return "completed";
    return "processing";
  }

  private DemoInvoiceExtractionResponse toExtractionResponse(DemoInvoiceExtraction extraction) {
    return new DemoInvoiceExtractionResponse(
        extraction.extractionId(),
        extraction.invoiceNumber(),
        extraction.supplierName(),
        extraction.supplierTestNif(),
        extraction.supplierTestIban(),
        extraction.totalAmount(),
        extraction.currency(),
        extraction.dueDate(),
        extraction.testData());
  }

  private void deleteDemoRows(String workspaceId) {
    jdbcTemplate.update(
        """
        delete from event_consumptions
        where event_id in (
          select event_id
          from event_outbox
          where workspace_id = ?
            and correlation_id in (
              select correlation_id from demo_invoice_runs where workspace_id = ?
            )
        )
        """,
        workspaceId,
        workspaceId);
    jdbcTemplate.update(
        """
        delete from search_documents
        where workspace_id = ?
          and correlation_id in (
            select correlation_id from demo_invoice_runs where workspace_id = ?
          )
        """,
        workspaceId,
        workspaceId);
    jdbcTemplate.update(
        """
        delete from notifications
        where workspace_id = ?
          and correlation_id in (
            select correlation_id from demo_invoice_runs where workspace_id = ?
          )
        """,
        workspaceId,
        workspaceId);
    jdbcTemplate.update(
        """
        delete from audit_records
        where workspace_id = ?
          and correlation_id in (
            select correlation_id from demo_invoice_runs where workspace_id = ?
          )
        """,
        workspaceId,
        workspaceId);
    jdbcTemplate.update(
        """
        delete from knowledge_items
        where workspace_id = ?
          and source_workflow_execution_id in (
            select execution_id
            from workflow_executions
            where workspace_id = ?
              and correlation_id in (
                select correlation_id from demo_invoice_runs where workspace_id = ?
              )
          )
        """,
        workspaceId,
        workspaceId,
        workspaceId);
    jdbcTemplate.update("delete from demo_invoice_extractions where workspace_id = ?", workspaceId);
    jdbcTemplate.update(
        """
        delete from workflow_step_executions
        where workspace_id = ?
          and execution_id in (
            select execution_id
            from workflow_executions
            where workspace_id = ?
              and correlation_id in (
                select correlation_id from demo_invoice_runs where workspace_id = ?
              )
          )
        """,
        workspaceId,
        workspaceId,
        workspaceId);
    jdbcTemplate.update(
        """
        delete from workflow_executions
        where workspace_id = ?
          and correlation_id in (
            select correlation_id from demo_invoice_runs where workspace_id = ?
          )
        """,
        workspaceId,
        workspaceId);
    jdbcTemplate.update(
        """
        delete from ocr_jobs
        where workspace_id = ?
          and file_id in (select file_id from demo_invoice_runs where workspace_id = ?)
        """,
        workspaceId,
        workspaceId);
    jdbcTemplate.update(
        """
        delete from event_outbox
        where workspace_id = ?
          and correlation_id in (
            select correlation_id from demo_invoice_runs where workspace_id = ?
          )
        """,
        workspaceId,
        workspaceId);
    jdbcTemplate.update(
        """
        delete from drive_files
        where workspace_id = ?
          and file_id in (select file_id from demo_invoice_runs where workspace_id = ?)
        """,
        workspaceId,
        workspaceId);
    jdbcTemplate.update("delete from demo_invoice_runs where workspace_id = ?", workspaceId);
  }

  private byte[] fakeInvoicePdf(String runId) {
    String content =
        """
        %%PDF-1.7
        1 0 obj
        << /Type /Catalog /Pages 2 0 R >>
        endobj
        2 0 obj
        << /Type /Pages /Count 1 /Kids [3 0 R] >>
        endobj
        3 0 obj
        << /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] >>
        endobj
        Fake/test invoice placeholder for Open Ecosystem OS demo.
        Run: %s
        Invoice number: TEST-INV-2026-0001
        Supplier: Demo Supplies S.L. (fake/test data)
        Test NIF: B00000000 (test data)
        Test IBAN: ES00 0000 0000 0000 0000 0000 (test data)
        Total: 124.00 EUR
        Due date: 2026-06-15
        %%EOF
        """
            .formatted(runId);
    return content.getBytes(StandardCharsets.UTF_8);
  }

  private String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static class DemoMultipartFile implements MultipartFile {

    private final String filename;
    private final byte[] content;

    DemoMultipartFile(String filename, byte[] content) {
      this.filename = filename;
      this.content = content.clone();
    }

    @Override
    public String getName() {
      return "file";
    }

    @Override
    public String getOriginalFilename() {
      return filename;
    }

    @Override
    public String getContentType() {
      return "application/pdf";
    }

    @Override
    public boolean isEmpty() {
      return content.length == 0;
    }

    @Override
    public long getSize() {
      return content.length;
    }

    @Override
    public byte[] getBytes() {
      return content.clone();
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(content);
    }

    @Override
    public void transferTo(File dest) throws IOException {
      java.nio.file.Files.write(dest.toPath(), content);
    }
  }
}
