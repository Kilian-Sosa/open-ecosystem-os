package com.openecosystem.os.worker.ocr;

public record OcrProviderResult(String extractedText) {

  public OcrProviderResult {
    if (extractedText == null || extractedText.isBlank()) {
      throw new IllegalArgumentException("extractedText must not be blank");
    }
  }
}
