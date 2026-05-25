package com.openecosystem.os.search;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record SearchDocument(
    String searchDocumentId,
    String workspaceId,
    String sourceType,
    String sourceId,
    String title,
    String summary,
    String content,
    String resourceHref,
    String correlationId,
    SearchDocumentStatus status,
    int attemptCount,
    int maxAttempts,
    String failureCode,
    String failureMessage,
    JsonNode metadata,
    Instant createdAt,
    Instant updatedAt,
    Instant indexedAt,
    Instant failedAt) {}
