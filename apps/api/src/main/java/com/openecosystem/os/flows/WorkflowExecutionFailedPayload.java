package com.openecosystem.os.flows;

import java.time.Instant;

public record WorkflowExecutionFailedPayload(
    String workflowId, String executionId, String failureReason, Instant failedAt) {}
