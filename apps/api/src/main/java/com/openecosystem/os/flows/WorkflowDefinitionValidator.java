package com.openecosystem.os.flows;

import com.fasterxml.jackson.databind.JsonNode;
import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class WorkflowDefinitionValidator {

  public static final String TRIGGER_TYPE_MANUAL = "manual";
  public static final String TRIGGER_TYPE_EVENT = "event";
  public static final String EVENT_TYPE_OCR_COMPLETED = "OcrCompleted";
  public static final String ACTION_CREATE_NOTIFICATION = "create_notification";
  public static final String ACTION_CREATE_AUDIT_ENTRY = "create_audit_entry";
  public static final String ACTION_CREATE_KNOWLEDGE_ITEM_PLACEHOLDER =
      "create_knowledge_item_placeholder";

  private static final Set<String> SUPPORTED_ACTIONS =
      Set.of(
          ACTION_CREATE_NOTIFICATION,
          ACTION_CREATE_AUDIT_ENTRY,
          ACTION_CREATE_KNOWLEDGE_ITEM_PLACEHOLDER);

  public WorkflowDefinition validate(JsonNode definition) {
    if (definition == null || !definition.isObject())
      throw validation("Workflow definition must be a JSON object", "definition");

    WorkflowTriggerDefinition trigger = validateTrigger(definition.get("trigger"));
    List<WorkflowStepDefinition> steps = validateSteps(definition.get("steps"));
    return new WorkflowDefinition(trigger, steps, definition.deepCopy());
  }

  private WorkflowTriggerDefinition validateTrigger(JsonNode triggerNode) {
    if (triggerNode == null || !triggerNode.isObject())
      throw validation("Workflow trigger must be a JSON object", "trigger");

    String type = requiredText(triggerNode, "type", "trigger.type");
    if (TRIGGER_TYPE_MANUAL.equals(type)) return new WorkflowTriggerDefinition(type, null);
    if (!TRIGGER_TYPE_EVENT.equals(type))
      throw validation("Workflow trigger type is not supported", "trigger.type");

    String eventType = requiredText(triggerNode, "eventType", "trigger.eventType");
    if (!EVENT_TYPE_OCR_COMPLETED.equals(eventType))
      throw validation("Only OcrCompleted event triggers are supported in the MVP", "eventType");
    return new WorkflowTriggerDefinition(type, eventType);
  }

  private List<WorkflowStepDefinition> validateSteps(JsonNode stepsNode) {
    if (stepsNode == null || !stepsNode.isArray() || stepsNode.isEmpty())
      throw validation("Workflow definition must include at least one step", "steps");

    Set<String> stepIds = new HashSet<>();
    List<WorkflowStepDefinition> steps = new ArrayList<>();
    for (JsonNode stepNode : stepsNode) {
      if (!stepNode.isObject()) throw validation("Workflow steps must be JSON objects", "steps");
      String stepId = requiredText(stepNode, "id", "steps.id");
      if (!stepIds.add(stepId)) throw validation("Workflow step IDs must be unique", "steps.id");
      String name = requiredText(stepNode, "name", "steps.name");
      JsonNode action = stepNode.get("action");
      if (action == null || !action.isObject())
        throw validation("Workflow step action must be a JSON object", "steps.action");
      String actionType = requiredText(action, "type", "steps.action.type");
      if (!SUPPORTED_ACTIONS.contains(actionType))
        throw validation("Workflow step action is not supported", "steps.action.type");
      steps.add(new WorkflowStepDefinition(stepId, name, actionType, action.deepCopy()));
    }
    return steps;
  }

  private String requiredText(JsonNode node, String fieldName, String detailKey) {
    JsonNode value = node.get(fieldName);
    if (value == null || !value.isTextual() || value.asText().isBlank())
      throw validation("Required workflow definition field is missing", detailKey);
    return value.asText().trim();
  }

  private ApiException validation(String message, String detailKey) {
    return new ApiException(
        HttpStatus.BAD_REQUEST,
        ApiErrorCode.VALIDATION_FAILED,
        message,
        Map.of("field", detailKey));
  }
}
