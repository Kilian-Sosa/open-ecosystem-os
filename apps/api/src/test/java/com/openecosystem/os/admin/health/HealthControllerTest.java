package com.openecosystem.os.admin.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.OpenEcosystemApiApplication;
import com.openecosystem.os.common.security.CorrelationIds;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(
    classes = OpenEcosystemApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthControllerTest {

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Test
  void healthReturnsUpAndEchoesCorrelationId() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(uri("/health"))
            .header(CorrelationIds.HEADER_NAME, "corr_test_health")
            .GET()
            .build();

    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue(CorrelationIds.HEADER_NAME))
        .contains("corr_test_health");
    assertThat(response.body()).contains("\"status\":\"UP\"");
    assertThat(response.body()).contains("\"correlationId\":\"corr_test_health\"");
  }

  @Test
  void readyReturnsReadyState() throws Exception {
    HttpRequest request = HttpRequest.newBuilder(uri("/ready")).GET().build();

    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"READY\"");
    assertThat(response.body()).contains("\"application\":\"ACCEPTING_TRAFFIC\"");
    assertThat(response.body()).contains("correlationId");
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }
}
