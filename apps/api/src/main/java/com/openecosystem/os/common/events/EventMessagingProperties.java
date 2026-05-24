package com.openecosystem.os.common.events;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("openecosystem.events")
public record EventMessagingProperties(
    String exchange,
    String retryExchange,
    String deadLetterExchange,
    Outbox outbox,
    Queues queues) {

  public EventMessagingProperties {
    exchange = defaultText(exchange, "openecosystem.events");
    retryExchange = defaultText(retryExchange, "openecosystem.events.retry");
    deadLetterExchange = defaultText(deadLetterExchange, "openecosystem.events.dlx");
    outbox = outbox == null ? new Outbox(true, 1000, 50) : outbox;
    queues =
        queues == null
            ? new Queues(null, null, null, null, null, null, null, null, null, null)
            : queues;
  }

  private static String defaultText(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  public record Outbox(boolean enabled, long pollIntervalMs, int batchSize) {

    public Outbox {
      pollIntervalMs = pollIntervalMs <= 0 ? 1000 : pollIntervalMs;
      batchSize = batchSize <= 0 ? 50 : batchSize;
    }
  }

  public record Queues(
      String mediaFileUploaded,
      String mediaFileUploadedRetry,
      String mediaFileUploadedDlq,
      String ocrRequested,
      String ocrRequestedRetry,
      String ocrRequestedDlq,
      String flowsOcrCompleted,
      String flowsOcrCompletedRetry,
      String flowsOcrCompletedDlq,
      Duration retryDelay) {

    public Queues {
      mediaFileUploaded = defaultText(mediaFileUploaded, "openecosystem.media.file-uploaded");
      mediaFileUploadedRetry =
          defaultText(mediaFileUploadedRetry, "openecosystem.media.file-uploaded.retry");
      mediaFileUploadedDlq =
          defaultText(mediaFileUploadedDlq, "openecosystem.media.file-uploaded.dlq");
      ocrRequested = defaultText(ocrRequested, "openecosystem.ocr.requested");
      ocrRequestedRetry = defaultText(ocrRequestedRetry, "openecosystem.ocr.requested.retry");
      ocrRequestedDlq = defaultText(ocrRequestedDlq, "openecosystem.ocr.requested.dlq");
      flowsOcrCompleted = defaultText(flowsOcrCompleted, "openecosystem.flows.ocr-completed");
      flowsOcrCompletedRetry =
          defaultText(flowsOcrCompletedRetry, "openecosystem.flows.ocr-completed.retry");
      flowsOcrCompletedDlq =
          defaultText(flowsOcrCompletedDlq, "openecosystem.flows.ocr-completed.dlq");
      retryDelay =
          retryDelay == null || retryDelay.isNegative() ? Duration.ofSeconds(30) : retryDelay;
    }
  }
}
