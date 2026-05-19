package com.openecosystem.os.common.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EventEnvelopeTest {

  @Test
  void createsEnvelopeWithRequiredMetadata() {
    EventEnvelope<Map<String, String>> envelope =
        new EventEnvelope<>(
            "evt_123",
            "FileUploaded",
            1,
            Instant.parse("2026-05-19T10:00:00Z"),
            "wrk_123",
            "usr_123",
            "corr_123",
            null,
            "drive",
            "drive:file_123:uploaded:v1",
            Map.of("fileId", "file_123"));

    assertThat(envelope.eventType()).isEqualTo("FileUploaded");
    assertThat(envelope.payload()).containsEntry("fileId", "file_123");
  }

  @Test
  void rejectsInvalidVersion() {
    assertThatThrownBy(
            () ->
                new EventEnvelope<>(
                    "evt_123",
                    "FileUploaded",
                    0,
                    Instant.now(),
                    "wrk_123",
                    "usr_123",
                    "corr_123",
                    null,
                    "drive",
                    "drive:file_123:uploaded:v1",
                    Map.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("version");
  }
}
