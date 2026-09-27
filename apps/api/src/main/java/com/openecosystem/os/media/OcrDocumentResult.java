package com.openecosystem.os.media;

import java.time.Instant;
import java.util.List;

public record OcrDocumentResult(
    String ocrResultId,
    String jobId,
    String fileId,
    String workspaceId,
    String provider,
    String providerVersion,
    String documentText,
    int pageCount,
    int wordCount,
    Instant createdAt,
    Instant updatedAt,
    List<OcrPageResult> pages) {

  public OcrDocumentResult {
    pages = pages == null ? List.of() : List.copyOf(pages);
  }
}
