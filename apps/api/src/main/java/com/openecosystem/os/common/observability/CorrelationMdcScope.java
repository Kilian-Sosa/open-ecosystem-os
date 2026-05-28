package com.openecosystem.os.common.observability;

import com.openecosystem.os.common.security.CorrelationContext;
import com.openecosystem.os.common.security.CorrelationIds;
import org.slf4j.MDC;

public final class CorrelationMdcScope implements AutoCloseable {

  private static final String MDC_KEY = "correlationId";

  private final String previousCorrelationId;
  private final boolean hadPreviousCorrelationId;
  private final String previousContextCorrelationId;

  private CorrelationMdcScope(String correlationId) {
    previousCorrelationId = MDC.get(MDC_KEY);
    hadPreviousCorrelationId = previousCorrelationId != null;
    previousContextCorrelationId = CorrelationContext.current();

    String normalizedCorrelationId = CorrelationIds.normalize(correlationId);
    MDC.put(MDC_KEY, normalizedCorrelationId);
    CorrelationContext.set(normalizedCorrelationId);
  }

  public static CorrelationMdcScope open(String correlationId) {
    return new CorrelationMdcScope(correlationId);
  }

  @Override
  public void close() {
    if (hadPreviousCorrelationId) MDC.put(MDC_KEY, previousCorrelationId);
    else MDC.remove(MDC_KEY);

    if (previousContextCorrelationId == null) CorrelationContext.clear();
    else CorrelationContext.set(previousContextCorrelationId);
  }
}
