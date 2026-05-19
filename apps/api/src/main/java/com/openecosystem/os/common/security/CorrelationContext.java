package com.openecosystem.os.common.security;

public final class CorrelationContext {

  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  private CorrelationContext() {}

  public static void set(String correlationId) {
    CURRENT.set(CorrelationIds.normalize(correlationId));
  }

  public static String current() {
    return CURRENT.get();
  }

  public static String currentOrCreate() {
    String current = CURRENT.get();
    if (current == null || current.isBlank()) {
      current = CorrelationIds.newCorrelationId();
      CURRENT.set(current);
    }
    return current;
  }

  public static void clear() {
    CURRENT.remove();
  }
}
