package com.openecosystem.os.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.common.security.ResourceAction;
import com.openecosystem.os.common.security.ResourceAuthorizationService;
import com.openecosystem.os.common.security.ResourceType;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

  private final AuthenticationContext authenticationContext;
  private final JdbcSearchDocumentRepository searchDocumentRepository;
  private final MeilisearchSearchClient meilisearchSearchClient;
  private final ResourceAuthorizationService authorizationService;
  private final ObjectMapper objectMapper;

  public SearchService(
      AuthenticationContext authenticationContext,
      JdbcSearchDocumentRepository searchDocumentRepository,
      MeilisearchSearchClient meilisearchSearchClient,
      ResourceAuthorizationService authorizationService,
      ObjectMapper objectMapper) {
    this.authenticationContext = authenticationContext;
    this.searchDocumentRepository = searchDocumentRepository;
    this.meilisearchSearchClient = meilisearchSearchClient;
    this.authorizationService = authorizationService;
    this.objectMapper = objectMapper;
  }

  public SearchResponse search(String query) {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    String normalized = query == null ? "" : query.trim();
    List<SearchCandidate> localResults =
        searchDocumentRepository.searchLocal(principal.workspaceId(), normalized).stream()
            .map(this::toCandidate)
            .toList();
    List<SearchCandidate> authorizedLocalResults = filterAuthorized(principal, localResults);
    try {
      List<SearchCandidate> meilisearchResults =
          filterAuthorized(
              principal, meilisearchSearchClient.search(principal.workspaceId(), normalized));
      List<SearchCandidate> mergedResults =
          mergeResults(meilisearchResults, authorizedLocalResults);
      String backend =
          mergedResults.size() > meilisearchResults.size()
              ? "meilisearch+postgres-local"
              : "meilisearch";
      return new SearchResponse(
          normalized, backend, mergedResults.stream().map(this::toResult).toList());
    } catch (RuntimeException | java.io.IOException | InterruptedException exception) {
      if (exception instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return new SearchResponse(
          normalized,
          "postgres-fallback",
          authorizedLocalResults.stream().map(this::toResult).toList());
    }
  }

  private List<SearchCandidate> mergeResults(
      List<SearchCandidate> primaryResults, List<SearchCandidate> localResults) {
    Map<String, SearchCandidate> merged = new LinkedHashMap<>();
    for (SearchCandidate result : primaryResults) {
      merged.put(result.id(), result);
    }
    for (SearchCandidate result : localResults) {
      merged.putIfAbsent(result.id(), result);
    }
    return List.copyOf(merged.values());
  }

  private List<SearchCandidate> filterAuthorized(
      AuthenticatedPrincipal principal, List<SearchCandidate> candidates) {
    Set<String> sourceFileIds = new LinkedHashSet<>();
    for (SearchCandidate candidate : candidates) {
      sourceFileId(candidate).ifPresent(sourceFileIds::add);
    }
    if (sourceFileIds.isEmpty()) return List.of();

    Set<String> allowedFileIds =
        authorizationService.allowedResourceIds(
            principal,
            principal.workspaceId(),
            ResourceType.FILE,
            sourceFileIds,
            ResourceAction.VIEW);
    return candidates.stream()
        .filter(candidate -> sourceFileId(candidate).filter(allowedFileIds::contains).isPresent())
        .toList();
  }

  private Optional<String> sourceFileId(SearchCandidate candidate) {
    Object fileId = candidate.metadata().get("fileId");
    if (!(fileId instanceof String value) || value.isBlank()) return Optional.empty();
    return Optional.of(value);
  }

  private SearchCandidate toCandidate(SearchDocument document) {
    return new SearchCandidate(
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

  private SearchResultResponse toResult(SearchCandidate candidate) {
    return new SearchResultResponse(
        candidate.id(),
        candidate.sourceType(),
        candidate.sourceId(),
        candidate.title(),
        candidate.summary(),
        candidate.resourceHref(),
        candidate.correlationId(),
        candidate.status(),
        candidate.metadata(),
        candidate.createdAt());
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
