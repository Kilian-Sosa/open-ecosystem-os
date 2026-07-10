package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.OpenEcosystemApiApplication;
import com.openecosystem.os.common.security.CorrelationIds;
import com.openecosystem.os.common.security.PlaceholderAuthenticationContext;
import com.openecosystem.os.drive.storage.FileObjectStorage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
      DriveFileControllerTest.DriveFileControllerTestConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DriveFileControllerTest {

  private static final String BOUNDARY = "----open-ecosystem-test-boundary";

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private FakeFileObjectStorage objectStorage;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from event_outbox");
    jdbcTemplate.update("delete from audit_records");
    jdbcTemplate.update("delete from drive_files");
    objectStorage.clear();
  }

  @Test
  void uploadStoresEncryptedObjectMetadataAuditAndFileUploadedOutboxEvent() throws Exception {
    byte[] plaintext = "%PDF-1.7 fake invoice".getBytes(StandardCharsets.UTF_8);

    HttpResponse<String> response =
        httpClient.send(
            uploadRequest("invoice.pdf", "application/pdf", plaintext)
                .header(
                    PlaceholderAuthenticationContext.ACTOR_HEADER,
                    PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID)
                .header(
                    PlaceholderAuthenticationContext.WORKSPACE_HEADER,
                    PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID)
                .header(CorrelationIds.HEADER_NAME, "corr_drive_upload")
                .build(),
            BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.body()).contains("\"name\":\"invoice.pdf\"");
    assertThat(response.body()).contains("\"contentType\":\"application/pdf\"");
    assertThat(response.body()).contains("\"sizeBytes\":21");
    assertThat(response.body()).contains("\"encrypted\":true");

    Map<String, Object> metadata =
        jdbcTemplate.queryForMap(
            "select * from drive_files where workspace_id = ?",
            PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID);
    String fileId = (String) metadata.get("file_id");
    assertThat(metadata.get("encrypted_name")).asString().doesNotContain("invoice.pdf");
    assertThat(metadata.get("storage_key"))
        .isEqualTo(
            "workspaces/"
                + PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID
                + "/drive/"
                + fileId
                + "/original");

    byte[] storedContent = objectStorage.objectBytes((String) metadata.get("storage_key"));
    assertThat(storedContent).isNotEmpty();
    assertThat(Arrays.equals(storedContent, plaintext)).isFalse();

    Map<String, Object> audit =
        jdbcTemplate.queryForMap("select * from audit_records where resource_id = ?", fileId);
    assertThat(audit.get("action")).isEqualTo("drive.file.uploaded");
    assertThat(audit.get("actor_id")).isEqualTo(PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID);
    assertThat(audit.get("correlation_id")).isEqualTo("corr_drive_upload");
    assertThat(audit.get("attributes_json")).asString().doesNotContain("invoice.pdf");

    Map<String, Object> event =
        jdbcTemplate.queryForMap(
            "select * from event_outbox where workspace_id = ?",
            PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID);
    assertThat(event.get("event_type")).isEqualTo("FileUploaded");
    assertThat(event.get("source")).isEqualTo("drive");
    assertThat(event.get("actor_id")).isEqualTo(PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID);
    assertThat(event.get("correlation_id")).isEqualTo("corr_drive_upload");
    assertThat(event.get("idempotency_key")).isEqualTo("drive:" + fileId + ":uploaded:v1");
    assertThat(event.get("payload_json")).asString().contains("\"encryptionAlgorithm\"");
    assertThat(event.get("envelope_json")).asString().doesNotContain("invoice.pdf");
  }

  @Test
  void listsOnlyFilesFromRequestedWorkspaceAfterUploads() throws Exception {
    HttpResponse<String> workspaceAUpload =
        httpClient.send(
            uploadRequest(
                    "workspace-a.pdf",
                    "application/pdf",
                    "%PDF-1.7 workspace A".getBytes(StandardCharsets.UTF_8))
                .header(PlaceholderAuthenticationContext.ACTOR_HEADER, "usr_workspace_a")
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, "wrk_workspace_a")
                .build(),
            BodyHandlers.ofString());
    HttpResponse<String> workspaceBUpload =
        httpClient.send(
            uploadRequest(
                    "workspace-b.pdf",
                    "application/pdf",
                    "%PDF-1.7 workspace B".getBytes(StandardCharsets.UTF_8))
                .header(PlaceholderAuthenticationContext.ACTOR_HEADER, "usr_workspace_b")
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, "wrk_workspace_b")
                .build(),
            BodyHandlers.ofString());

    assertThat(workspaceAUpload.statusCode()).isEqualTo(201);
    assertThat(workspaceBUpload.statusCode()).isEqualTo(201);

    HttpResponse<String> listResponse =
        httpClient.send(
            HttpRequest.newBuilder(uri("/api/drive/files"))
                .header(PlaceholderAuthenticationContext.ACTOR_HEADER, "usr_workspace_a")
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, "wrk_workspace_a")
                .GET()
                .build(),
            BodyHandlers.ofString());

    assertThat(listResponse.statusCode()).isEqualTo(200);
    assertThat(listResponse.body())
        .contains("\"name\":\"workspace-a.pdf\"")
        .doesNotContain("\"name\":\"workspace-b.pdf\"");
  }

  @Test
  void rejectsEmptyUpload() throws Exception {
    HttpResponse<String> response =
        httpClient.send(
            uploadRequest("empty.pdf", "application/pdf", new byte[0]).build(),
            BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("\"error\":\"BAD_REQUEST\"");
    assertThat(objectStorage.objectCount()).isZero();
  }

  @Test
  void rejectsUnsupportedContentType() throws Exception {
    HttpResponse<String> response =
        httpClient.send(
            uploadRequest("script.sh", "application/x-sh", "echo hi".getBytes()).build(),
            BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("\"contentType\":\"application/x-sh\"");
    assertThat(objectStorage.objectCount()).isZero();
  }

  private HttpRequest.Builder uploadRequest(String filename, String contentType, byte[] fileContent)
      throws Exception {
    return HttpRequest.newBuilder(uri("/api/drive/files"))
        .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
        .POST(
            HttpRequest.BodyPublishers.ofByteArray(
                multipartBody(filename, contentType, fileContent)));
  }

  private byte[] multipartBody(String filename, String contentType, byte[] fileContent)
      throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    output.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
    output.write(
        ("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n")
            .getBytes(StandardCharsets.UTF_8));
    output.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    output.write(fileContent);
    output.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return output.toByteArray();
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  @TestConfiguration
  static class DriveFileControllerTestConfiguration {

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

    byte[] objectBytes(String storageKey) {
      return objects.get(storageKey);
    }

    int objectCount() {
      return objects.size();
    }

    void clear() {
      objects.clear();
    }
  }
}
