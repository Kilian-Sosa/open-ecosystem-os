package com.openecosystem.os.worker.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.worker.OpenEcosystemWorkerApplication;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    classes = {
      OpenEcosystemWorkerApplication.class,
      SearchIndexingProcessorTest.SearchIndexingProcessorTestConfiguration.class
    })
class SearchIndexingProcessorTest {

  @Autowired private SearchIndexingProcessor processor;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TestSearchIndexClient indexClient;

  @BeforeEach
  void createSchema() {
    indexClient.fail.set(false);
    indexClient.indexedCount = 0;
    jdbcTemplate.update("drop table if exists event_consumptions");
    jdbcTemplate.update("drop table if exists event_outbox");
    jdbcTemplate.update("drop table if exists search_documents");
    jdbcTemplate.update(
        """
        create table search_documents (
          search_document_id varchar(64) primary key,
          workspace_id varchar(128) not null,
          source_type varchar(128) not null,
          source_id varchar(128) not null,
          title varchar(255) not null,
          summary varchar(1024) not null,
          content text not null,
          resource_href varchar(1024) not null,
          correlation_id varchar(128) not null,
          status varchar(32) not null,
          attempt_count integer not null,
          max_attempts integer not null,
          failure_code varchar(128),
          failure_message varchar(512),
          metadata_json text not null,
          created_at timestamp with time zone not null,
          updated_at timestamp with time zone not null,
          indexed_at timestamp with time zone,
          failed_at timestamp with time zone
        )
        """);
    jdbcTemplate.update(
        """
        create table event_outbox (
          event_id varchar(64) primary key,
          event_type varchar(128) not null,
          version integer not null,
          occurred_at timestamp with time zone not null,
          workspace_id varchar(128) not null,
          actor_id varchar(128) not null,
          correlation_id varchar(128) not null,
          causation_id varchar(128),
          source varchar(128) not null,
          idempotency_key varchar(256) not null unique,
          payload_json text not null,
          envelope_json text not null,
          published_at timestamp with time zone,
          created_at timestamp with time zone not null
        )
        """);
    jdbcTemplate.update(
        """
        create table event_consumptions (
          consumer_name varchar(128) not null,
          idempotency_key varchar(256) not null,
          event_id varchar(64) not null,
          consumed_at timestamp with time zone not null,
          primary key (consumer_name, idempotency_key)
        )
        """);
  }

  @Test
  void indexesPendingDocumentAndSkipsDuplicateDelivery() {
    insertDocument("srch_success", 3);
    IndexingRequestedEvent event = event("evt_index", "srch_success");

    SearchIndexingResult firstResult = processor.process(event);
    SearchIndexingResult duplicateResult = processor.process(event);

    assertThat(firstResult.outcome()).isEqualTo(SearchIndexingOutcome.INDEXED);
    assertThat(duplicateResult.outcome()).isEqualTo(SearchIndexingOutcome.NO_OP);
    assertThat(indexClient.indexedCount).isEqualTo(1);

    Map<String, Object> document =
        jdbcTemplate.queryForMap(
            "select * from search_documents where search_document_id = ?", "srch_success");
    assertThat(document.get("status")).isEqualTo("indexed");
    assertThat(document.get("attempt_count")).isEqualTo(1);
    assertThat(document.get("indexed_at")).isNotNull();

    Integer completed =
        jdbcTemplate.queryForObject(
            "select count(*) from event_outbox where event_type = 'IndexingCompleted'",
            Integer.class);
    Integer consumptions =
        jdbcTemplate.queryForObject("select count(*) from event_consumptions", Integer.class);
    assertThat(completed).isEqualTo(1);
    assertThat(consumptions).isEqualTo(1);
  }

  @Test
  void retriesIndexFailureThenFailsAndEmitsFailedEvent() {
    indexClient.fail.set(true);
    insertDocument("srch_fail", 2);
    IndexingRequestedEvent event = event("evt_index_fail", "srch_fail");

    SearchIndexingResult retryResult = processor.process(event);
    SearchIndexingResult finalResult = processor.process(event);

    assertThat(retryResult.outcome()).isEqualTo(SearchIndexingOutcome.RETRY);
    assertThat(finalResult.outcome()).isEqualTo(SearchIndexingOutcome.DEAD_LETTER);

    Map<String, Object> document =
        jdbcTemplate.queryForMap(
            "select * from search_documents where search_document_id = ?", "srch_fail");
    assertThat(document.get("status")).isEqualTo("failed");
    assertThat(document.get("attempt_count")).isEqualTo(2);
    assertThat(document.get("failure_code")).isEqualTo("TEST_SEARCH_FAILED");

    Integer failed =
        jdbcTemplate.queryForObject(
            "select count(*) from event_outbox where event_type = 'IndexingFailed'", Integer.class);
    Integer consumptions =
        jdbcTemplate.queryForObject("select count(*) from event_consumptions", Integer.class);
    assertThat(failed).isEqualTo(1);
    assertThat(consumptions).isEqualTo(1);
  }

  private void insertDocument(String searchDocumentId, int maxAttempts) {
    Instant now = Instant.parse("2026-05-23T10:00:00Z");
    jdbcTemplate.update(
        """
        insert into search_documents (
          search_document_id,
          workspace_id,
          source_type,
          source_id,
          title,
          summary,
          content,
          resource_href,
          correlation_id,
          status,
          attempt_count,
          max_attempts,
          failure_code,
          failure_message,
          metadata_json,
          created_at,
          updated_at,
          indexed_at,
          failed_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, null, null, ?, ?, ?, null, null)
        """,
        searchDocumentId,
        "wrk_123",
        "demo_invoice_extraction",
        "invx_123",
        "Fake/test invoice",
        "Fake/test invoice summary",
        "TEST-INV-2026-0001 fake/test data",
        "/app/search?q=TEST-INV-2026-0001",
        "corr_123",
        "pending",
        maxAttempts,
        "{\"isTestData\":true}",
        now,
        now);
  }

  private IndexingRequestedEvent event(String eventId, String searchDocumentId) {
    Instant now = Instant.parse("2026-05-23T10:01:00Z");
    return new IndexingRequestedEvent(
        eventId,
        1,
        now,
        "wrk_123",
        "usr_123",
        "corr_123",
        "evt_ocr_completed",
        "search:document:" + searchDocumentId + ":requested:v1",
        searchDocumentId,
        "demo_invoice_extraction",
        "invx_123",
        "/app/search?q=TEST-INV-2026-0001",
        0,
        2,
        now);
  }

  @TestConfiguration
  static class SearchIndexingProcessorTestConfiguration {

    @Bean
    @Primary
    TestSearchIndexClient testSearchIndexClient() {
      return new TestSearchIndexClient();
    }
  }

  static class TestSearchIndexClient implements SearchIndexClient {

    private final AtomicBoolean fail = new AtomicBoolean(false);
    private int indexedCount;

    @Override
    public void index(SearchDocument document) {
      if (fail.get())
        throw new SearchIndexingException("TEST_SEARCH_FAILED", "Test search failure");
      indexedCount++;
    }
  }
}
