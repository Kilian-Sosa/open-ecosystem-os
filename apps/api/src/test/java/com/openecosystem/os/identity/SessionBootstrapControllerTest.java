package com.openecosystem.os.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.OpenEcosystemApiApplication;
import com.openecosystem.os.common.security.PlaceholderAuthenticationContext;
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
class SessionBootstrapControllerTest {

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Test
  void returnsSeededSessionBootstrapForDefaultWorkspace() throws Exception {
    HttpResponse<String> response =
        httpClient.send(request("/api/session/bootstrap").GET().build(), BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"authMode\":\"seeded_dev\"");
    assertThat(response.body()).contains("\"actorId\":\"usr_dev_placeholder\"");
    assertThat(response.body()).contains("\"displayName\":\"Demo Admin\"");
    assertThat(response.body()).contains("\"workspaceId\":\"wrk_dev_placeholder\"");
    assertThat(response.body()).contains("\"name\":\"Open Ecosystem Demo Workspace\"");
    assertThat(response.body()).contains("\"WORKSPACE_ADMIN\"");
    assertThat(response.body()).contains("\"DEVELOPMENT_PLACEHOLDER\"");
  }

  @Test
  void rejectsHeadersWithoutSeededMembership() throws Exception {
    HttpResponse<String> response =
        httpClient.send(
            request("/api/session/bootstrap")
                .header(PlaceholderAuthenticationContext.ACTOR_HEADER, "usr_unknown")
                .header(
                    PlaceholderAuthenticationContext.WORKSPACE_HEADER,
                    PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID)
                .GET()
                .build(),
            BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.body()).contains("\"error\":\"FORBIDDEN\"");
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
  }
}
