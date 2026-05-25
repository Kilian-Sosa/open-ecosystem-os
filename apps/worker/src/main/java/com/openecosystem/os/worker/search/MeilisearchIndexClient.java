package com.openecosystem.os.worker.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MeilisearchIndexClient implements SearchIndexClient {

  private final SearchProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public MeilisearchIndexClient(SearchProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  }

  @Override
  public void index(SearchDocument document) {
    try {
      String body = objectMapper.writeValueAsString(List.of(meilisearchDocument(document)));
      HttpRequest request =
          HttpRequest.newBuilder(documentsUri())
              .timeout(Duration.ofSeconds(5))
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + properties.meilisearchMasterKey())
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new SearchIndexingException(
            "MEILISEARCH_INDEX_FAILED",
            "Meilisearch indexing failed with status " + response.statusCode());
    } catch (JsonProcessingException exception) {
      throw new SearchIndexingException(
          "SEARCH_DOCUMENT_SERIALIZATION_FAILED", "Search document could not be serialized");
    } catch (IOException exception) {
      throw new SearchIndexingException("MEILISEARCH_UNAVAILABLE", "Meilisearch is unavailable");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new SearchIndexingException(
          "MEILISEARCH_INTERRUPTED", "Meilisearch request interrupted");
    }
  }

  private Map<String, Object> meilisearchDocument(SearchDocument document) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", document.searchDocumentId());
    value.put("workspaceId", document.workspaceId());
    value.put("sourceType", document.sourceType());
    value.put("sourceId", document.sourceId());
    value.put("title", document.title());
    value.put("summary", document.summary());
    value.put("content", document.content());
    value.put("resourceHref", document.resourceHref());
    value.put("correlationId", document.correlationId());
    value.put("createdAt", document.createdAt().toString());
    value.put("metadata", document.metadata());
    return value;
  }

  private URI documentsUri() {
    String host = properties.meilisearchHost();
    String trimmedHost = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    return URI.create(trimmedHost + "/indexes/" + properties.indexName() + "/documents");
  }
}
