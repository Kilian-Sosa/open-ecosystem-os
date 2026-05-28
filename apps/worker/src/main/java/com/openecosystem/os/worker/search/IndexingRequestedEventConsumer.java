package com.openecosystem.os.worker.search;

import com.openecosystem.os.worker.common.events.EventMessagingProperties;
import com.openecosystem.os.worker.common.observability.CorrelationMdcScope;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class IndexingRequestedEventConsumer {

  private final IndexingRequestedEventParser eventParser;
  private final SearchIndexingProcessor processor;
  private final RabbitTemplate rabbitTemplate;
  private final EventMessagingProperties properties;

  public IndexingRequestedEventConsumer(
      IndexingRequestedEventParser eventParser,
      SearchIndexingProcessor processor,
      RabbitTemplate rabbitTemplate,
      EventMessagingProperties properties) {
    this.eventParser = eventParser;
    this.processor = processor;
    this.rabbitTemplate = rabbitTemplate;
    this.properties = properties;
  }

  @RabbitListener(
      queues =
          "${openecosystem.events.queues.search-indexing-requested:openecosystem.search.indexing-requested}")
  public void consume(String envelopeJson) {
    try {
      IndexingRequestedEvent event = eventParser.parse(envelopeJson);
      try (CorrelationMdcScope ignored = CorrelationMdcScope.open(event.correlationId())) {
        SearchIndexingResult result = processor.process(event);
        if (result.retry())
          throw new AmqpRejectAndDontRequeueException("Search indexing will be retried");
        if (result.deadLetter()) publishToDeadLetterQueue(envelopeJson);
      }
    } catch (AmqpRejectAndDontRequeueException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new AmqpRejectAndDontRequeueException("IndexingRequested consumer failed", exception);
    }
  }

  private void publishToDeadLetterQueue(String envelopeJson) {
    rabbitTemplate.convertAndSend(
        properties.deadLetterExchange(),
        properties.queues().searchIndexingRequestedDlq(),
        envelopeJson,
        message -> {
          message.getMessageProperties().setContentType("application/json");
          message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
          return message;
        });
  }
}
