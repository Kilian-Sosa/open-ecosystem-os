package com.openecosystem.os.worker.ocr;

import java.util.List;

public record OcrPageResult(
    int pageNumber, OcrSourceKind sourceKind, String pageText, List<OcrWord> words) {

  public OcrPageResult {
    if (pageNumber <= 0) {
      throw new IllegalArgumentException("OCR page number must be positive");
    }
    if (sourceKind == null) {
      throw new IllegalArgumentException("OCR source kind is required");
    }
    if (pageText == null) {
      throw new IllegalArgumentException("OCR page text is required");
    }
    words = words == null ? List.of() : List.copyOf(words);
    if (words.stream().anyMatch(word -> word.pageNumber() != pageNumber)) {
      throw new IllegalArgumentException("OCR word page must match the page result");
    }
  }
}
