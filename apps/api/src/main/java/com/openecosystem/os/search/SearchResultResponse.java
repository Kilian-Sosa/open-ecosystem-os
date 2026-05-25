package com.openecosystem.os.search;

import java.time.Instant;
import java.util.Map;

public record SearchResultResponse(
    String id,
    String sourceType,
    String sourceId,
    String title,
    String summary,
    String resourceHref,
    String correlationId,
    String status,
    Map<String, Object> metadata,
    Instant createdAt) {}
