package com.openecosystem.os.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceExtraction(
    String extractionId,
    String workflowExecutionId,
    String ocrResultId,
    String jobId,
    String fileId,
    String workspaceId,
    String extractorName,
    String extractorVersion,
    InvoiceExtractionStatus status,
    List<InvoiceExtractionWarning> warnings,
    BigDecimal aggregateConfidence,
    List<InvoiceExtractionField> fields,
    Instant createdAt,
    Instant updatedAt) {

  public InvoiceExtraction {
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
    fields = fields == null ? List.of() : List.copyOf(fields);
  }
}
