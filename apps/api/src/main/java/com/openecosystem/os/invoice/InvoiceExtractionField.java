package com.openecosystem.os.invoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceExtractionField(
    String fieldId,
    String extractionId,
    String ocrResultId,
    String workspaceId,
    String fieldKey,
    String displayValue,
    String normalizedValue,
    InvoiceFieldStatus status,
    BigDecimal confidence,
    int sourcePageNumber,
    int sourceBlockNumber,
    int sourceParagraphNumber,
    int sourceLineNumber,
    Instant createdAt,
    List<InvoiceExtractionFieldSource> sources) {

  public InvoiceExtractionField {
    sources = sources == null ? List.of() : List.copyOf(sources);
  }
}
