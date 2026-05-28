package com.openecosystem.os.worker.metrics;

import com.openecosystem.os.worker.ocr.OcrProcessingOutcome;
import com.openecosystem.os.worker.search.SearchIndexingOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class WorkerMetrics {

  private static final String OUTCOME_TAG = "outcome";

  private final MeterRegistry meterRegistry;

  public WorkerMetrics(MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
  }

  public void recordOcrJob(OcrProcessingOutcome outcome, Duration duration) {
    record(
        "openecosystem.worker.ocr.jobs",
        "openecosystem.worker.ocr.job.duration",
        outcome,
        duration);
  }

  public void recordOcrJobError(Duration duration) {
    record(
        "openecosystem.worker.ocr.jobs",
        "openecosystem.worker.ocr.job.duration",
        "error",
        duration);
  }

  public void recordSearchIndexingJob(SearchIndexingOutcome outcome, Duration duration) {
    record(
        "openecosystem.worker.search.indexing.jobs",
        "openecosystem.worker.search.indexing.job.duration",
        outcome,
        duration);
  }

  public void recordSearchIndexingJobError(Duration duration) {
    record(
        "openecosystem.worker.search.indexing.jobs",
        "openecosystem.worker.search.indexing.job.duration",
        "error",
        duration);
  }

  private void record(String counterName, String timerName, Enum<?> outcome, Duration duration) {
    record(counterName, timerName, metricOutcome(outcome), duration);
  }

  private void record(String counterName, String timerName, String outcome, Duration duration) {
    meterRegistry.counter(counterName, OUTCOME_TAG, outcome).increment();
    meterRegistry.timer(timerName, OUTCOME_TAG, outcome).record(duration);
  }

  private String metricOutcome(Enum<?> outcome) {
    return outcome.name().toLowerCase(Locale.ROOT).replace('_', '-');
  }
}
