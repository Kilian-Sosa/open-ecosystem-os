package com.openecosystem.os.worker.search;

import com.openecosystem.os.worker.common.Ids;
import com.openecosystem.os.worker.common.events.EventConsumptionRepository;
import com.openecosystem.os.worker.common.events.EventEnvelope;
import com.openecosystem.os.worker.common.events.JdbcEventOutboxRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SearchIndexingProcessor {

  private static final String CONSUMER_NAME = "worker.search-indexing.processor";
  private static final String INDEXING_COMPLETED = "IndexingCompleted";
  private static final String INDEXING_FAILED = "IndexingFailed";

  private final SearchIndexClient searchIndexClient;
  private final SearchDocumentRepository searchDocumentRepository;
  private final JdbcEventOutboxRepository eventOutboxRepository;
  private final EventConsumptionRepository eventConsumptionRepository;
  private final TransactionTemplate transactionTemplate;

  public SearchIndexingProcessor(
      SearchIndexClient searchIndexClient,
      SearchDocumentRepository searchDocumentRepository,
      JdbcEventOutboxRepository eventOutboxRepository,
      EventConsumptionRepository eventConsumptionRepository,
      TransactionTemplate transactionTemplate) {
    this.searchIndexClient = searchIndexClient;
    this.searchDocumentRepository = searchDocumentRepository;
    this.eventOutboxRepository = eventOutboxRepository;
    this.eventConsumptionRepository = eventConsumptionRepository;
    this.transactionTemplate = transactionTemplate;
  }

  public SearchIndexingResult process(IndexingRequestedEvent event) {
    if (event.version() != 1
        || eventConsumptionRepository.exists(CONSUMER_NAME, event.idempotencyKey()))
      return new SearchIndexingResult(SearchIndexingOutcome.NO_OP, event.searchDocumentId());

    SearchDocument document = claimDocument(event);
    if (document == null)
      return new SearchIndexingResult(SearchIndexingOutcome.NO_OP, event.searchDocumentId());

    try {
      searchIndexClient.index(document);
      complete(event, document);
      return new SearchIndexingResult(SearchIndexingOutcome.INDEXED, event.searchDocumentId());
    } catch (RuntimeException exception) {
      return handleFailure(event, document, exception);
    }
  }

  private SearchDocument claimDocument(IndexingRequestedEvent event) {
    return transactionTemplate.execute(
        status ->
            searchDocumentRepository
                .findById(event.searchDocumentId())
                .map(
                    existing -> {
                      if (existing.status() == SearchDocumentStatus.INDEXED
                          || existing.status() == SearchDocumentStatus.FAILED) {
                        eventConsumptionRepository.save(
                            CONSUMER_NAME, event.idempotencyKey(), event.eventId(), Instant.now());
                        return null;
                      }
                      return searchDocumentRepository
                          .startAttempt(event.searchDocumentId(), Instant.now())
                          .orElse(null);
                    })
                .orElse(null));
  }

  private void complete(IndexingRequestedEvent event, SearchDocument document) {
    transactionTemplate.executeWithoutResult(
        status -> {
          Instant now = Instant.now();
          searchDocumentRepository.complete(document.searchDocumentId(), now);
          eventOutboxRepository.save(indexingCompletedEnvelope(event, document, now));
          eventConsumptionRepository.save(
              CONSUMER_NAME, event.idempotencyKey(), event.eventId(), now);
        });
  }

  private SearchIndexingResult handleFailure(
      IndexingRequestedEvent event, SearchDocument document, RuntimeException exception) {
    String errorCode =
        exception instanceof SearchIndexingException indexingException
            ? indexingException.code()
            : "SEARCH_INDEXING_FAILED";
    String errorMessage = sanitizedMessage(exception);
    boolean finalAttempt = document.attemptCount() >= document.maxAttempts();
    Instant now = Instant.now();

    if (!finalAttempt) {
      searchDocumentRepository.queueRetry(
          document.searchDocumentId(), errorCode, errorMessage, now);
      return new SearchIndexingResult(SearchIndexingOutcome.RETRY, document.searchDocumentId());
    }

    transactionTemplate.executeWithoutResult(
        status -> {
          searchDocumentRepository.fail(document.searchDocumentId(), errorCode, errorMessage, now);
          eventOutboxRepository.save(
              indexingFailedEnvelope(event, document, errorCode, errorMessage, now));
          eventConsumptionRepository.save(
              CONSUMER_NAME, event.idempotencyKey(), event.eventId(), now);
        });
    return new SearchIndexingResult(SearchIndexingOutcome.DEAD_LETTER, document.searchDocumentId());
  }

  private EventEnvelope<IndexingCompletedPayload> indexingCompletedEnvelope(
      IndexingRequestedEvent event, SearchDocument document, Instant now) {
    return new EventEnvelope<>(
        Ids.newId("evt"),
        INDEXING_COMPLETED,
        1,
        now,
        document.workspaceId(),
        event.actorId(),
        event.correlationId(),
        event.eventId(),
        "search",
        "search:document:" + document.searchDocumentId() + ":completed:v1",
        new IndexingCompletedPayload(
            document.searchDocumentId(),
            document.sourceType(),
            document.sourceId(),
            document.resourceHref(),
            document.attemptCount(),
            now));
  }

  private EventEnvelope<IndexingFailedPayload> indexingFailedEnvelope(
      IndexingRequestedEvent event,
      SearchDocument document,
      String errorCode,
      String errorMessage,
      Instant now) {
    return new EventEnvelope<>(
        Ids.newId("evt"),
        INDEXING_FAILED,
        1,
        now,
        document.workspaceId(),
        event.actorId(),
        event.correlationId(),
        event.eventId(),
        "search",
        "search:document:" + document.searchDocumentId() + ":failed:v1",
        new IndexingFailedPayload(
            document.searchDocumentId(),
            document.sourceType(),
            document.sourceId(),
            document.resourceHref(),
            document.attemptCount(),
            document.maxAttempts(),
            errorCode,
            errorMessage,
            now));
  }

  private String sanitizedMessage(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) return "Search indexing failed";
    return message.length() > 256 ? message.substring(0, 256) : message;
  }
}
