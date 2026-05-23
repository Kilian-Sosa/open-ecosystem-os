package com.openecosystem.os.worker.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class OcrRequestedEventParser {

  private static final String EVENT_TYPE = "OcrRequested";

  private final ObjectMapper objectMapper;

  public OcrRequestedEventParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public OcrRequestedEvent parse(String envelopeJson) {
    try {
      JsonNode root = objectMapper.readTree(envelopeJson);
      String eventType = text(root, "eventType");
      if (!EVENT_TYPE.equals(eventType)) {
        throw new IllegalArgumentException("Expected OcrRequested event but received " + eventType);
      }
      JsonNode payload = root.required("payload");
      return new OcrRequestedEvent(
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
          text(payload, "contentType"),
          text(payload, "storageKey"),
          payload.required("attemptCount").asInt(),
          payload.required("maxAttempts").asInt(),
          instant(payload, "requestedAt"));
    } catch (IOException exception) {
      throw new IllegalArgumentException("OcrRequested envelope could not be parsed", exception);
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
