package com.openecosystem.os.worker.ocr;

import java.math.BigDecimal;

public record OcrWord(
    int readingOrder,
    int pageNumber,
    int pageWordOrder,
    int blockNumber,
    int paragraphNumber,
    int lineNumber,
    int wordNumber,
    String text,
    BigDecimal confidence,
    OcrBoundingBox boundingBox) {

  public OcrWord {
    if (readingOrder <= 0 || pageNumber <= 0 || pageWordOrder <= 0) {
      throw new IllegalArgumentException("OCR word order and page values must be positive");
    }
    if (blockNumber < 0 || paragraphNumber < 0 || lineNumber < 0 || wordNumber < 0) {
      throw new IllegalArgumentException("OCR word identifiers must not be negative");
    }
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException("OCR word text must not be blank");
    }
    if (confidence != null
        && (confidence.compareTo(BigDecimal.ZERO) < 0
            || confidence.compareTo(BigDecimal.valueOf(100)) > 0)) {
      throw new IllegalArgumentException("OCR confidence must be between zero and one hundred");
    }
  }
}
