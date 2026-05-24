package com.openecosystem.os.flows;

import java.time.Instant;

public record WorkflowDetailResponse(
    String workflowId,
    String name,
    String description,
    String status,
    String currentVersionId,
    int currentVersionNumber,
    Object definition,
    Instant createdAt,
    Instant updatedAt) {}
