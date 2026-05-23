package com.openecosystem.os.worker.ocr;

import com.openecosystem.os.worker.common.events.EventMessagingProperties;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OcrRequestedEventConsumer {

  private final OcrRequestedEventParser eventParser;
  private final OcrJobProcessor processor;
  private final RabbitTemplate rabbitTemplate;
  private final EventMessagingProperties properties;

  public OcrRequestedEventConsumer(
      OcrRequestedEventParser eventParser,
      OcrJobProcessor processor,
      RabbitTemplate rabbitTemplate,
      EventMessagingProperties properties) {
    this.eventParser = eventParser;
    this.processor = processor;
    this.rabbitTemplate = rabbitTemplate;
    this.properties = properties;
  }

  @RabbitListener(
      queues = "${openecosystem.events.queues.ocr-requested:openecosystem.ocr.requested}")
  public void consume(String envelopeJson) {
    try {
      OcrRequestedEvent event = eventParser.parse(envelopeJson);
      OcrProcessingResult result = processor.process(event);
      if (result.retry()) {
        throw new AmqpRejectAndDontRequeueException("OCR job will be retried");
      }
      if (result.deadLetter()) {
        publishToDeadLetterQueue(envelopeJson);
      }
    } catch (AmqpRejectAndDontRequeueException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new AmqpRejectAndDontRequeueException("OcrRequested consumer failed", exception);
    }
  }

  private void publishToDeadLetterQueue(String envelopeJson) {
    rabbitTemplate.convertAndSend(
        properties.deadLetterExchange(),
        properties.queues().ocrRequestedDlq(),
        envelopeJson,
        message -> {
          message.getMessageProperties().setContentType("application/json");
          message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
          return message;
        });
  }
}
