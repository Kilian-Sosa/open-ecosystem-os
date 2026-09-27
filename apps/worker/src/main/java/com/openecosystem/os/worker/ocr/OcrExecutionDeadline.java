package com.openecosystem.os.worker.ocr;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public final class OcrExecutionDeadline {

  private final Clock clock;
  private final Instant deadline;

  private OcrExecutionDeadline(Clock clock, Instant deadline) {
    this.clock = clock;
    this.deadline = deadline;
  }

  public static OcrExecutionDeadline start(
      Clock clock, Instant claimStartedAt, Duration documentTimeout) {
    if (clock == null
        || claimStartedAt == null
        || documentTimeout == null
        || documentTimeout.isZero()
        || documentTimeout.isNegative()) {
      throw new IllegalArgumentException("OCR document deadline must be positive");
    }
    return new OcrExecutionDeadline(clock, claimStartedAt.plus(documentTimeout));
  }

  public OcrExecutionDeadline child(Duration pageTimeout) {
    if (pageTimeout == null || pageTimeout.isZero() || pageTimeout.isNegative()) {
      throw new IllegalArgumentException("OCR page deadline must be positive");
    }
    Instant pageDeadline = clock.instant().plus(pageTimeout);
    return new OcrExecutionDeadline(
        clock, pageDeadline.isBefore(deadline) ? pageDeadline : deadline);
  }

  public Duration remaining() {
    Duration remaining = Duration.between(clock.instant(), deadline);
    if (remaining.isZero() || remaining.isNegative()) {
      throw new OcrProviderException("OCR_DOCUMENT_TIMEOUT", "OCR document deadline was exceeded");
    }
    return remaining;
  }
}
