package com.openecosystem.os.media;

import com.openecosystem.os.common.observability.CorrelationMdcScope;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class FileUploadedEventConsumer {

  private final FileUploadedEventParser eventParser;
  private final FileUploadedOcrJobService ocrJobService;

  public FileUploadedEventConsumer(
      FileUploadedEventParser eventParser, FileUploadedOcrJobService ocrJobService) {
    this.eventParser = eventParser;
    this.ocrJobService = ocrJobService;
  }

  @RabbitListener(
      queues =
          "${openecosystem.events.queues.media-file-uploaded:openecosystem.media.file-uploaded}")
  public void consume(String envelopeJson) {
    try {
      FileUploadedEvent event = eventParser.parse(envelopeJson);
      try (CorrelationMdcScope ignored = CorrelationMdcScope.open(event.correlationId())) {
        if (event.version() == 1) ocrJobService.queueOcrJobIfEligible(event);
      }
    } catch (RuntimeException exception) {
      throw new AmqpRejectAndDontRequeueException("FileUploaded OCR consumer failed", exception);
    }
  }
}
