package com.openecosystem.os.flows;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record WorkflowVersion(
    String versionId,
    String workflowId,
    String workspaceId,
    int versionNumber,
    JsonNode definition,
    String createdBy,
    Instant createdAt,
    Instant publishedAt) {}
