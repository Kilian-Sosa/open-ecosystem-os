package com.openecosystem.os.worker.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.worker.OpenEcosystemWorkerApplication;
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
  void healthReturnsUp() throws Exception {
    HttpResponse<String> response = httpClient.send(request("/health"), BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
    assertThat(response.body()).contains("\"service\":\"open-ecosystem-worker\"");
  }

  @Test
  void readyReturnsReadyState() throws Exception {
    HttpResponse<String> response = httpClient.send(request("/ready"), BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"READY\"");
    assertThat(response.body()).contains("\"application\":\"ACCEPTING_TRAFFIC\"");
  }

  private HttpRequest request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
  }
}
