package com.openecosystem.os.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class FileUploadedEventConsumerTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void setsCorrelationIdMdcWhileProcessingEvent() {
    FileUploadedEvent event = fileUploaded();
    FileUploadedEventParser eventParser = mock(FileUploadedEventParser.class);
    FileUploadedOcrJobService ocrJobService = mock(FileUploadedOcrJobService.class);
    when(eventParser.parse("{}")).thenReturn(event);
    doAnswer(
            invocation -> {
              assertThat(MDC.get("correlationId")).isEqualTo("corr_consumer_test");
              return null;
            })
        .when(ocrJobService)
        .queueOcrJobIfEligible(event);

    new FileUploadedEventConsumer(eventParser, ocrJobService).consume("{}");

    assertThat(MDC.get("correlationId")).isNull();
  }

  private FileUploadedEvent fileUploaded() {
    Instant now = Instant.parse("2026-05-22T10:00:00Z");
    return new FileUploadedEvent(
        "evt_file_uploaded",
        1,
        now,
        "wrk_123",
        "usr_123",
        "corr_consumer_test",
        "drive:file_123:uploaded:v1",
        "file_123",
        "application/pdf",
        1024,
        "workspaces/wrk_123/drive/file_123/original",
        now);
  }
}
