package com.openecosystem.os.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.OpenEcosystemApiApplication;
import com.openecosystem.os.drive.DriveFileMetadata;
import com.openecosystem.os.drive.DriveFileRepository;
import com.openecosystem.os.drive.DriveFileVisibility;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = OpenEcosystemApiApplication.class)
class OcrJobRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

  @Autowired private OcrJobRepository repository;
  @Autowired private DriveFileRepository driveFileRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from invoice_extraction_field_sources");
    jdbcTemplate.update("delete from invoice_extraction_fields");
    jdbcTemplate.update("delete from invoice_extractions");
    jdbcTemplate.update("delete from ocr_result_words");
    jdbcTemplate.update("delete from ocr_result_pages");
    jdbcTemplate.update("delete from ocr_results");
    jdbcTemplate.update("delete from ocr_jobs");
    jdbcTemplate.update("delete from drive_files");
  }

  @Test
  void exposesOnlyContentFreeReferencesAndSafeSummariesBeforeDetailLoading() {
    driveFileRepository.save(file("file_allowed", "wrk_test"));
    driveFileRepository.save(file("file_other", "wrk_other"));
    repository.saveQueued(job("ocr_allowed", "file_allowed", "wrk_test"));
    repository.saveQueued(job("ocr_other", "file_other", "wrk_other"));

    assertThat(repository.listSourceReferencesByWorkspace("wrk_test"))
        .containsExactly(new OcrJobSourceReference("ocr_allowed", "file_allowed", "wrk_test"));
    assertThat(repository.findSourceReferenceByIdForWorkspace("ocr_allowed", "wrk_test"))
        .contains(new OcrJobSourceReference("ocr_allowed", "file_allowed", "wrk_test"));
    assertThat(
            repository.findSummariesByIdsForWorkspace(
                List.of("ocr_allowed", "ocr_other"), "wrk_test"))
        .containsExactly(
            new OcrJobSummary(
                "ocr_allowed",
                "file_allowed",
                "wrk_test",
                "application/pdf",
                OcrJobStatus.FAILED,
                "tesseract",
                2,
                3,
                24,
                "OCR_FAILED",
                "corr_allowed",
                NOW,
                NOW,
                null,
                NOW,
                NOW));

    assertThat(repository.findDetailByIdForWorkspace("ocr_allowed", "wrk_test"))
        .get()
        .satisfies(
            detail -> {
              assertThat(detail.extractedText()).isEqualTo("legacy extracted secret");
              assertThat(detail.failureMessage()).isEqualTo("private failure diagnostic");
              assertThat(detail.storageKey()).isEqualTo("private-storage-key");
            });
  }

  @Test
  void returnsNoSafeSummariesForAnEmptyAuthorizedCollection() {
    driveFileRepository.save(file("file_allowed", "wrk_test"));
    repository.saveQueued(job("ocr_allowed", "file_allowed", "wrk_test"));

    assertThat(repository.findSummariesByIdsForWorkspace(List.of(), "wrk_test")).isEmpty();
  }

  private OcrJob job(String jobId, String fileId, String workspaceId) {
    return new OcrJob(
        jobId,
        fileId,
        workspaceId,
        "usr_owner",
        "evt_test",
        "corr_allowed",
        "application/pdf",
        "private-storage-key",
        OcrJobStatus.FAILED,
        "tesseract",
        2,
        3,
        "legacy extracted secret",
        24,
        "OCR_FAILED",
        "private failure diagnostic",
        NOW,
        NOW,
        null,
        NOW,
        null,
        NOW,
        NOW);
  }

  private DriveFileMetadata file(String fileId, String workspaceId) {
    return new DriveFileMetadata(
        fileId,
        workspaceId,
        "usr_owner",
        DriveFileVisibility.PRIVATE,
        "encrypted-name",
        "application/pdf",
        12,
        "checksum",
        "private-storage-key",
        "AES-256-GCM",
        "key-id",
        "content-iv",
        "name-iv",
        NOW,
        NOW);
  }
}
