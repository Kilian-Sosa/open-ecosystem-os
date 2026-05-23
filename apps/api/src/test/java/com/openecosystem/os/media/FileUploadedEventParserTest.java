package com.openecosystem.os.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FileUploadedEventParserTest {

  private final FileUploadedEventParser parser = new FileUploadedEventParser(new ObjectMapper());

  @Test
  void parsesIsoInstantTimestamps() {
    FileUploadedEvent event =
        parser.parse(
            """
            {
              "eventId": "evt_iso",
              "eventType": "FileUploaded",
              "version": 1,
              "occurredAt": "2026-05-22T10:00:00Z",
              "workspaceId": "wrk_123",
              "actorId": "usr_123",
              "correlationId": "corr_123",
              "idempotencyKey": "drive:file_iso:uploaded:v1",
              "payload": {
                "fileId": "file_iso",
                "contentType": "application/pdf",
                "sizeBytes": 1024,
                "storageKey": "workspaces/wrk_123/drive/file_iso/original",
                "uploadedAt": "2026-05-22T10:00:01Z"
              }
            }
            """);

    assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-05-22T10:00:00Z"));
    assertThat(event.uploadedAt()).isEqualTo(Instant.parse("2026-05-22T10:00:01Z"));
  }

  @Test
  void parsesEpochSecondTimestampsWrittenByExistingOutboxEvents() {
    FileUploadedEvent event =
        parser.parse(
            """
            {
              "eventId": "evt_numeric",
              "eventType": "FileUploaded",
              "version": 1,
              "occurredAt": 1.7794486238590353E9,
              "workspaceId": "wrk_123",
              "actorId": "usr_123",
              "correlationId": "corr_123",
              "idempotencyKey": "drive:file_numeric:uploaded:v1",
              "payload": {
                "fileId": "file_numeric",
                "contentType": "image/png",
                "sizeBytes": 2048,
                "storageKey": "workspaces/wrk_123/drive/file_numeric/original",
                "uploadedAt": "1.779448778703988E9"
              }
            }
            """);

    assertThat(event.occurredAt()).isEqualTo(Instant.ofEpochSecond(1779448623, 859035300));
    assertThat(event.uploadedAt()).isEqualTo(Instant.ofEpochSecond(1779448778, 703988000));
  }
}
