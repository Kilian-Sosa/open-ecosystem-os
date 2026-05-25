package com.openecosystem.os.flows;

import java.time.Instant;

public record WorkflowExecution(
    String executionId,
    String workflowId,
    String workflowVersionId,
    int workflowVersionNumber,
    String workspaceId,
    String actorId,
    String correlationId,
    WorkflowTriggerType triggerType,
    String sourceEventId,
    String sourceEventType,
    String triggerIdempotencyKey,
    WorkflowExecutionStatus status,
    int retryCount,
    String failureReason,
    Instant startedAt,
    Instant completedAt,
    Instant failedAt,
    Instant createdAt,
    Instant updatedAt) {}
