package com.openecosystem.os.media;

import java.util.Locale;

public enum OcrJobStatus {
  QUEUED("queued"),
  PROCESSING("processing"),
  COMPLETED("completed"),
  FAILED("failed");

  private final String value;

  OcrJobStatus(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static OcrJobStatus fromValue(String value) {
    return OcrJobStatus.valueOf(value.toUpperCase(Locale.ROOT));
  }
}
