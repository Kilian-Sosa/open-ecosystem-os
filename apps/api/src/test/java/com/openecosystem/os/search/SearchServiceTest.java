package com.openecosystem.os.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.common.security.AuthorizationDecision;
import com.openecosystem.os.common.security.ResourceAction;
import com.openecosystem.os.common.security.ResourceAuthorizationService;
import com.openecosystem.os.common.security.ResourceType;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
  private RecordingResourceAuthorizationService authorizationService;
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
    authorizationService = new RecordingResourceAuthorizationService();
    authorizationService.allowedFileIds = Set.of("file_test_invoice");
    searchService =
        new SearchService(
            authenticationContext(),
            repository,
            meilisearchSearchClient,
            authorizationService,
            objectMapper);
  }

  @Test
  void searchIncludesLocalDocumentWhenMeilisearchReturnsNoHits() {
    repository.save(searchDocument(SearchDocumentStatus.INDEXING, true));
    meilisearchSearchClient.results = List.of();

    SearchResponse response = searchService.search("TEST-INV-2026-0001");

    assertThat(response.backend()).isEqualTo("meilisearch+postgres-local");
    assertThat(response.results()).hasSize(1);
    assertThat(response.results().getFirst().id()).isEqualTo("srch_test_invoice");
    assertThat(response.results().getFirst().status()).isEqualTo("indexing");
  }

  @Test
  void searchFallsBackToLocalDocumentsWhenMeilisearchFails() {
    repository.save(searchDocument(SearchDocumentStatus.INDEXED, true));
    meilisearchSearchClient.failure = new IOException("meilisearch unavailable");

    SearchResponse response = searchService.search("TEST-INV-2026-0001");

    assertThat(response.backend()).isEqualTo("postgres-fallback");
    assertThat(response.results()).hasSize(1);
    assertThat(response.results().getFirst().id()).isEqualTo("srch_test_invoice");
  }

  @Test
  void searchOmitsLocalDriveDerivedCandidatesWithoutSourceFileLineage() {
    repository.save(searchDocument(SearchDocumentStatus.INDEXED, false));
    meilisearchSearchClient.results = List.of();

    SearchResponse response = searchService.search("TEST-INV-2026-0001");

    assertThat(response.results()).isEmpty();
  }

  @Test
  void searchRetainsLocallyAuthorizedOwnerWorkspaceAndExplicitGrantCandidates() {
    repository.save(searchDocument("srch_owner", "file_owner", "Owner invoice"));
    repository.save(searchDocument("srch_workspace", "file_workspace", "Workspace invoice"));
    repository.save(searchDocument("srch_grant", "file_grant", "Granted invoice"));
    authorizationService.allowedFileIds = Set.of("file_owner", "file_workspace", "file_grant");

    SearchResponse response = searchService.search("invoice");

    assertThat(response.results())
        .extracting(SearchResultResponse::id)
        .containsExactlyInAnyOrder("srch_owner", "srch_workspace", "srch_grant");
    assertThat(authorizationService.requestedFileIds)
        .containsExactly(Set.of("file_owner", "file_workspace", "file_grant"));
  }

  @Test
  void searchOmitsLocalCandidatesForDeniedSourceFiles() {
    repository.save(searchDocument("srch_private", "file_private", "Private invoice"));
    repository.save(searchDocument("srch_revoked", "file_revoked", "Revoked invoice"));
    repository.save(searchDocument("srch_foreign", "file_foreign", "Foreign invoice"));
    repository.save(searchDocument("srch_inactive", "file_inactive", "Inactive invoice"));
    repository.save(
        searchDocument("srch_missing_source", "file_missing", "Missing source invoice"));
    authorizationService.allowedFileIds = Set.of();

    SearchResponse response = searchService.search("invoice");

    assertThat(response.results()).isEmpty();
  }

  @Test
  void searchOmitsBlankAndNonStringLocalLineageBeforeAuthorization() {
    repository.save(searchDocument("srch_blank", "   ", "Blank lineage invoice"));
    repository.save(searchDocument("srch_non_string", 7, "Non-string lineage invoice"));
    repository.save(searchDocument("srch_allowed", "file_allowed", "Allowed invoice"));
    authorizationService.allowedFileIds = Set.of("file_allowed");

    SearchResponse response = searchService.search("invoice");

    assertThat(response.results())
        .extracting(SearchResultResponse::id)
        .containsExactly("srch_allowed");
    assertThat(authorizationService.requestedFileIds).containsExactly(Set.of("file_allowed"));
  }

  @Test
  void searchFiltersMeilisearchCandidatesBeforeMergeAndPublicBackendLabel() {
    repository.save(
        searchDocument("srch_local_denied", "file_local_denied", "Denied local invoice"));
    meilisearchSearchClient.results =
        List.of(
            remoteResult("srch_remote_allowed", "file_remote_allowed", "Allowed remote invoice"),
            remoteResult("srch_remote_denied", "file_remote_denied", "Denied remote invoice"));
    authorizationService.allowedFileIds = Set.of("file_remote_allowed");

    SearchResponse response = searchService.search("invoice");

    assertThat(response.backend()).isEqualTo("meilisearch");
    assertThat(response.results())
        .extracting(SearchResultResponse::id)
        .containsExactly("srch_remote_allowed");
    assertThat(response.results().toString()).doesNotContain("Denied local", "Denied remote");
    assertThat(authorizationService.requestedFileIds)
        .containsExactly(
            Set.of("file_local_denied"), Set.of("file_remote_allowed", "file_remote_denied"));
  }

  @Test
  void searchFiltersPostgresFallbackBySourceAuthorization() {
    repository.save(searchDocument("srch_allowed", "file_allowed", "Allowed fallback invoice"));
    repository.save(searchDocument("srch_denied", "file_denied", "Denied fallback invoice"));
    meilisearchSearchClient.failure = new IOException("meilisearch unavailable");
    authorizationService.allowedFileIds = Set.of("file_allowed");

    SearchResponse response = searchService.search("invoice");

    assertThat(response.backend()).isEqualTo("postgres-fallback");
    assertThat(response.results())
        .extracting(SearchResultResponse::id)
        .containsExactly("srch_allowed");
  }

  @Test
  void searchOmitsMissingBlankAndNonStringMeilisearchLineageBeforeAuthorization() {
    meilisearchSearchClient.results =
        List.of(
            remoteResult("srch_remote_missing", null, "Missing remote invoice"),
            remoteResult("srch_remote_blank", " ", "Blank remote invoice"),
            remoteResult("srch_remote_non_string", 7, "Non-string remote invoice"),
            remoteResult("srch_remote_allowed", "file_remote_allowed", "Allowed remote invoice"));
    authorizationService.allowedFileIds = Set.of("file_remote_allowed");

    SearchResponse response = searchService.search("invoice");

    assertThat(response.results())
        .extracting(SearchResultResponse::id)
        .containsExactly("srch_remote_allowed");
    assertThat(authorizationService.requestedFileIds)
        .containsExactly(Set.of("file_remote_allowed"));
  }

  @Test
  void searchDoesNotLetDeniedRemoteCandidateWinDeduplicationAgainstAuthorizedLocalCandidate() {
    repository.save(searchDocument("srch_shared", "file_local_allowed", "Allowed local invoice"));
    meilisearchSearchClient.results =
        List.of(remoteResult("srch_shared", "file_remote_denied", "Denied remote invoice"));
    authorizationService.allowedFileIds = Set.of("file_local_allowed");

    SearchResponse response = searchService.search("invoice");

    assertThat(response.backend()).isEqualTo("meilisearch+postgres-local");
    assertThat(response.results())
        .extracting(SearchResultResponse::title)
        .containsExactly("Allowed local invoice");
  }

  @Test
  void searchAuthorizesDistinctLocalSourceFileIdsOnlyOnce() {
    repository.save(searchDocument("srch_first", "file_shared", "First shared invoice"));
    repository.save(searchDocument("srch_second", "file_shared", "Second shared invoice"));
    authorizationService.allowedFileIds = Set.of("file_shared");

    searchService.search("invoice");

    assertThat(authorizationService.requestedFileIds).containsExactly(Set.of("file_shared"));
  }

  private SearchDocument searchDocument(SearchDocumentStatus status, boolean withFileLineage) {
    return searchDocument(
        "srch_test_invoice",
        withFileLineage ? "file_test_invoice" : null,
        "Test invoice TEST-INV-2026-0001",
        status);
  }

  private SearchDocument searchDocument(String id, Object fileId, String title) {
    return searchDocument(id, fileId, title, SearchDocumentStatus.INDEXED);
  }

  private SearchDocument searchDocument(
      String id, Object fileId, String title, SearchDocumentStatus status) {
    ObjectNode metadata = objectMapper.createObjectNode();
    metadata.put("invoice_number", "TEST-INV-2026-0001");
    metadata.put("isTestData", true);
    if (fileId != null) metadata.set("fileId", objectMapper.valueToTree(fileId));
    Instant now = Instant.parse("2026-05-25T10:00:00Z");
    return new SearchDocument(
        id,
        "wrk_test",
        "invoice_extraction",
        "invx_" + id,
        title,
        "Test-only invoice extraction fixture.",
        "TEST-INV-2026-0001 Test-only Supplier",
        "/app/media?jobId=ocr_test_invoice",
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

  private SearchCandidate remoteResult(String id, Object fileId, String title) {
    Map<String, Object> metadata = new java.util.LinkedHashMap<>();
    if (fileId != null) metadata.put("fileId", fileId);
    return new SearchCandidate(
        id,
        "invoice_extraction",
        "invx_" + id,
        title,
        "Test-only invoice extraction fixture.",
        "/app/media?jobId=ocr_test_invoice",
        "corr_test_invoice",
        "indexed",
        metadata,
        Instant.parse("2026-05-25T10:00:00Z"));
  }

  private AuthenticationContext authenticationContext() {
    return () -> new AuthenticatedPrincipal("usr_test", "wrk_test", Set.of("admin"), true);
  }

  private static class FakeMeilisearchSearchClient extends MeilisearchSearchClient {

    private List<SearchCandidate> results = List.of();
    private IOException failure;

    FakeMeilisearchSearchClient(ObjectMapper objectMapper) {
      super(new SearchProperties("http://localhost:7700", "test", "test", 3), objectMapper);
    }

    @Override
    public List<SearchCandidate> search(String workspaceId, String query)
        throws IOException, InterruptedException {
      if (failure != null) throw failure;
      return results;
    }
  }

  private static class RecordingResourceAuthorizationService
      implements ResourceAuthorizationService {

    private Set<String> allowedFileIds = Set.of();
    private final List<Set<String>> requestedFileIds = new java.util.ArrayList<>();

    @Override
    public AuthorizationDecision decide(
        AuthenticatedPrincipal principal,
        String workspaceId,
        ResourceType resourceType,
        String resourceId,
        ResourceAction action) {
      throw new UnsupportedOperationException("Search uses batch authorization only");
    }

    @Override
    public Set<String> allowedResourceIds(
        AuthenticatedPrincipal principal,
        String workspaceId,
        ResourceType resourceType,
        Collection<String> resourceIds,
        ResourceAction action) {
      requestedFileIds.add(Set.copyOf(new LinkedHashSet<>(resourceIds)));
      return resourceIds.stream()
          .filter(allowedFileIds::contains)
          .collect(java.util.stream.Collectors.toSet());
    }
  }
}
