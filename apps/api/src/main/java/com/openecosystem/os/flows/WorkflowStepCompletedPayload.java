package com.openecosystem.os.flows;

import java.time.Instant;

public record WorkflowStepCompletedPayload(
    String workflowId,
    String executionId,
    String stepKey,
    String actionType,
    Instant completedAt) {}
