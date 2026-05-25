package com.openecosystem.os.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SearchServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private JdbcTemplate jdbcTemplate;
  private JdbcSearchDocumentRepository repository;
  private FakeMeilisearchSearchClient meilisearchSearchClient;
  private SearchService searchService;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource("jdbc:h2:mem:search_service;DB_CLOSE_DELAY=-1", "sa", "");
    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("drop table if exists search_documents");
    jdbcTemplate.execute(
        """
        create table search_documents (
          search_document_id varchar(64) primary key,
          workspace_id varchar(64) not null,
          source_type varchar(80) not null,
          source_id varchar(64) not null,
          title varchar(255) not null,
          summary varchar(1000) not null,
          content clob not null,
          resource_href varchar(255) not null,
          correlation_id varchar(128) not null,
          status varchar(32) not null,
          attempt_count int not null,
          max_attempts int not null,
          failure_code varchar(120),
          failure_message varchar(500),
          metadata_json clob not null,
          created_at timestamp not null,
          updated_at timestamp not null,
          indexed_at timestamp,
          failed_at timestamp
        )
        """);
    repository = new JdbcSearchDocumentRepository(jdbcTemplate, objectMapper);
    meilisearchSearchClient = new FakeMeilisearchSearchClient(objectMapper);
    searchService =
        new SearchService(
            authenticationContext(), repository, meilisearchSearchClient, objectMapper);
  }

  @Test
  void searchIncludesLocalDocumentWhenMeilisearchReturnsNoHits() {
    repository.save(searchDocument(SearchDocumentStatus.INDEXING));
    meilisearchSearchClient.results = List.of();

    SearchResponse response = searchService.search("TEST-INV-2026-0001");

    assertThat(response.backend()).isEqualTo("meilisearch+postgres-local");
    assertThat(response.results()).hasSize(1);
    assertThat(response.results().getFirst().id()).isEqualTo("srch_test_invoice");
    assertThat(response.results().getFirst().status()).isEqualTo("indexing");
  }

  @Test
  void searchFallsBackToLocalDocumentsWhenMeilisearchFails() {
    repository.save(searchDocument(SearchDocumentStatus.INDEXED));
    meilisearchSearchClient.failure = new IOException("meilisearch unavailable");

    SearchResponse response = searchService.search("TEST-INV-2026-0001");

    assertThat(response.backend()).isEqualTo("postgres-fallback");
    assertThat(response.results()).hasSize(1);
    assertThat(response.results().getFirst().id()).isEqualTo("srch_test_invoice");
  }

  private SearchDocument searchDocument(SearchDocumentStatus status) {
    ObjectNode metadata = objectMapper.createObjectNode();
    metadata.put("invoiceNumber", "TEST-INV-2026-0001");
    metadata.put("isTestData", true);
    Instant now = Instant.parse("2026-05-25T10:00:00Z");
    return new SearchDocument(
        "srch_test_invoice",
        "wrk_test",
        "demo_invoice_extraction",
        "dinv_test_invoice",
        "Fake/test invoice TEST-INV-2026-0001",
        "Seeded fake/test invoice search document.",
        "TEST-INV-2026-0001 Demo Supplies fake/test invoice",
        "/app/demo/invoice-automation",
        "corr_test_invoice",
        status,
        1,
        3,
        null,
        null,
        metadata,
        now,
        now,
        status == SearchDocumentStatus.INDEXED ? now : null,
        null);
  }

  private AuthenticationContext authenticationContext() {
    return () -> new AuthenticatedPrincipal("usr_test", "wrk_test", Set.of("admin"), true);
  }

  private static class FakeMeilisearchSearchClient extends MeilisearchSearchClient {

    private List<SearchResultResponse> results = List.of();
    private IOException failure;

    FakeMeilisearchSearchClient(ObjectMapper objectMapper) {
      super(new SearchProperties("http://localhost:7700", "test", "test", 3), objectMapper);
    }

    @Override
    public List<SearchResultResponse> search(String workspaceId, String query)
        throws IOException, InterruptedException {
      if (failure != null)
        throw failure;
      return results;
    }
  }
}
