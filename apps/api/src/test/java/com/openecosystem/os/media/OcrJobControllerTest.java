package com.openecosystem.os.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.OpenEcosystemApiApplication;
import com.openecosystem.os.common.security.PlaceholderAuthenticationContext;
import com.openecosystem.os.drive.DriveFileMetadata;
import com.openecosystem.os.drive.DriveFileRepository;
import com.openecosystem.os.drive.crypto.EncryptedText;
import com.openecosystem.os.drive.crypto.FileEncryptionService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    classes = OpenEcosystemApiApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OcrJobControllerTest {

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DriveFileRepository driveFileRepository;
  @Autowired private FileEncryptionService encryptionService;
  @Autowired private OcrJobRepository ocrJobRepository;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from event_consumptions");
    jdbcTemplate.update("delete from ocr_jobs");
    jdbcTemplate.update("delete from event_outbox");
    jdbcTemplate.update("delete from audit_records");
    jdbcTemplate.update("delete from drive_files");
  }

  @Test
  void listsWorkspaceJobsWithoutFullExtractedTextAndShowsDetailText() throws Exception {
    saveDriveFile("file_invoice", "wrk_123", "Invoice_2026_05.pdf", "application/pdf");
    saveDriveFile("file_other", "wrk_other", "Other.pdf", "application/pdf");
    ocrJobRepository.saveQueued(
        job("ocr_invoice", "file_invoice", "wrk_123", OcrJobStatus.COMPLETED));
    ocrJobRepository.saveQueued(
        job("ocr_other", "file_other", "wrk_other", OcrJobStatus.COMPLETED));

    HttpResponse<String> listResponse =
        httpClient.send(
            request("/api/media/ocr-jobs")
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, "wrk_123")
                .build(),
            BodyHandlers.ofString());

    assertThat(listResponse.statusCode()).isEqualTo(200);
    assertThat(listResponse.body()).contains("ocr_invoice");
    assertThat(listResponse.body()).contains("Invoice_2026_05.pdf");
    assertThat(listResponse.body()).doesNotContain("ocr_other");
    assertThat(listResponse.body()).doesNotContain("Fake extracted invoice total");

    HttpResponse<String> detailResponse =
        httpClient.send(
            request("/api/media/ocr-jobs/ocr_invoice")
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, "wrk_123")
                .build(),
            BodyHandlers.ofString());

    assertThat(detailResponse.statusCode()).isEqualTo(200);
    assertThat(detailResponse.body()).contains("Fake extracted invoice total");
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
  }

  private void saveDriveFile(
      String fileId, String workspaceId, String filename, String contentType) {
    Instant now = Instant.parse("2026-05-22T10:00:00Z");
    EncryptedText encryptedName = encryptionService.encryptText(filename);
    driveFileRepository.save(
        new DriveFileMetadata(
            fileId,
            workspaceId,
            "usr_123",
            encryptedName.ciphertextBase64(),
            contentType,
            1024,
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "workspaces/" + workspaceId + "/drive/" + fileId + "/original",
            FileEncryptionService.ALGORITHM,
            "test-key",
            "content-iv",
            encryptedName.ivBase64(),
            now,
            now));
  }

  private OcrJob job(String jobId, String fileId, String workspaceId, OcrJobStatus status) {
    Instant now = Instant.parse("2026-05-22T10:00:00Z");
    String extractedText = "Fake extracted invoice total 124.00 EUR";
    return new OcrJob(
        jobId,
        fileId,
        workspaceId,
        "usr_123",
        "evt_uploaded",
        "corr_123",
        "application/pdf",
        "workspaces/" + workspaceId + "/drive/" + fileId + "/original",
        status,
        "mock",
        1,
        3,
        extractedText,
        extractedText.length(),
        null,
        null,
        now,
        now,
        now,
        null,
        null,
        now,
        now);
  }
}
