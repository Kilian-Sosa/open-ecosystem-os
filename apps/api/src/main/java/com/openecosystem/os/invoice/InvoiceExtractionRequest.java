package com.openecosystem.os.invoice;

public record InvoiceExtractionRequest(
    String workspaceId,
    String ocrResultId,
    String ocrJobId,
    String fileId,
    String workflowExecutionId,
    String actorId,
    String correlationId,
    String causationId) {}
