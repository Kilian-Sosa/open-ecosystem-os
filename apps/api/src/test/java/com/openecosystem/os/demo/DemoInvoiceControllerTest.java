package com.openecosystem.os.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.OpenEcosystemApiApplication;
import com.openecosystem.os.common.security.CorrelationIds;
import com.openecosystem.os.common.security.PlaceholderAuthenticationContext;
import com.openecosystem.os.drive.storage.FileObjectStorage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    classes = {
      OpenEcosystemApiApplication.class,
      DemoInvoiceControllerTest.DemoInvoiceControllerTestConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoInvoiceControllerTest {

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private FakeFileObjectStorage objectStorage;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from event_consumptions");
    jdbcTemplate.update("delete from search_documents");
    jdbcTemplate.update("delete from demo_invoice_extractions");
    jdbcTemplate.update("delete from knowledge_items");
    jdbcTemplate.update("delete from notifications");
    jdbcTemplate.update("delete from workflow_step_executions");
    jdbcTemplate.update("delete from workflow_executions");
    jdbcTemplate.update("delete from event_outbox");
    jdbcTemplate.update("delete from audit_records");
    jdbcTemplate.update("delete from ocr_jobs");
    jdbcTemplate.update("delete from demo_invoice_runs");
    jdbcTemplate.update("delete from drive_files");
    objectStorage.clear();
  }

  @Test
  void startsFakeInvoiceRunThroughDriveAndResetsDemoData() throws Exception {
    HttpResponse<String> startResponse =
        httpClient.send(
            request("/api/demo/invoice-automation/runs")
                .header(
                    PlaceholderAuthenticationContext.ACTOR_HEADER,
                    PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID)
                .header(
                    PlaceholderAuthenticationContext.WORKSPACE_HEADER,
                    PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID)
                .header(CorrelationIds.HEADER_NAME, "corr_demo_invoice")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            BodyHandlers.ofString());

    assertThat(startResponse.statusCode()).isEqualTo(200);
    assertThat(startResponse.body()).contains("\"correlationId\":\"corr_demo_invoice\"");
    assertThat(startResponse.body()).contains("\"status\":\"processing\"");
    assertThat(count("demo_invoice_runs")).isEqualTo(1);
    assertThat(count("drive_files")).isEqualTo(1);
    assertThat(count("audit_records")).isEqualTo(1);
    assertThat(outboxCount("FileUploaded")).isEqualTo(1);
    assertThat(objectStorage.objectCount()).isEqualTo(1);

    Map<String, Object> event = jdbcTemplate.queryForMap("select * from event_outbox limit 1");
    assertThat(event.get("envelope_json")).asString().doesNotContain("Fake_Test_Invoice");

    HttpResponse<String> resetResponse =
        httpClient.send(
            request("/api/demo/invoice-automation/reset")
                .header(
                    PlaceholderAuthenticationContext.WORKSPACE_HEADER,
                    PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            BodyHandlers.ofString());

    assertThat(resetResponse.statusCode()).isEqualTo(200);
    assertThat(resetResponse.body()).contains("\"runsDeleted\":1");
    assertThat(count("demo_invoice_runs")).isZero();
    assertThat(count("drive_files")).isZero();
    assertThat(objectStorage.objectCount()).isZero();
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
  }

  private int count(String table) {
    Integer count = jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    return count == null ? 0 : count;
  }

  private int outboxCount(String eventType) {
    Integer count =
        jdbcTemplate.queryForObject(
            "select count(*) from event_outbox where event_type = ?", Integer.class, eventType);
    return count == null ? 0 : count;
  }

  @TestConfiguration
  static class DemoInvoiceControllerTestConfiguration {

    @Bean
    @Primary
    FakeFileObjectStorage fakeFileObjectStorage() {
      return new FakeFileObjectStorage();
    }
  }

  static class FakeFileObjectStorage implements FileObjectStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void putEncryptedObject(
        String storageKey, byte[] encryptedContent, String originalContentType, String contentIv) {
      objects.put(storageKey, encryptedContent);
    }

    @Override
    public void deleteObjectIfExists(String storageKey) {
      objects.remove(storageKey);
    }

    int objectCount() {
      return objects.size();
    }

    void clear() {
      objects.clear();
    }
  }
}
