package com.openecosystem.os.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record KnowledgeItem(
    String knowledgeItemId,
    String workspaceId,
    String title,
    String summary,
    String sourceFileId,
    String sourceOcrJobId,
    String sourceWorkflowExecutionId,
    String sourceEventId,
    JsonNode metadata,
    String createdBy,
    String correlationId,
    String idempotencyKey,
    Instant createdAt,
    Instant updatedAt) {}
