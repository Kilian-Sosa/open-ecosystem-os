package com.openecosystem.os.worker.ocr;

import org.springframework.stereotype.Component;

@Component
public final class OcrExecutionBudgetValidator {

  private final WorkerOcrProperties properties;

  public OcrExecutionBudgetValidator(WorkerOcrProperties properties) {
    this.properties = properties;
    validate();
  }

  public void validate() {
    if (properties.documentTimeout().isZero()
        || properties.documentTimeout().isNegative()
        || properties.pageTimeout().isZero()
        || properties.pageTimeout().isNegative()
        || properties.pageTimeout().compareTo(properties.documentTimeout()) > 0
        || properties.persistenceCleanupMargin().isZero()
        || properties.persistenceCleanupMargin().isNegative()) {
      throw new IllegalStateException("OCR execution budgets must be positive and page-bounded");
    }
    if (properties
            .staleProcessingTimeout()
            .compareTo(properties.documentTimeout().plus(properties.persistenceCleanupMargin()))
        <= 0) {
      throw new IllegalStateException(
          "OCR stale processing timeout must exceed the document budget and cleanup margin");
    }
  }
}
