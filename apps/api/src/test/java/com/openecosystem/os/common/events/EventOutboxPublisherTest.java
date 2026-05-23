package com.openecosystem.os.common.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.OpenEcosystemApiApplication;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = OpenEcosystemApiApplication.class)
class EventOutboxPublisherTest {

  @Autowired private JdbcEventOutboxRepository eventOutboxRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from event_consumptions");
    jdbcTemplate.update("delete from ocr_jobs");
    jdbcTemplate.update("delete from event_outbox");
    jdbcTemplate.update("delete from audit_records");
    jdbcTemplate.update("delete from drive_files");
  }

  @Test
  void publishesUnpublishedOutboxEventsAndMarksThemPublished() {
    eventOutboxRepository.save(
        new EventEnvelope<>(
            "evt_123",
            "OcrRequested",
            1,
            Instant.parse("2026-05-22T10:00:00Z"),
            "wrk_123",
            "usr_123",
            "corr_123",
            "evt_previous",
            "media",
            "media:ocr:ocr_123:requested:v1",
            Map.of("jobId", "ocr_123")));
    CapturingRabbitTemplate rabbitTemplate = new CapturingRabbitTemplate();
    EventMessagingProperties properties =
        new EventMessagingProperties(
            "openecosystem.events",
            "openecosystem.events.retry",
            "openecosystem.events.dlx",
            new EventMessagingProperties.Outbox(true, 1000, 10),
            null);

    new EventOutboxPublisher(eventOutboxRepository, rabbitTemplate, properties)
        .publishUnpublishedEvents();

    assertThat(rabbitTemplate.exchange).isEqualTo("openecosystem.events");
    assertThat(rabbitTemplate.routingKey).isEqualTo("OcrRequested");
    assertThat(rabbitTemplate.message).contains("\"jobId\":\"ocr_123\"");
    assertThat(rabbitTemplate.message).contains("\"occurredAt\":\"2026-05-22T10:00:00Z\"");
    Object publishedAt =
        jdbcTemplate.queryForObject(
            "select published_at from event_outbox where event_id = 'evt_123'", Object.class);
    assertThat(publishedAt).isNotNull();
  }

  static class CapturingRabbitTemplate extends RabbitTemplate {

    String exchange;
    String routingKey;
    String message;

    @Override
    public void convertAndSend(
        String exchange,
        String routingKey,
        Object object,
        MessagePostProcessor messagePostProcessor) {
      this.exchange = exchange;
      this.routingKey = routingKey;
      this.message = (String) object;
      messagePostProcessor.postProcessMessage(new Message(new byte[0], new MessageProperties()));
    }
  }
}
