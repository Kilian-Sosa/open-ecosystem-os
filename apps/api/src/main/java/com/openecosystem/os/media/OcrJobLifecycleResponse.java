package com.openecosystem.os.media;

import java.time.Instant;
import java.util.List;

public record OcrJobLifecycleResponse(String state, String outcome, List<Entry> entries) {

  public record Entry(
      String entryId,
      String phase,
      String kind,
      String label,
      String status,
      boolean observed,
      Instant occurredAt,
      String source,
      Event event,
      Workflow workflow,
      Retry retry,
      Failure failure,
      Resource resource) {}

  public record Event(
      String eventId,
      String eventType,
      int eventVersion,
      String correlationId,
      String causationId,
      String publicationState,
      Instant publishedAt,
      List<Consumption> consumptions) {}

  public record Consumption(String consumer, String state, Instant consumedAt) {}

  public record Workflow(
      String executionId,
      String workflowId,
      String workflowVersionId,
      int workflowVersionNumber,
      String stepKey,
      String actionType,
      int retryCount) {}

  public record Retry(int attemptCount, int maxAttempts, Instant nextAttemptAt) {}

  public record Failure(String code, String reason) {}

  public record Resource(String resourceType, String resourceId) {}
}
