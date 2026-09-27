package com.openecosystem.os.worker.ocr;

import org.springframework.stereotype.Component;

@Component
public class OcrProviderConfigurationValidator {
  public OcrProviderConfigurationValidator(WorkerOcrProperties properties) {
    if (!"tesseract".equals(properties.provider())) {
      throw new IllegalStateException("Only the tesseract OCR provider is supported");
    }
  }
}
