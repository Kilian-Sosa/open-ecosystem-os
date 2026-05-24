package com.openecosystem.os.flows;

import java.time.Instant;

public record WorkflowExecutionSummaryResponse(
    String executionId,
    String workflowId,
    String workflowName,
    int workflowVersionNumber,
    String triggerType,
    String sourceEventType,
    String sourceEventId,
    String status,
    int retryCount,
    String failureReason,
    String correlationId,
    Instant startedAt,
    Instant completedAt,
    Instant failedAt,
    Instant updatedAt) {}
