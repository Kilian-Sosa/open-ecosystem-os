package com.openecosystem.os.worker.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.openecosystem.os.worker.common.observability.CorrelationContext;
import com.openecosystem.os.worker.metrics.WorkerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class OcrTemporaryWorkspaceCleanerTest {

  @TempDir Path temporaryRoot;

  @Test
  void retriesATransientWorkerOwnedCleanupFailure() throws Exception {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AtomicInteger attempts = new AtomicInteger();
    OcrTemporaryWorkspaceCleaner cleaner =
        new OcrTemporaryWorkspaceCleaner(
            new WorkerMetrics(registry),
            target -> {
              if (attempts.incrementAndGet() == 1) throw new java.io.IOException("locked");
            },
            duration -> {});

    Path directory = java.nio.file.Files.createDirectory(temporaryRoot.resolve("ocr-safe"));
    assertThat(cleaner.clean(directory, "source")).isTrue();
    assertThat(attempts).hasValue(2);
    assertThat(registry.find("openecosystem.worker.ocr.cleanup.failures").counter()).isNull();
  }

  @Test
  void emitsOnlyOneContentFreeFailureSignalAfterBoundedRetries() throws Exception {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    AtomicInteger attempts = new AtomicInteger();
    OcrTemporaryWorkspaceCleaner cleaner =
        new OcrTemporaryWorkspaceCleaner(
            new WorkerMetrics(registry),
            target -> {
              attempts.incrementAndGet();
              throw new java.io.IOException("private path must not escape");
            },
            duration -> {});

    Path directory = java.nio.file.Files.createDirectory(temporaryRoot.resolve("ocr-safe"));
    Logger logger = (Logger) LoggerFactory.getLogger(OcrTemporaryWorkspaceCleaner.class);
    ListAppender<ILoggingEvent> events = new ListAppender<>();
    events.start();
    logger.addAppender(events);
    CorrelationContext.set("corr_cleanup_test");
    try {
      assertThat(cleaner.clean(directory, "source")).isFalse();
      assertThat(attempts).hasValue(3);
      assertThat(registry.get("openecosystem.worker.ocr.cleanup.failures").counter().count())
          .isEqualTo(1);
      assertThat(events.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .containsExactly(
              "OCR temporary cleanup failed: code=OCR_TEMPORARY_CLEANUP_FAILED"
                  + " correlationId=corr_cleanup_test")
          .noneMatch(message -> message.contains(directory.toString()));
    } finally {
      CorrelationContext.clear();
      logger.detachAppender(events);
      events.stop();
    }
  }
}
