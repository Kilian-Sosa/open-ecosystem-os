package com.openecosystem.os.demo;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DemoInvoiceExtraction(
    String extractionId,
    String runId,
    String workspaceId,
    String actorId,
    String fileId,
    String ocrJobId,
    String workflowExecutionId,
    String invoiceNumber,
    String supplierName,
    String supplierTestNif,
    String supplierTestIban,
    BigDecimal totalAmount,
    String currency,
    LocalDate dueDate,
    boolean testData,
    JsonNode metadata,
    Instant createdAt) {}
