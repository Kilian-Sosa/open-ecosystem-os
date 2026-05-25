package com.openecosystem.os.flows;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OcrCompletedEventConsumer {

  private final OcrCompletedEventParser eventParser;
  private final OcrCompletedWorkflowTriggerService triggerService;

  public OcrCompletedEventConsumer(
      OcrCompletedEventParser eventParser, OcrCompletedWorkflowTriggerService triggerService) {
    this.eventParser = eventParser;
    this.triggerService = triggerService;
  }

  @RabbitListener(
      queues =
          "${openecosystem.events.queues.flows-ocr-completed:openecosystem.flows.ocr-completed}")
  public void consume(String envelopeJson) {
    try {
      triggerService.trigger(eventParser.parse(envelopeJson));
    } catch (RuntimeException exception) {
      throw new AmqpRejectAndDontRequeueException(
          "OcrCompleted workflow trigger consumer failed", exception);
    }
  }
}
