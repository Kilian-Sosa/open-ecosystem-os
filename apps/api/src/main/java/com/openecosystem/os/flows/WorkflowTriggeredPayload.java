package com.openecosystem.os.flows;

import java.time.Instant;

public record WorkflowTriggeredPayload(
    String workflowId,
    String workflowVersionId,
    String executionId,
    String triggerType,
    String sourceEventType,
    String sourceEventId,
    Instant triggeredAt) {}
