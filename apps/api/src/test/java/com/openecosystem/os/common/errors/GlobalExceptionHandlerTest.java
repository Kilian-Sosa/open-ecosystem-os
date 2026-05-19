package com.openecosystem.os.common.errors;

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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    classes = OpenEcosystemApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Test
  void responseStatusExceptionUsesSharedErrorShape() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/__test-error"))
            .header(CorrelationIds.HEADER_NAME, "corr_test_error")
            .GET()
            .build();

    HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue(CorrelationIds.HEADER_NAME))
        .contains("corr_test_error");
    assertThat(response.body()).contains("\"status\":400");
    assertThat(response.body()).contains("\"error\":\"BAD_REQUEST\"");
    assertThat(response.body()).contains("\"message\":\"test failure\"");
    assertThat(response.body()).contains("\"correlationId\":\"corr_test_error\"");
    assertThat(response.body()).contains("/__test-error");
  }

  @RestController
  static class TestController {

    @GetMapping("/__test-error")
    void testError() {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "test failure");
    }
  }
}
