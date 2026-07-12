package com.openecosystem.os.media;

import java.time.Instant;
import java.util.List;

public record OcrPageResult(
    String ocrPageId,
    String ocrResultId,
    String workspaceId,
    int pageNumber,
    String sourceKind,
    String pageText,
    int wordCount,
    Instant createdAt,
    List<OcrWord> words) {

  public OcrPageResult {
    words = words == null ? List.of() : List.copyOf(words);
  }
}
