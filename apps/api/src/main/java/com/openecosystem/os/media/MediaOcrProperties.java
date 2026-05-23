package com.openecosystem.os.media;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties("openecosystem.media.ocr")
public record MediaOcrProperties(
    int maxAttempts, @DurationUnit(ChronoUnit.SECONDS) Duration retryDelay) {

  public MediaOcrProperties {
    maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
    retryDelay =
        retryDelay == null || retryDelay.isNegative() ? Duration.ofSeconds(30) : retryDelay;
  }
}
