package com.openecosystem.os.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.OpenEcosystemApiApplication;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = OpenEcosystemApiApplication.class)
class JdbcOcrResultRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-07-10T10:00:00Z");

  @Autowired private JdbcOcrResultRepository repository;
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
  void preservesFullPageAndWordStructureInDocumentOrder() {
    insertParents("wrk_test", "file_test", "ocr_test", "legacy text is not authoritative");
    OcrWord secondWord =
        word(
            "ocrw_second",
            "ocrp_first",
            2,
            1,
            2,
            "2026-0001",
            new BigDecimal("91.25"),
            160,
            20,
            90,
            18,
            "tesseract_tsv");
    OcrWord firstWord =
        word(
            "ocrw_first",
            "ocrp_first",
            1,
            1,
            1,
            "Invoice",
            new BigDecimal("98.75"),
            20,
            20,
            120,
            18,
            "tesseract_tsv");
    OcrWord nativeWord =
        word(
            "ocrw_native",
            "ocrp_second",
            3,
            2,
            1,
            "Total",
            null,
            null,
            null,
            null,
            null,
            "pdf_text_layer");
    OcrPageResult secondPage =
        new OcrPageResult(
            "ocrp_second",
            "ocrr_test",
            "wrk_test",
            2,
            "pdf_text_layer",
            "Total",
            1,
            NOW,
            List.of(nativeWord));
    OcrPageResult firstPage =
        new OcrPageResult(
            "ocrp_first",
            "ocrr_test",
            "wrk_test",
            1,
            "tesseract_tsv",
            "Invoice 2026-0001",
            2,
            NOW,
            List.of(secondWord, firstWord));
    OcrDocumentResult result =
        new OcrDocumentResult(
            "ocrr_test",
            "ocr_test",
            "file_test",
            "wrk_test",
            "tesseract",
            "5.5.0",
            "Invoice 2026-0001\nTotal",
            2,
            3,
            NOW,
            NOW,
            List.of(secondPage, firstPage));

    repository.save(result);

    OcrDocumentResult stored =
        repository.findByJobIdForWorkspace("ocr_test", "wrk_test").orElseThrow();
    assertThat(stored.provider()).isEqualTo("tesseract");
    assertThat(stored.providerVersion()).isEqualTo("5.5.0");
    assertThat(stored.pages()).extracting(OcrPageResult::pageNumber).containsExactly(1, 2);
    assertThat(stored.pages().getFirst().words())
        .extracting(OcrWord::readingOrder)
        .containsExactly(1, 2);
    assertThat(stored.pages().getFirst().words().getFirst())
        .satisfies(
            storedWord -> {
              assertThat(storedWord.wordText()).isEqualTo("Invoice");
              assertThat(storedWord.confidence()).isEqualByComparingTo("98.75");
              assertThat(storedWord.leftPx()).isEqualTo(20);
              assertThat(storedWord.topPx()).isEqualTo(20);
              assertThat(storedWord.widthPx()).isEqualTo(120);
              assertThat(storedWord.heightPx()).isEqualTo(18);
              assertThat(storedWord.blockNumber()).isZero();
              assertThat(storedWord.paragraphNumber()).isZero();
              assertThat(storedWord.lineNumber()).isZero();
              assertThat(storedWord.wordNumber()).isZero();
            });
    assertThat(stored.pages().get(1).words().getFirst().confidence()).isNull();
    assertThat(stored.pages().get(1).words().getFirst().leftPx()).isNull();
  }

  @Test
  void requiresWorkspaceScopeForEveryResultRead() {
    insertParents("wrk_test", "file_test", "ocr_test", null);
    repository.save(document("ocrr_test", "ocr_test", "file_test", "wrk_test"));

    assertThat(repository.findByJobIdForWorkspace("ocr_test", "wrk_other")).isEmpty();
    assertThat(repository.findByIdForWorkspace("ocrr_test", "wrk_other")).isEmpty();
    assertThat(repository.findByJobIdForWorkspace("ocr_test", "wrk_test")).isPresent();
  }

  @Test
  void keepsLegacyJobTextOutsideTheStructuredResultRepositoryBoundary() {
    insertParents("wrk_test", "file_legacy", "ocr_legacy", "legacy-only OCR text");

    assertThat(repository.findByJobIdForWorkspace("ocr_legacy", "wrk_test")).isEmpty();
  }

  @Test
  void findsWorkspaceScopedResultPresenceWithoutLoadingDocumentContent() {
    insertParents("wrk_test", "file_present", "ocr_present", null);
    insertParents("wrk_test", "file_absent", "ocr_absent", null);
    repository.save(document("ocrr_present", "ocr_present", "file_present", "wrk_test"));

    assertThat(
            repository.findPresentJobIdsForWorkspace(
                "wrk_test", List.of("ocr_present", "ocr_absent")))
        .containsExactly("ocr_present");
    assertThat(repository.findPresentJobIdsForWorkspace("wrk_other", List.of("ocr_present")))
        .isEmpty();
  }

  private OcrDocumentResult document(
      String resultId, String jobId, String fileId, String workspaceId) {
    OcrWord word =
        new OcrWord(
            "ocrw_test",
            "ocrp_test",
            resultId,
            workspaceId,
            1,
            1,
            1,
            0,
            0,
            0,
            0,
            "Invoice",
            null,
            null,
            null,
            null,
            null,
            "pdf_text_layer",
            NOW);
    OcrPageResult page =
        new OcrPageResult(
            "ocrp_test",
            resultId,
            workspaceId,
            1,
            "pdf_text_layer",
            "Invoice",
            1,
            NOW,
            List.of(word));
    return new OcrDocumentResult(
        resultId,
        jobId,
        fileId,
        workspaceId,
        "pdf_text_layer",
        "pdfbox-test",
        "Invoice",
        1,
        1,
        NOW,
        NOW,
        List.of(page));
  }

  private OcrWord word(
      String wordId,
      String pageId,
      int readingOrder,
      int pageNumber,
      int pageWordOrder,
      String text,
      BigDecimal confidence,
      Integer left,
      Integer top,
      Integer width,
      Integer height,
      String sourceKind) {
    return new OcrWord(
        wordId,
        pageId,
        "ocrr_test",
        "wrk_test",
        readingOrder,
        pageNumber,
        pageWordOrder,
        0,
        0,
        0,
        0,
        text,
        confidence,
        left,
        top,
        width,
        height,
        sourceKind,
        NOW);
  }

  private void insertParents(
      String workspaceId, String fileId, String jobId, String legacyExtractedText) {
    jdbcTemplate.update(
        """
        insert into drive_files (
          file_id, workspace_id, owner_id, encrypted_name, content_type, size_bytes,
          checksum_sha256, storage_key, encryption_algorithm, encryption_key_id,
          content_iv, name_iv, created_at, updated_at
        ) values (?, ?, 'usr_test', 'encrypted-name', 'application/pdf', 10,
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', ?,
          'AES-256-GCM', 'test-key', 'content-iv', 'name-iv', ?, ?)
        """,
        fileId,
        workspaceId,
        "workspaces/" + workspaceId + "/drive/" + fileId + "/original",
        NOW,
        NOW);
    jdbcTemplate.update(
        """
        insert into ocr_jobs (
          job_id, file_id, workspace_id, actor_id, source_event_id, correlation_id,
          content_type, storage_key, status, provider, attempt_count, max_attempts,
          extracted_text, extracted_text_length, failure_code, failure_message, queued_at,
          processing_started_at, completed_at, failed_at, next_attempt_at, created_at, updated_at
        ) values (?, ?, ?, 'usr_test', 'evt_test', 'corr_test', 'application/pdf', ?,
          'completed', 'legacy', 1, 3, ?, ?, null, null, ?, ?, ?, null, null, ?, ?)
        """,
        jobId,
        fileId,
        workspaceId,
        "workspaces/" + workspaceId + "/drive/" + fileId + "/original",
        legacyExtractedText,
        legacyExtractedText == null ? null : legacyExtractedText.length(),
        NOW,
        NOW,
        NOW,
        NOW,
        NOW);
  }
}
