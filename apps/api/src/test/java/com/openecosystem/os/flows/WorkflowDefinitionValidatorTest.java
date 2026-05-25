package com.openecosystem.os.flows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openecosystem.os.common.errors.ApiException;
import org.junit.jupiter.api.Test;

class WorkflowDefinitionValidatorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WorkflowDefinitionValidator validator = new WorkflowDefinitionValidator();

  @Test
  void acceptsOcrCompletedVerticalWorkflowDefinition() throws Exception {
    WorkflowDefinition definition =
        validator.validate(
            objectMapper.readTree(
                """
                {
                  "trigger": { "type": "event", "eventType": "OcrCompleted" },
                  "steps": [
                    {
                      "id": "notify",
                      "name": "Notify",
                      "action": {
                        "type": "create_notification",
                        "title": "OCR completed"
                      }
                    }
                  ]
                }
                """));

    assertThat(definition.trigger().eventType()).isEqualTo("OcrCompleted");
    assertThat(definition.steps()).hasSize(1);
    assertThat(definition.steps().getFirst().actionType()).isEqualTo("create_notification");
  }

  @Test
  void rejectsUnknownTriggerActionAndDuplicateStepIds() throws Exception {
    assertThatThrownBy(
            () ->
                validator.validate(
                    objectMapper.readTree(
                        """
                        {
                          "trigger": { "type": "event", "eventType": "FileUploaded" },
                          "steps": [
                            {
                              "id": "notify",
                              "name": "Notify",
                              "action": { "type": "create_notification", "title": "Done" }
                            }
                          ]
                        }
                        """)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Only OcrCompleted");

    assertThatThrownBy(
            () ->
                validator.validate(
                    objectMapper.readTree(
                        """
                        {
                          "trigger": { "type": "manual" },
                          "steps": [
                            {
                              "id": "unknown",
                              "name": "Unknown",
                              "action": { "type": "send_email" }
                            }
                          ]
                        }
                        """)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("not supported");

    assertThatThrownBy(
            () ->
                validator.validate(
                    objectMapper.readTree(
                        """
                        {
                          "trigger": { "type": "manual" },
                          "steps": [
                            {
                              "id": "dup",
                              "name": "First",
                              "action": { "type": "create_audit_entry" }
                            },
                            {
                              "id": "dup",
                              "name": "Second",
                              "action": { "type": "create_audit_entry" }
                            }
                          ]
                        }
                        """)))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("unique");
  }
}
