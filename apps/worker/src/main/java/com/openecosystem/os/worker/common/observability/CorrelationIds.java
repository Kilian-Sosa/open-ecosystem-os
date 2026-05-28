package com.openecosystem.os.worker.common.observability;

import java.util.UUID;

public final class CorrelationIds {

  public static final String HEADER_NAME = "X-Correlation-Id";

  private CorrelationIds() {}

  public static String normalize(String correlationId) {
    if (correlationId == null || correlationId.isBlank()) return newCorrelationId();
    return correlationId.trim();
  }

  public static String newCorrelationId() {
    return "corr_" + UUID.randomUUID();
  }
}
