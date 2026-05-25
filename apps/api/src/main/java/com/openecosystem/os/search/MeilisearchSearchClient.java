package com.openecosystem.os.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MeilisearchSearchClient {

  private final SearchProperties properties;
  private final ObjectMapper objectMapper;
  private final HttpClient httpClient;

  public MeilisearchSearchClient(SearchProperties properties, ObjectMapper objectMapper) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  }

  public List<SearchResultResponse> search(String workspaceId, String query)
      throws IOException, InterruptedException {
    String body = objectMapper.writeValueAsString(Map.of("q", query == null ? "" : query));
    HttpRequest request =
        HttpRequest.newBuilder(searchUri())
            .timeout(Duration.ofSeconds(5))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + properties.meilisearchMasterKey())
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300)
      throw new IOException("Meilisearch search failed with status " + response.statusCode());

    JsonNode hits = objectMapper.readTree(response.body()).path("hits");
    List<SearchResultResponse> results = new ArrayList<>();
    if (!hits.isArray())
      return results;

    for (JsonNode hit : hits) {
      if (!workspaceId.equals(hit.path("workspaceId").asText()))
        continue;
      results.add(toResult(hit));
    }
    return results;
  }

  private SearchResultResponse toResult(JsonNode hit) {
    return new SearchResultResponse(
        hit.path("id").asText(),
        hit.path("sourceType").asText(),
        hit.path("sourceId").asText(),
        hit.path("title").asText(),
        hit.path("summary").asText(),
        hit.path("resourceHref").asText(),
        hit.path("correlationId").asText(),
        "indexed",
        metadata(hit.path("metadata")),
        Instant.parse(hit.path("createdAt").asText()));
  }

  private Map<String, Object> metadata(JsonNode metadata) {
    if (!metadata.isObject())
      return Map.of();
    return objectMapper.convertValue(
        metadata, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  private URI searchUri() {
    String host = properties.meilisearchHost();
    String trimmedHost = host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    return URI.create(trimmedHost + "/indexes/" + properties.indexName() + "/search");
  }
}
