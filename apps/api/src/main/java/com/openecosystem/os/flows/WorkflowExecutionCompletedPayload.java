package com.openecosystem.os.flows;

import java.time.Instant;

public record WorkflowExecutionCompletedPayload(
    String workflowId, String executionId, int completedStepCount, Instant completedAt) {}
