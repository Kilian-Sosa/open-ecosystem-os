package com.openecosystem.os.worker.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class IndexingRequestedEventParser {

  private static final String EVENT_TYPE = "IndexingRequested";

  private final ObjectMapper objectMapper;

  public IndexingRequestedEventParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public IndexingRequestedEvent parse(String envelopeJson) {
    try {
      JsonNode root = objectMapper.readTree(envelopeJson);
      String eventType = text(root, "eventType");
      if (!EVENT_TYPE.equals(eventType))
        throw new IllegalArgumentException(
            "Expected IndexingRequested event but received " + eventType);
      JsonNode payload = root.required("payload");
      return new IndexingRequestedEvent(
          text(root, "eventId"),
          root.required("version").asInt(),
          instant(root, "occurredAt"),
          text(root, "workspaceId"),
          text(root, "actorId"),
          text(root, "correlationId"),
          nullableText(root, "causationId"),
          text(root, "idempotencyKey"),
          text(payload, "searchDocumentId"),
          text(payload, "sourceType"),
          text(payload, "sourceId"),
          text(payload, "resourceHref"),
          payload.required("attemptCount").asInt(),
          payload.required("maxAttempts").asInt(),
          instant(payload, "requestedAt"));
    } catch (IOException exception) {
      throw new IllegalArgumentException(
          "IndexingRequested envelope could not be parsed", exception);
    }
  }

  private static String text(JsonNode node, String fieldName) {
    String value = node.required(fieldName).asText();
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(fieldName + " must not be blank");
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
