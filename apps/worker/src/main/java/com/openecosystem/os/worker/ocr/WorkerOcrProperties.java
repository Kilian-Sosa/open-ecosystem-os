package com.openecosystem.os.worker.ocr;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties("openecosystem.media.ocr")
public record WorkerOcrProperties(
    String provider, int maxAttempts, @DurationUnit(ChronoUnit.SECONDS) Duration retryDelay) {

  public WorkerOcrProperties {
    provider = provider == null || provider.isBlank() ? "mock" : provider;
    maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
    retryDelay =
        retryDelay == null || retryDelay.isNegative() ? Duration.ofSeconds(30) : retryDelay;
  }
}
