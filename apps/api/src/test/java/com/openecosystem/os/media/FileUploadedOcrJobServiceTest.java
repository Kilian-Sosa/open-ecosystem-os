package com.openecosystem.os.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.OpenEcosystemApiApplication;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = OpenEcosystemApiApplication.class)
class FileUploadedOcrJobServiceTest {

  @Autowired private FileUploadedOcrJobService service;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from invoice_extraction_field_sources");
    jdbcTemplate.update("delete from invoice_extraction_fields");
    jdbcTemplate.update("delete from invoice_extractions");
    jdbcTemplate.update("delete from ocr_result_words");
    jdbcTemplate.update("delete from ocr_result_pages");
    jdbcTemplate.update("delete from ocr_results");
    jdbcTemplate.update("delete from event_consumptions");
    jdbcTemplate.update("delete from ocr_jobs");
    jdbcTemplate.update("delete from event_outbox");
    jdbcTemplate.update("delete from audit_records");
    jdbcTemplate.update("delete from drive_files");
  }

  @Test
  void pdfFileUploadedCreatesQueuedOcrJobAuditAndRequestedEvent() {
    service.queueOcrJobIfEligible(fileUploaded("evt_pdf", "file_pdf", "application/pdf"));

    Map<String, Object> job =
        jdbcTemplate.queryForMap("select * from ocr_jobs where file_id = ?", "file_pdf");
    assertThat(job.get("status")).isEqualTo("queued");
    assertThat(job.get("content_type")).isEqualTo("application/pdf");
    assertThat(job.get("attempt_count")).isEqualTo(0);
    assertThat(job.get("max_attempts")).isEqualTo(3);
    assertThat(job.get("extracted_text")).isNull();

    Map<String, Object> event =
        jdbcTemplate.queryForMap("select * from event_outbox where event_type = ?", "OcrRequested");
    assertThat(event.get("source")).isEqualTo("media");
    assertThat(event.get("causation_id")).isEqualTo("evt_pdf");
    assertThat(event.get("payload_json")).asString().contains("\"fileId\":\"file_pdf\"");
    assertThat(event.get("payload_json")).asString().doesNotContain("extractedText");

    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "select * from audit_records where action = ?", "media.ocr.job.queued");
    assertThat(audit.get("resource_type")).isEqualTo("ocr_job");
    assertThat(audit.get("attributes_json")).asString().doesNotContain("Invoice");
  }

  @Test
  void nonOcrContentTypeIsConsumedWithoutCreatingJob() {
    service.queueOcrJobIfEligible(fileUploaded("evt_text", "file_text", "text/plain"));

    Integer jobs = jdbcTemplate.queryForObject("select count(*) from ocr_jobs", Integer.class);
    Integer requestedEvents =
        jdbcTemplate.queryForObject(
            "select count(*) from event_outbox where event_type = 'OcrRequested'", Integer.class);
    Integer consumptions =
        jdbcTemplate.queryForObject("select count(*) from event_consumptions", Integer.class);

    assertThat(jobs).isZero();
    assertThat(requestedEvents).isZero();
    assertThat(consumptions).isEqualTo(1);
  }

  @Test
  void duplicateFileUploadedEventDoesNotCreateDuplicateJob() {
    FileUploadedEvent event = fileUploaded("evt_png", "file_png", "image/png");

    service.queueOcrJobIfEligible(event);
    service.queueOcrJobIfEligible(event);

    Integer jobs = jdbcTemplate.queryForObject("select count(*) from ocr_jobs", Integer.class);
    Integer outboxEvents =
        jdbcTemplate.queryForObject(
            "select count(*) from event_outbox where event_type = 'OcrRequested'", Integer.class);
    Integer consumptions =
        jdbcTemplate.queryForObject("select count(*) from event_consumptions", Integer.class);

    assertThat(jobs).isEqualTo(1);
    assertThat(outboxEvents).isEqualTo(1);
    assertThat(consumptions).isEqualTo(1);
  }

  private FileUploadedEvent fileUploaded(String eventId, String fileId, String contentType) {
    jdbcTemplate.update(
        """
        insert into drive_files (
          file_id, workspace_id, owner_id, encrypted_name, content_type, size_bytes,
          checksum_sha256, storage_key, encryption_algorithm, encryption_key_id,
          content_iv, name_iv, created_at, updated_at
        ) values (?, 'wrk_123', 'usr_123', 'encrypted-name', ?, 1024,
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', ?,
          'AES-256-GCM', 'test-key', 'content-iv', 'name-iv', current_timestamp, current_timestamp)
        """,
        fileId,
        contentType,
        "workspaces/wrk_123/drive/" + fileId + "/original");
    return new FileUploadedEvent(
        eventId,
        1,
        Instant.parse("2026-05-22T10:00:00Z"),
        "wrk_123",
        "usr_123",
        "corr_123",
        "drive:" + fileId + ":uploaded:v1",
        fileId,
        contentType,
        1024,
        "workspaces/wrk_123/drive/" + fileId + "/original",
        Instant.parse("2026-05-22T10:00:00Z"));
  }
}
