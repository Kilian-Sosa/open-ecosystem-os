package com.openecosystem.os.flows;

import java.time.Instant;

public record WorkflowExecutionStartedPayload(
    String workflowId, String executionId, int workflowVersionNumber, Instant startedAt) {}
