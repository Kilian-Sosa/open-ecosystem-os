package com.openecosystem.os.common.events;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "openecosystem.events.outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EventOutboxPublisher {

  private static final Logger LOGGER = LoggerFactory.getLogger(EventOutboxPublisher.class);

  private final JdbcEventOutboxRepository eventOutboxRepository;
  private final RabbitTemplate rabbitTemplate;
  private final EventMessagingProperties properties;

  public EventOutboxPublisher(
      JdbcEventOutboxRepository eventOutboxRepository,
      RabbitTemplate rabbitTemplate,
      EventMessagingProperties properties) {
    this.eventOutboxRepository = eventOutboxRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.properties = properties;
  }

  @Scheduled(fixedDelayString = "${openecosystem.events.outbox.poll-interval-ms:1000}")
  public void publishUnpublishedEvents() {
    for (JdbcEventOutboxRepository.OutboxEvent event :
        eventOutboxRepository.findUnpublished(properties.outbox().batchSize())) {
      publish(event);
    }
  }

  private void publish(JdbcEventOutboxRepository.OutboxEvent event) {
    try {
      rabbitTemplate.convertAndSend(
          properties.exchange(),
          event.eventType(),
          event.envelopeJson(),
          message -> {
            message.getMessageProperties().setContentType("application/json");
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setMessageId(event.eventId());
            message.getMessageProperties().setHeader("eventId", event.eventId());
            message.getMessageProperties().setHeader("eventType", event.eventType());
            return message;
          });
      eventOutboxRepository.markPublished(event.eventId(), Instant.now());
    } catch (RuntimeException exception) {
      LOGGER.warn("Event outbox publish failed for event {}", event.eventId(), exception);
    }
  }
}
