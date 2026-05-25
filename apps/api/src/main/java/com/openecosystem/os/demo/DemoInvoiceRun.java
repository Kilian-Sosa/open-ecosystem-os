package com.openecosystem.os.demo;

import java.time.Instant;

public record DemoInvoiceRun(
    String runId,
    String workspaceId,
    String actorId,
    String correlationId,
    String fileId,
    Instant createdAt,
    Instant updatedAt) {}
