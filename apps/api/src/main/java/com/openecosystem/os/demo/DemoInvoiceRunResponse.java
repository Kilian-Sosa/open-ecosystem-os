package com.openecosystem.os.demo;

import java.time.Instant;
import java.util.List;

public record DemoInvoiceRunResponse(
    String runId,
    String correlationId,
    String fileId,
    String ocrJobId,
    String workflowExecutionId,
    String notificationId,
    String searchDocumentId,
    String status,
    DemoInvoiceLinksResponse links,
    List<DemoTimelineStepResponse> timeline,
    DemoInvoiceExtractionResponse extraction,
    Instant createdAt,
    Instant updatedAt) {}
