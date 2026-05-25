package com.openecosystem.os.flows;

import java.time.Instant;

public record WorkflowStepFailedPayload(
    String workflowId,
    String executionId,
    String stepKey,
    String actionType,
    String failureReason,
    Instant failedAt) {}
