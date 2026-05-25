package com.openecosystem.os.common.events;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class EventRabbitConfiguration {

  static final String FILE_UPLOADED_ROUTING_KEY = "FileUploaded";
  static final String OCR_REQUESTED_ROUTING_KEY = "OcrRequested";
  static final String OCR_COMPLETED_ROUTING_KEY = "OcrCompleted";
  static final String INDEXING_REQUESTED_ROUTING_KEY = "IndexingRequested";

  @Bean
  DirectExchange eventExchange(EventMessagingProperties properties) {
    return new DirectExchange(properties.exchange(), true, false);
  }

  @Bean
  DirectExchange eventRetryExchange(EventMessagingProperties properties) {
    return new DirectExchange(properties.retryExchange(), true, false);
  }

  @Bean
  DirectExchange eventDeadLetterExchange(EventMessagingProperties properties) {
    return new DirectExchange(properties.deadLetterExchange(), true, false);
  }

  @Bean
  Queue mediaFileUploadedQueue(EventMessagingProperties properties) {
    return primaryQueue(
        properties.queues().mediaFileUploaded(),
        properties.retryExchange(),
        properties.queues().mediaFileUploadedRetry());
  }

  @Bean
  Queue mediaFileUploadedRetryQueue(EventMessagingProperties properties) {
    return retryQueue(
        properties.queues().mediaFileUploadedRetry(),
        properties.exchange(),
        FILE_UPLOADED_ROUTING_KEY,
        properties.queues().retryDelay().toMillis());
  }

  @Bean
  Queue mediaFileUploadedDlq(EventMessagingProperties properties) {
    return new Queue(properties.queues().mediaFileUploadedDlq(), true);
  }

  @Bean
  Queue ocrRequestedQueue(EventMessagingProperties properties) {
    return primaryQueue(
        properties.queues().ocrRequested(),
        properties.retryExchange(),
        properties.queues().ocrRequestedRetry());
  }

  @Bean
  Queue ocrRequestedRetryQueue(EventMessagingProperties properties) {
    return retryQueue(
        properties.queues().ocrRequestedRetry(),
        properties.exchange(),
        OCR_REQUESTED_ROUTING_KEY,
        properties.queues().retryDelay().toMillis());
  }

  @Bean
  Queue ocrRequestedDlq(EventMessagingProperties properties) {
    return new Queue(properties.queues().ocrRequestedDlq(), true);
  }

  @Bean
  Queue flowsOcrCompletedQueue(EventMessagingProperties properties) {
    return primaryQueue(
        properties.queues().flowsOcrCompleted(),
        properties.retryExchange(),
        properties.queues().flowsOcrCompletedRetry());
  }

  @Bean
  Queue flowsOcrCompletedRetryQueue(EventMessagingProperties properties) {
    return retryQueue(
        properties.queues().flowsOcrCompletedRetry(),
        properties.exchange(),
        OCR_COMPLETED_ROUTING_KEY,
        properties.queues().retryDelay().toMillis());
  }

  @Bean
  Queue flowsOcrCompletedDlq(EventMessagingProperties properties) {
    return new Queue(properties.queues().flowsOcrCompletedDlq(), true);
  }

  @Bean
  Queue searchIndexingRequestedQueue(EventMessagingProperties properties) {
    return primaryQueue(
        properties.queues().searchIndexingRequested(),
        properties.retryExchange(),
        properties.queues().searchIndexingRequestedRetry());
  }

  @Bean
  Queue searchIndexingRequestedRetryQueue(EventMessagingProperties properties) {
    return retryQueue(
        properties.queues().searchIndexingRequestedRetry(),
        properties.exchange(),
        INDEXING_REQUESTED_ROUTING_KEY,
        properties.queues().retryDelay().toMillis());
  }

  @Bean
  Queue searchIndexingRequestedDlq(EventMessagingProperties properties) {
    return new Queue(properties.queues().searchIndexingRequestedDlq(), true);
  }

  @Bean
  Binding mediaFileUploadedBinding(
      @Qualifier("mediaFileUploadedQueue") Queue mediaFileUploadedQueue,
      @Qualifier("eventExchange") DirectExchange eventExchange) {
    return BindingBuilder.bind(mediaFileUploadedQueue)
        .to(eventExchange)
        .with(FILE_UPLOADED_ROUTING_KEY);
  }

  @Bean
  Binding mediaFileUploadedRetryBinding(
      @Qualifier("mediaFileUploadedRetryQueue") Queue mediaFileUploadedRetryQueue,
      @Qualifier("eventRetryExchange") DirectExchange eventRetryExchange,
      EventMessagingProperties properties) {
    return BindingBuilder.bind(mediaFileUploadedRetryQueue)
        .to(eventRetryExchange)
        .with(properties.queues().mediaFileUploadedRetry());
  }

  @Bean
  Binding mediaFileUploadedDlqBinding(
      @Qualifier("mediaFileUploadedDlq") Queue mediaFileUploadedDlq,
      @Qualifier("eventDeadLetterExchange") DirectExchange eventDeadLetterExchange,
      EventMessagingProperties properties) {
    return BindingBuilder.bind(mediaFileUploadedDlq)
        .to(eventDeadLetterExchange)
        .with(properties.queues().mediaFileUploadedDlq());
  }

  @Bean
  Binding ocrRequestedBinding(
      @Qualifier("ocrRequestedQueue") Queue ocrRequestedQueue,
      @Qualifier("eventExchange") DirectExchange eventExchange) {
    return BindingBuilder.bind(ocrRequestedQueue).to(eventExchange).with(OCR_REQUESTED_ROUTING_KEY);
  }

  @Bean
  Binding ocrRequestedRetryBinding(
      @Qualifier("ocrRequestedRetryQueue") Queue ocrRequestedRetryQueue,
      @Qualifier("eventRetryExchange") DirectExchange eventRetryExchange,
      EventMessagingProperties properties) {
    return BindingBuilder.bind(ocrRequestedRetryQueue)
        .to(eventRetryExchange)
        .with(properties.queues().ocrRequestedRetry());
  }

  @Bean
  Binding ocrRequestedDlqBinding(
      @Qualifier("ocrRequestedDlq") Queue ocrRequestedDlq,
      @Qualifier("eventDeadLetterExchange") DirectExchange eventDeadLetterExchange,
      EventMessagingProperties properties) {
    return BindingBuilder.bind(ocrRequestedDlq)
        .to(eventDeadLetterExchange)
        .with(properties.queues().ocrRequestedDlq());
  }

  @Bean
  Binding flowsOcrCompletedBinding(
      @Qualifier("flowsOcrCompletedQueue") Queue flowsOcrCompletedQueue,
      @Qualifier("eventExchange") DirectExchange eventExchange) {
    return BindingBuilder.bind(flowsOcrCompletedQueue)
        .to(eventExchange)
        .with(OCR_COMPLETED_ROUTING_KEY);
  }

  @Bean
  Binding flowsOcrCompletedRetryBinding(
      @Qualifier("flowsOcrCompletedRetryQueue") Queue flowsOcrCompletedRetryQueue,
      @Qualifier("eventRetryExchange") DirectExchange eventRetryExchange,
      EventMessagingProperties properties) {
    return BindingBuilder.bind(flowsOcrCompletedRetryQueue)
        .to(eventRetryExchange)
        .with(properties.queues().flowsOcrCompletedRetry());
  }

  @Bean
  Binding flowsOcrCompletedDlqBinding(
      @Qualifier("flowsOcrCompletedDlq") Queue flowsOcrCompletedDlq,
      @Qualifier("eventDeadLetterExchange") DirectExchange eventDeadLetterExchange,
      EventMessagingProperties properties) {
    return BindingBuilder.bind(flowsOcrCompletedDlq)
        .to(eventDeadLetterExchange)
        .with(properties.queues().flowsOcrCompletedDlq());
  }

  @Bean
  Binding searchIndexingRequestedBinding(
      @Qualifier("searchIndexingRequestedQueue") Queue searchIndexingRequestedQueue,
      @Qualifier("eventExchange") DirectExchange eventExchange) {
    return BindingBuilder.bind(searchIndexingRequestedQueue)
        .to(eventExchange)
        .with(INDEXING_REQUESTED_ROUTING_KEY);
  }

  @Bean
  Binding searchIndexingRequestedRetryBinding(
      @Qualifier("searchIndexingRequestedRetryQueue") Queue searchIndexingRequestedRetryQueue,
      @Qualifier("eventRetryExchange") DirectExchange eventRetryExchange,
      EventMessagingProperties properties) {
    return BindingBuilder.bind(searchIndexingRequestedRetryQueue)
        .to(eventRetryExchange)
        .with(properties.queues().searchIndexingRequestedRetry());
  }

  @Bean
  Binding searchIndexingRequestedDlqBinding(
      @Qualifier("searchIndexingRequestedDlq") Queue searchIndexingRequestedDlq,
      @Qualifier("eventDeadLetterExchange") DirectExchange eventDeadLetterExchange,
      EventMessagingProperties properties) {
    return BindingBuilder.bind(searchIndexingRequestedDlq)
        .to(eventDeadLetterExchange)
        .with(properties.queues().searchIndexingRequestedDlq());
  }

  private Queue primaryQueue(String queueName, String retryExchange, String retryRoutingKey) {
    return new Queue(
        queueName,
        true,
        false,
        false,
        Map.of(
            "x-dead-letter-exchange", retryExchange, "x-dead-letter-routing-key", retryRoutingKey));
  }

  private Queue retryQueue(
      String queueName, String eventExchange, String eventRoutingKey, long retryDelayMillis) {
    return new Queue(
        queueName,
        true,
        false,
        false,
        Map.of(
            "x-message-ttl",
            retryDelayMillis,
            "x-dead-letter-exchange",
            eventExchange,
            "x-dead-letter-routing-key",
            eventRoutingKey));
  }
}
