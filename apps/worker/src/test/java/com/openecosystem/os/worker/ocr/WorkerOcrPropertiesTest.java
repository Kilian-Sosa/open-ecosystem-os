package com.openecosystem.os.worker.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class WorkerOcrPropertiesTest {

  @Test
  void defaultsTheStaleClaimFenceBeyondTheMaximumDocumentProcessingWindow() {
    WorkerOcrProperties properties =
        new WorkerOcrProperties(null, 0, null, null, null, 0, null, 0, 0, 0, 0, 0, null);

    assertThat(properties.staleProcessingTimeout())
        .isGreaterThanOrEqualTo(properties.pageTimeout().multipliedBy(properties.maxPages()));
    assertThat(properties.staleProcessingTimeout()).isEqualTo(Duration.ofHours(1));
  }
}
