package com.openecosystem.os.worker.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class WorkerOcrPropertiesTest {

  @Test
  void defaultsTheStaleClaimFenceBeyondTheWholeDocumentBudgetAndCleanupMargin() {
    WorkerOcrProperties properties =
        new WorkerOcrProperties(
            null, 0, null, null, null, 0, null, 0, 0, 0, 0, 0, null, null, null, null, null, 0, 0,
            null, 0);

    assertThat(properties.documentTimeout()).isEqualTo(Duration.ofMinutes(10));
    assertThat(properties.persistenceCleanupMargin()).isEqualTo(Duration.ofMinutes(2));
    assertThat(properties.staleProcessingTimeout()).isEqualTo(Duration.ofMinutes(15));
    assertThat(properties.staleProcessingTimeout())
        .isGreaterThan(properties.documentTimeout().plus(properties.persistenceCleanupMargin()));
  }

  @Test
  void rejectsAStaleTimeoutEqualToTheDocumentBudgetAndCleanupMargin() {
    WorkerOcrProperties properties =
        new WorkerOcrProperties(
            "tesseract",
            3,
            Duration.ofSeconds(30),
            "tesseract",
            "eng",
            1,
            Duration.ofSeconds(60),
            50,
            200,
            25L * 1024 * 1024,
            20_000_000,
            10 * 1024 * 1024,
            Duration.ofMinutes(12),
            Duration.ofMinutes(10),
            Duration.ofMinutes(2),
            "java",
            "/app/pdf-helper.jar",
            10_000,
            100_000,
            Duration.ofSeconds(5),
            4 * 1024);

    assertThatThrownBy(() -> new OcrExecutionBudgetValidator(properties).validate())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "OCR stale processing timeout must exceed the document budget and cleanup margin");
  }
}
