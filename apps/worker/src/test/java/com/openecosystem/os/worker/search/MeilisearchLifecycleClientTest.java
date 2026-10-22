package com.openecosystem.os.worker.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MeilisearchLifecycleClientTest {
  @Container
  static final GenericContainer<?> MEILI =
      new GenericContainer<>("getmeili/meilisearch:latest")
          .withEnv("MEILI_ENV", "development")
          .withExposedPorts(7700)
          .waitingFor(Wait.forHttp("/health").forPort(7700));

  @Test
  void acceptedTaskCanBeDiscoveredThenRemovalProvesAbsence() throws Exception {
    String index = "synthetic_" + UUID.randomUUID().toString().replace("-", "");
    var mapper = new ObjectMapper();
    var client =
        new MeilisearchIndexClient(
            new SearchProperties(
                "http://" + MEILI.getHost() + ":" + MEILI.getMappedPort(7700),
                "synthetic_test_key",
                index,
                3),
            mapper);
    String operation = UUID.randomUUID().toString();
    var now = Instant.now();
    var doc =
        new SearchDocument(
            "synthetic_doc",
            "wrk_test",
            "ocr_result",
            "ocr_test",
            "Synthetic",
            "Synthetic",
            "Synthetic content",
            "/app/drive",
            "corr_test",
            SearchDocumentStatus.PENDING,
            0,
            3,
            null,
            null,
            mapper.readTree("{\"fileId\":\"file_test\"}"),
            now,
            now,
            null,
            null);
    long upsert = client.submitUpsert(doc, operation);
    assertThat(client.findTask(operation, now, null).taskUid()).hasValue(upsert);
    await(client, upsert);
    assertThat(client.isAbsent(doc.searchDocumentId())).isFalse();
    long removal = client.submitRemoval(doc.searchDocumentId(), UUID.randomUUID().toString());
    await(client, removal);
    assertThat(client.isAbsent(doc.searchDocumentId())).isTrue();
  }

  @Test
  void gatewayFailureIsAmbiguousInsteadOfAssumedRejection() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          byte[] body =
              "{\"code\":\"gateway_timeout\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(502, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      var client =
          new MeilisearchIndexClient(
              new SearchProperties(
                  "http://127.0.0.1:" + server.getAddress().getPort(),
                  "synthetic_key",
                  "synthetic_index",
                  3),
              new ObjectMapper());
      assertThatThrownBy(() -> client.submitRemoval("synthetic_doc", "synthetic_operation"))
          .isInstanceOfSatisfying(
              SearchSubmissionException.class,
              exception -> assertThat(exception.ambiguous()).isTrue());
    } finally {
      server.stop(0);
    }
  }

  private static void await(SearchIndexClient client, long uid) throws Exception {
    long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
    while (System.nanoTime() < deadline) {
      var state = client.taskState(uid);
      if (state != SearchIndexClient.TaskState.PENDING) {
        assertThat(state).isEqualTo(SearchIndexClient.TaskState.SUCCEEDED);
        return;
      }
      Thread.sleep(20);
    }
    throw new AssertionError("Synthetic remote task did not complete");
  }
}
