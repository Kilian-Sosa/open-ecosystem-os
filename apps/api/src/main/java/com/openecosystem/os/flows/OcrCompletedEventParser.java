package com.openecosystem.os.flows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class OcrCompletedEventParser {

  private static final String EVENT_TYPE = "OcrCompleted";

  private final ObjectMapper objectMapper;

  public OcrCompletedEventParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public OcrCompletedEvent parse(String envelopeJson) {
    try {
      JsonNode root = objectMapper.readTree(envelopeJson);
      String eventType = text(root, "eventType");
      if (!EVENT_TYPE.equals(eventType)) {
        throw new IllegalArgumentException("Expected OcrCompleted event but received " + eventType);
      }
      JsonNode payload = root.required("payload");
      return new OcrCompletedEvent(
          text(root, "eventId"),
          root.required("version").asInt(),
          instant(root, "occurredAt"),
          text(root, "workspaceId"),
          text(root, "actorId"),
          text(root, "correlationId"),
          nullableText(root, "causationId"),
          text(root, "idempotencyKey"),
          text(payload, "jobId"),
          text(payload, "fileId"),
          text(payload, "provider"),
          payload.required("attemptCount").asInt(),
          payload.required("extractedTextLength").asInt(),
          instant(payload, "completedAt"));
    } catch (IOException exception) {
      throw new IllegalArgumentException("OcrCompleted envelope could not be parsed", exception);
    }
  }

  private static String text(JsonNode node, String fieldName) {
    String value = node.required(fieldName).asText();
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }

  private static String nullableText(JsonNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    return value == null || value.isNull() ? null : value.asText();
  }

  private static Instant instant(JsonNode node, String fieldName) {
    return Instant.parse(text(node, fieldName));
  }
}
