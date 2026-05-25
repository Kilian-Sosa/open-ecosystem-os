package com.openecosystem.os.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

  private final AuthenticationContext authenticationContext;
  private final JdbcSearchDocumentRepository searchDocumentRepository;
  private final MeilisearchSearchClient meilisearchSearchClient;
  private final ObjectMapper objectMapper;

  public SearchService(
      AuthenticationContext authenticationContext,
      JdbcSearchDocumentRepository searchDocumentRepository,
      MeilisearchSearchClient meilisearchSearchClient,
      ObjectMapper objectMapper) {
    this.authenticationContext = authenticationContext;
    this.searchDocumentRepository = searchDocumentRepository;
    this.meilisearchSearchClient = meilisearchSearchClient;
    this.objectMapper = objectMapper;
  }

  public SearchResponse search(String query) {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    String normalized = query == null ? "" : query.trim();
    List<SearchResultResponse> localResults =
        searchDocumentRepository.searchLocal(principal.workspaceId(), normalized).stream()
            .map(this::toResult)
            .toList();
    try {
      List<SearchResultResponse> meilisearchResults =
          meilisearchSearchClient.search(principal.workspaceId(), normalized);
      List<SearchResultResponse> mergedResults = mergeResults(meilisearchResults, localResults);
      String backend =
          mergedResults.size() > meilisearchResults.size()
              ? "meilisearch+postgres-local"
              : "meilisearch";
      return new SearchResponse(normalized, backend, mergedResults);
    } catch (RuntimeException | java.io.IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return new SearchResponse(normalized, "postgres-fallback", localResults);
    }
  }

  private List<SearchResultResponse> mergeResults(
      List<SearchResultResponse> primaryResults, List<SearchResultResponse> localResults) {
    Map<String, SearchResultResponse> merged = new LinkedHashMap<>();
    for (SearchResultResponse result : primaryResults) {
      merged.put(result.id(), result);
    }
    for (SearchResultResponse result : localResults) {
      merged.putIfAbsent(result.id(), result);
    }
    return List.copyOf(merged.values());
  }

  private SearchResultResponse toResult(SearchDocument document) {
    return new SearchResultResponse(
        document.searchDocumentId(),
        document.sourceType(),
        document.sourceId(),
        document.title(),
        document.summary(),
        document.resourceHref(),
        document.correlationId(),
        document.status().value(),
        metadata(document),
        document.createdAt());
  }

  private Map<String, Object> metadata(SearchDocument document) {
    try {
      return objectMapper.readValue(
          document.metadata().toString(), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (JsonProcessingException exception) {
      return Map.of();
    }
  }
}
