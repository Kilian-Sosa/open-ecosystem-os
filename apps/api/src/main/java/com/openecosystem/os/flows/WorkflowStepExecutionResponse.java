package com.openecosystem.os.flows;

import java.time.Instant;

public record WorkflowStepExecutionResponse(
    String stepExecutionId,
    String stepKey,
    String stepName,
    String actionType,
    String status,
    int retryCount,
    String failureReason,
    Object input,
    Object output,
    Instant startedAt,
    Instant completedAt,
    Instant failedAt,
    Instant updatedAt) {}
