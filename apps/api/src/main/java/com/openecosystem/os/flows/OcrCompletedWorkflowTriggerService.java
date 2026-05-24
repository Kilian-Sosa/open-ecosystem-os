package com.openecosystem.os.flows;

import com.openecosystem.os.common.events.EventConsumptionRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class OcrCompletedWorkflowTriggerService {

  private static final String CONSUMER_NAME = "flows.ocr-completed.workflow-trigger";
  private static final String OCR_COMPLETED = "OcrCompleted";

  private final WorkflowDefinitionValidator definitionValidator;
  private final JdbcWorkflowRepository workflowRepository;
  private final WorkflowRunner workflowRunner;
  private final EventConsumptionRepository eventConsumptionRepository;

  public OcrCompletedWorkflowTriggerService(
      WorkflowDefinitionValidator definitionValidator,
      JdbcWorkflowRepository workflowRepository,
      WorkflowRunner workflowRunner,
      EventConsumptionRepository eventConsumptionRepository) {
    this.definitionValidator = definitionValidator;
    this.workflowRepository = workflowRepository;
    this.workflowRunner = workflowRunner;
    this.eventConsumptionRepository = eventConsumptionRepository;
  }

  public void trigger(OcrCompletedEvent event) {
    if (eventConsumptionRepository.exists(CONSUMER_NAME, event.idempotencyKey())) {
      return;
    }

    if (event.version() == 1) {
      workflowRepository.listActiveByWorkspace(event.workspaceId()).stream()
          .filter(workflowWithVersion -> matchesOcrCompletedTrigger(workflowWithVersion.version()))
          .forEach(workflowWithVersion -> runWorkflow(workflowWithVersion, event));
    }

    eventConsumptionRepository.save(
        CONSUMER_NAME, event.idempotencyKey(), event.eventId(), Instant.now());
  }

  private boolean matchesOcrCompletedTrigger(WorkflowVersion version) {
    WorkflowDefinition definition = definitionValidator.validate(version.definition());
    return WorkflowDefinitionValidator.TRIGGER_TYPE_EVENT.equals(definition.trigger().type())
        && OCR_COMPLETED.equals(definition.trigger().eventType());
  }

  private void runWorkflow(WorkflowWithVersion workflowWithVersion, OcrCompletedEvent event) {
    workflowRunner.run(
        new WorkflowRunCommand(
            workflowWithVersion,
            WorkflowTriggerType.EVENT,
            event.actorId(),
            event.correlationId(),
            event.eventId(),
            OCR_COMPLETED,
            "flows:"
                + workflowWithVersion.workflow().workflowId()
                + ":event:"
                + event.eventId()
                + ":v1",
            event));
  }
}
