package com.openecosystem.os.flows;

import java.time.Instant;

public record Workflow(
    String workflowId,
    String workspaceId,
    String name,
    String description,
    WorkflowStatus status,
    String currentVersionId,
    int currentVersionNumber,
    String createdBy,
    String updatedBy,
    Instant createdAt,
    Instant updatedAt) {}
