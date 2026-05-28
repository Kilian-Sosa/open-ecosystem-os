package com.openecosystem.os.worker.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.openecosystem.os.worker.common.events.EventMessagingProperties;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class OcrRequestedEventConsumerTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void setsCorrelationIdMdcWhileProcessingEvent() {
    OcrRequestedEvent event = requestedEvent();
    OcrRequestedEventParser eventParser = mock(OcrRequestedEventParser.class);
    OcrJobProcessor processor = mock(OcrJobProcessor.class);
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    EventMessagingProperties properties = mock(EventMessagingProperties.class);
    when(eventParser.parse("{}")).thenReturn(event);
    doAnswer(
            invocation -> {
              assertThat(MDC.get("correlationId")).isEqualTo("corr_worker_consumer_test");
              return new OcrProcessingResult(OcrProcessingOutcome.COMPLETED, event.jobId());
            })
        .when(processor)
        .process(event);

    new OcrRequestedEventConsumer(eventParser, processor, rabbitTemplate, properties).consume("{}");

    assertThat(MDC.get("correlationId")).isNull();
  }

  private OcrRequestedEvent requestedEvent() {
    Instant now = Instant.parse("2026-05-22T10:00:00Z");
    return new OcrRequestedEvent(
        "evt_ocr_requested",
        1,
        now,
        "wrk_123",
        "usr_123",
        "corr_worker_consumer_test",
        "evt_file_uploaded",
        "media:ocr:ocr_123:requested:v1",
        "ocr_123",
        "file_123",
        "application/pdf",
        "workspaces/wrk_123/drive/file_123/original",
        0,
        3,
        now);
  }
}
