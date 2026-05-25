package com.openecosystem.os.flows;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record WorkflowStepExecution(
    String stepExecutionId,
    String executionId,
    String workflowId,
    String workspaceId,
    String stepKey,
    String stepName,
    String actionType,
    WorkflowStepExecutionStatus status,
    int retryCount,
    String failureReason,
    JsonNode input,
    JsonNode output,
    Instant startedAt,
    Instant completedAt,
    Instant failedAt,
    Instant createdAt,
    Instant updatedAt) {}
