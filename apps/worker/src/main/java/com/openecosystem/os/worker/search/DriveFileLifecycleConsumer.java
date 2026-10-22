package com.openecosystem.os.worker.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class DriveFileLifecycleConsumer {
  private final ObjectMapper mapper;
  private final DriveSearchLifecycleProcessor processor;

  public DriveFileLifecycleConsumer(ObjectMapper mapper, DriveSearchLifecycleProcessor processor) {
    this.mapper = mapper;
    this.processor = processor;
  }

  private static boolean validText(com.fasterxml.jackson.databind.JsonNode node, String field) {
    return node.path(field).isTextual() && !node.path(field).asText().isBlank();
  }

  public boolean consume(String envelope) {
    try {
      var root = mapper.readTree(envelope);
      if (root == null || !root.isObject()) return false;
      var payload = root.path("payload");
      if (!validText(root, "eventId")
          || !validText(root, "idempotencyKey")
          || !validText(root, "correlationId")
          || !validText(root, "actorId")
          || !root.path("source").asText().equals("drive")
          || !root.path("eventType").asText().equals("DriveFileLifecycleChanged")
          || root.path("version").asInt() != 1
          || !root.path("workspaceId").isTextual()
          || root.path("workspaceId").asText().isBlank()
          || !payload.path("fileId").isTextual()
          || !payload.path("fileId").asText().matches("[A-Za-z0-9_-]{1,64}")
          || !payload.path("revision").isIntegralNumber()
          || payload.path("revision").asLong() < 1
          || !(payload.path("desiredState").asText().equals("ACTIVE")
              || payload.path("desiredState").asText().equals("ABSENT"))) return false;
      processor.reconcileFile(root.path("workspaceId").asText(), payload.path("fileId").asText());
      return true;
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      return false;
    }
  }
}
