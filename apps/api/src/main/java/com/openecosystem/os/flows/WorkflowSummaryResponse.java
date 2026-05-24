package com.openecosystem.os.flows;

import java.time.Instant;

public record WorkflowSummaryResponse(
    String workflowId,
    String name,
    String description,
    String status,
    int currentVersionNumber,
    String triggerType,
    String triggerEventType,
    int stepCount,
    Instant updatedAt) {}
