package com.openecosystem.os.worker.search;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openecosystem.os.worker.common.events.EventMessagingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class DriveFileLifecycleRoutingTest {
  @Test
  void lifecycleEnvelopeRoutesToRegisteredConsumerWithoutIndexingParser() {
    var parser = mock(IndexingRequestedEventParser.class);
    var indexer = mock(SearchIndexingProcessor.class);
    var lifecycle = mock(DriveFileLifecycleConsumer.class);
    var rabbit = mock(RabbitTemplate.class);
    var properties = mock(EventMessagingProperties.class);
    String body = "{\"eventType\":\"DriveFileLifecycleChanged\"}";
    when(lifecycle.consume(body)).thenReturn(true);
    new IndexingRequestedEventConsumer(
            new ObjectMapper(), lifecycle, parser, indexer, rabbit, properties)
        .consume(body);
    verify(lifecycle).consume(body);
    verifyNoInteractions(parser, indexer, rabbit);
  }

  @Test
  void invalidLifecycleEnvelopeGoesToExistingSearchDlq() {
    var parser = mock(IndexingRequestedEventParser.class);
    var indexer = mock(SearchIndexingProcessor.class);
    var processor = mock(DriveSearchLifecycleProcessor.class);
    var rabbit = mock(RabbitTemplate.class);
    var properties = mock(EventMessagingProperties.class);
    var queues = mock(EventMessagingProperties.Queues.class);
    when(properties.queues()).thenReturn(queues);
    when(properties.deadLetterExchange()).thenReturn("synthetic_dlx");
    when(queues.searchIndexingRequestedDlq()).thenReturn("synthetic_dlq");
    String body = "{\"eventType\":\"DriveFileLifecycleChanged\",\"version\":1}";
    var lifecycle = new DriveFileLifecycleConsumer(new ObjectMapper(), processor);
    new IndexingRequestedEventConsumer(
            new ObjectMapper(), lifecycle, parser, indexer, rabbit, properties)
        .consume(body);
    verify(rabbit)
        .convertAndSend(
            eq("synthetic_dlx"), eq("synthetic_dlq"), eq(body), any(MessagePostProcessor.class));
    verifyNoInteractions(parser, indexer, processor);
  }
}
