package com.openecosystem.os.worker.ocr;

import com.openecosystem.os.worker.common.observability.CorrelationContext;
import com.openecosystem.os.worker.metrics.WorkerMetrics;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public final class OcrTemporaryWorkspaceCleaner {

  private static final Logger LOGGER = LoggerFactory.getLogger(OcrTemporaryWorkspaceCleaner.class);
  private static final int MAX_ATTEMPTS = 3;
  private static final Duration RETRY_DELAY = Duration.ofMillis(50);
  private static final int MAX_STARTUP_DIRECTORIES = 100;
  private static final Duration STARTUP_MAX_AGE = Duration.ofHours(24);

  private final WorkerMetrics metrics;
  private final DeletionStrategy deletionStrategy;
  private final Sleeper sleeper;

  @Autowired
  public OcrTemporaryWorkspaceCleaner(WorkerMetrics metrics) {
    this(metrics, Files::deleteIfExists, Thread::sleep);
  }

  OcrTemporaryWorkspaceCleaner(
      WorkerMetrics metrics, DeletionStrategy deletionStrategy, Sleeper sleeper) {
    this.metrics = metrics;
    this.deletionStrategy = deletionStrategy;
    this.sleeper = sleeper;
  }

  public boolean clean(Path ownedDirectory, String stage) {
    if (ownedDirectory == null || !ownedDirectory.getFileName().toString().startsWith("ocr-")) {
      return true;
    }
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      try {
        deleteTree(ownedDirectory);
        return true;
      } catch (IOException exception) {
        if (attempt < MAX_ATTEMPTS) pause();
      }
    }
    metrics.recordOcrCleanupFailure(safeStage(stage));
    LOGGER.warn(
        "OCR temporary cleanup failed: code=OCR_TEMPORARY_CLEANUP_FAILED correlationId={}",
        CorrelationContext.currentOrCreate());
    return false;
  }

  @EventListener(ApplicationReadyEvent.class)
  void cleanAbandonedWorkerDirectories() {
    Path root = Path.of(System.getProperty("java.io.tmpdir"));
    Instant cutoff = Instant.now().minus(STARTUP_MAX_AGE);
    try (var entries = Files.list(root)) {
      entries
          .filter(Files::isDirectory)
          .filter(path -> path.getFileName().toString().startsWith("ocr-"))
          .limit(MAX_STARTUP_DIRECTORIES)
          .filter(path -> olderThan(path, cutoff))
          .forEach(path -> clean(path, "startup"));
    } catch (IOException ignored) {
      // Startup cleanup is best effort and never scans outside the configured worker temp root.
    }
  }

  private void deleteTree(Path directory) throws IOException {
    try (var paths = Files.walk(directory)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        deletionStrategy.delete(path);
      }
    }
  }

  private boolean olderThan(Path path, Instant cutoff) {
    try {
      return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
    } catch (IOException exception) {
      return false;
    }
  }

  private void pause() {
    try {
      sleeper.sleep(RETRY_DELAY);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }

  private String safeStage(String stage) {
    return switch (stage) {
      case "source", "rendered", "startup" -> stage;
      default -> "unknown";
    };
  }

  @FunctionalInterface
  interface DeletionStrategy {
    void delete(Path path) throws IOException;
  }

  @FunctionalInterface
  interface Sleeper {
    void sleep(Duration duration) throws InterruptedException;
  }
}
