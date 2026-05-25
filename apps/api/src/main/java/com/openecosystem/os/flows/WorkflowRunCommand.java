package com.openecosystem.os.flows;

public record WorkflowRunCommand(
    WorkflowWithVersion workflowWithVersion,
    WorkflowTriggerType triggerType,
    String actorId,
    String correlationId,
    String sourceEventId,
    String sourceEventType,
    String triggerIdempotencyKey,
    OcrCompletedEvent ocrCompletedEvent) {}
