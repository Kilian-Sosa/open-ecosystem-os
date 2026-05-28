package com.openecosystem.os.worker.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.worker.OpenEcosystemWorkerApplication;
import com.openecosystem.os.worker.common.observability.CorrelationIds;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    classes = OpenEcosystemWorkerApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkerHealthControllerTest {

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Test
  void healthReturnsUpAndEchoesCorrelationId() throws Exception {
    HttpResponse<String> response =
        httpClient.send(request("/health", "corr_worker_health"), BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue(CorrelationIds.HEADER_NAME))
        .contains("corr_worker_health");
    assertThat(response.body()).contains("\"status\":\"UP\"");
    assertThat(response.body()).contains("\"service\":\"open-ecosystem-worker\"");
    assertThat(response.body()).contains("\"correlationId\":\"corr_worker_health\"");
  }

  @Test
  void readyReturnsReadyState() throws Exception {
    HttpResponse<String> response = httpClient.send(request("/ready"), BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"READY\"");
    assertThat(response.body()).contains("\"application\":\"ACCEPTING_TRAFFIC\"");
    assertThat(response.body()).contains("correlationId");
  }

  @Test
  void metricsReturnsPrometheusText() throws Exception {
    HttpResponse<String> response = httpClient.send(request("/metrics"), BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("content-type").orElse("")).contains("text/plain");
    assertThat(response.body()).contains("# HELP");
  }

  private HttpRequest request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
  }

  private HttpRequest request(String path, String correlationId) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
        .header(CorrelationIds.HEADER_NAME, correlationId)
        .GET()
        .build();
  }
}
