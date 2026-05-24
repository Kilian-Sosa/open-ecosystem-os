package com.openecosystem.os.flows;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record WorkflowDefinition(
    WorkflowTriggerDefinition trigger, List<WorkflowStepDefinition> steps, JsonNode json) {

  public WorkflowDefinition {
    steps = List.copyOf(steps);
  }
}
