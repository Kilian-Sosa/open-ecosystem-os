package com.openecosystem.os.worker.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MeilisearchIndexClient implements SearchIndexClient {
  private final SearchProperties properties;
  private final ObjectMapper mapper;
  private final HttpClient http;

  public MeilisearchIndexClient(SearchProperties properties, ObjectMapper mapper) {
    this.properties = properties;
    this.mapper = mapper;
    this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  }

  @Override
  public void index(SearchDocument document) {
    submitUpsert(document, UUID.randomUUID().toString());
  }

  @Override
  public long submitUpsert(SearchDocument document, String operationId) {
    try {
      String body = mapper.writeValueAsString(List.of(remoteDocument(document)));
      return submit(
          request(documentsPath() + "?primaryKey=id&customMetadata=" + encode(operationId))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build());
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new SearchSubmissionException(false);
    }
  }

  @Override
  public long submitRemoval(String documentId, String operationId) {
    try {
      return submit(
          request(documentsPath() + "/delete-batch?customMetadata=" + encode(operationId))
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      mapper.writeValueAsString(List.of(documentId))))
              .build());
    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
      throw new SearchSubmissionException(false);
    }
  }

  private long submit(HttpRequest request) {
    try {
      var response = http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new SearchSubmissionException(
            response.statusCode() >= 500 || response.statusCode() == 408);
      JsonNode body = mapper.readTree(response.body());
      JsonNode id = body == null ? null : body.get("taskUid");
      if (id == null || !id.isIntegralNumber() || id.asLong() < 0)
        throw new SearchSubmissionException(true);
      return id.asLong();
    } catch (ConnectException | HttpConnectTimeoutException exception) {
      throw new SearchSubmissionException(false);
    } catch (IOException exception) {
      throw new SearchSubmissionException(true);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new SearchSubmissionException(true);
    }
  }

  @Override
  public TaskState taskState(long taskUid) {
    String state = get("/tasks/" + taskUid).path("status").asText();
    return switch (state) {
      case "succeeded" -> TaskState.SUCCEEDED;
      case "failed", "canceled" -> TaskState.FAILED;
      case "enqueued", "processing" -> TaskState.PENDING;
      default -> throw unavailable();
    };
  }

  @Override
  public TaskLookup findTask(String operationId, Instant startedAt, Long cursor) {
    String path =
        "/tasks?indexUids="
            + encode(properties.indexName())
            + "&limit=100"
            + (cursor == null ? "" : "&from=" + cursor);
    JsonNode result = get(path);
    if (!result.path("results").isArray()) throw unavailable();
    for (JsonNode task : result.path("results"))
      if (operationId.equals(task.path("customMetadata").asText())) {
        if (!task.path("uid").isIntegralNumber()) throw unavailable();
        return new TaskLookup(OptionalLong.of(task.path("uid").asLong()), null);
      }
    JsonNode next = result.get("next");
    return new TaskLookup(
        OptionalLong.empty(), next != null && next.isIntegralNumber() ? next.asLong() : null);
  }

  @Override
  public boolean isAbsent(String documentId) {
    var response = read(documentsPath() + "/" + encode(documentId));
    if (response.statusCode() == 200) return false;
    if (response.statusCode() == 404) {
      String code = parse(response.body()).path("code").asText();
      if (code.equals("document_not_found") || code.equals("index_not_found")) return true;
    }
    throw unavailable();
  }

  private JsonNode get(String path) {
    var response = read(path);
    if (response.statusCode() < 200 || response.statusCode() >= 300) throw unavailable();
    return parse(response.body());
  }

  private JsonNode parse(String body) {
    try {
      return mapper.readTree(body);
    } catch (IOException exception) {
      throw unavailable();
    }
  }

  private HttpResponse<String> read(String path) {
    try {
      return http.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
    } catch (IOException exception) {
      throw unavailable();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw unavailable();
    }
  }

  private HttpRequest.Builder request(String path) {
    String host = properties.meilisearchHost().replaceAll("/+$", "");
    return HttpRequest.newBuilder(URI.create(host + path))
        .timeout(Duration.ofSeconds(5))
        .header("Authorization", "Bearer " + properties.meilisearchMasterKey());
  }

  private String documentsPath() {
    return "/indexes/" + encode(properties.indexName()) + "/documents";
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  private static SearchIndexingException unavailable() {
    return new SearchIndexingException(
        "MEILISEARCH_UNAVAILABLE", "Remote search outcome could not be verified");
  }

  private Map<String, Object> remoteDocument(SearchDocument d) {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("id", d.searchDocumentId());
    value.put("workspaceId", d.workspaceId());
    value.put("sourceType", d.sourceType());
    value.put("sourceId", d.sourceId());
    value.put("title", d.title());
    value.put("summary", d.summary());
    value.put("content", d.content());
    value.put("resourceHref", d.resourceHref());
    value.put("correlationId", d.correlationId());
    value.put("createdAt", d.createdAt().toString());
    value.put("metadata", d.metadata());
    return value;
  }
}
