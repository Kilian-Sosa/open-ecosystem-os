package com.openecosystem.os.invoice;

public record InvoiceExtractionFieldSource(
    String fieldId,
    String ocrResultId,
    String workspaceId,
    String ocrWordId,
    String sourceRole,
    int sourceOrder) {}
