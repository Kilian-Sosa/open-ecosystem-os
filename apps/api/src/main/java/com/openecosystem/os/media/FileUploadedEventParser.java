package com.openecosystem.os.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

@Component
public class FileUploadedEventParser {

  private static final String EVENT_TYPE = "FileUploaded";

  private final ObjectMapper objectMapper;

  public FileUploadedEventParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public FileUploadedEvent parse(String envelopeJson) {
    try {
      JsonNode root = objectMapper.readTree(envelopeJson);
      String eventType = text(root, "eventType");
      if (!EVENT_TYPE.equals(eventType)) {
        throw new IllegalArgumentException("Expected FileUploaded event but received " + eventType);
      }
      JsonNode payload = root.required("payload");
      return new FileUploadedEvent(
          text(root, "eventId"),
          root.required("version").asInt(),
          instant(root, "occurredAt"),
          text(root, "workspaceId"),
          text(root, "actorId"),
          text(root, "correlationId"),
          text(root, "idempotencyKey"),
          text(payload, "fileId"),
          text(payload, "contentType"),
          payload.required("sizeBytes").asLong(),
          text(payload, "storageKey"),
          instant(payload, "uploadedAt"));
    } catch (IOException exception) {
      throw new IllegalArgumentException("FileUploaded envelope could not be parsed", exception);
    }
  }

  private static String text(JsonNode node, String fieldName) {
    String value = node.required(fieldName).asText();
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }

  private static Instant instant(JsonNode node, String fieldName) {
    JsonNode value = node.required(fieldName);
    if (value.isNumber()) {
      return epochSeconds(value.decimalValue());
    }

    String raw = value.asText();
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }

    try {
      return Instant.parse(raw);
    } catch (DateTimeParseException parseException) {
      try {
        return epochSeconds(new BigDecimal(raw));
      } catch (NumberFormatException | ArithmeticException numberException) {
        throw new IllegalArgumentException(
            fieldName + " must be an ISO-8601 instant or epoch seconds", parseException);
      }
    }
  }

  private static Instant epochSeconds(BigDecimal value) {
    BigDecimal epochSecond = value.setScale(0, RoundingMode.FLOOR);
    BigDecimal fractionalSecond = value.subtract(epochSecond);
    long nanos = fractionalSecond.movePointRight(9).setScale(0, RoundingMode.DOWN).longValue();
    return Instant.ofEpochSecond(epochSecond.longValue(), nanos);
  }
}
