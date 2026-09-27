package com.openecosystem.os.worker.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.worker.OpenEcosystemWorkerApplication;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    classes = {
      OpenEcosystemWorkerApplication.class,
      TestTesseractVersionConfiguration.class,
      OcrJobProcessorTest.OcrJobProcessorTestConfiguration.class
    })
class OcrJobProcessorTest {

  @Autowired private OcrJobProcessor processor;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private TestOcrProvider provider;
  @Autowired private Environment environment;

  @BeforeEach
  void createSchema() {
    provider.fail.set(false);
    provider.unsafeFailure.set(false);
    provider.multiPage.set(false);
    provider.calls.set(0);
    provider.onExtract = () -> {};
    jdbcTemplate.update("drop table if exists event_consumptions");
    jdbcTemplate.update("drop table if exists event_outbox");
    jdbcTemplate.update("drop table if exists audit_records");
    jdbcTemplate.update("drop table if exists ocr_result_words");
    jdbcTemplate.update("drop table if exists ocr_result_pages");
    jdbcTemplate.update("drop table if exists ocr_results");
    jdbcTemplate.update("drop table if exists ocr_jobs");
    jdbcTemplate.update("drop table if exists drive_files");
    jdbcTemplate.update(
        """
        create table drive_files (
          file_id varchar(64) primary key,
          workspace_id varchar(128) not null,
          unique (file_id, workspace_id)
        )
        """);
    jdbcTemplate.update(
        """
        create table ocr_jobs (
          job_id varchar(64) primary key,
          file_id varchar(64) not null,
          workspace_id varchar(128) not null,
          actor_id varchar(128) not null,
          source_event_id varchar(64) not null,
          correlation_id varchar(128) not null,
          content_type varchar(255) not null,
          storage_key varchar(1024) not null,
          status varchar(32) not null,
          provider varchar(128),
          attempt_count integer not null,
          max_attempts integer not null,
          extracted_text text,
          extracted_text_length integer,
          failure_code varchar(128),
          failure_message varchar(512),
          queued_at timestamp with time zone not null,
          processing_started_at timestamp with time zone,
          completed_at timestamp with time zone,
          failed_at timestamp with time zone,
          next_attempt_at timestamp with time zone,
          created_at timestamp with time zone not null,
          updated_at timestamp with time zone not null,
          unique (job_id, workspace_id),
          unique (job_id, file_id, workspace_id),
          foreign key (file_id, workspace_id) references drive_files (file_id, workspace_id)
        )
        """);
    jdbcTemplate.update(
        """
        create table ocr_results (
          ocr_result_id varchar(64) primary key,
          job_id varchar(64) not null unique,
          file_id varchar(64) not null,
          workspace_id varchar(128) not null,
          provider varchar(128) not null,
          provider_version varchar(128) not null,
          document_text text not null,
          page_count integer not null,
          word_count integer not null,
          created_at timestamp with time zone not null,
          updated_at timestamp with time zone not null,
          unique (ocr_result_id, workspace_id),
          unique (ocr_result_id, job_id, file_id, workspace_id),
          foreign key (job_id, file_id, workspace_id)
            references ocr_jobs (job_id, file_id, workspace_id),
          foreign key (file_id, workspace_id) references drive_files (file_id, workspace_id),
          check (page_count > 0),
          check (word_count >= 0)
        )
        """);
    jdbcTemplate.update(
        """
        create table ocr_result_pages (
          ocr_page_id varchar(64) primary key,
          ocr_result_id varchar(64) not null,
          workspace_id varchar(128) not null,
          page_number integer not null,
          source_kind varchar(32) not null,
          page_text text not null,
          word_count integer not null,
          created_at timestamp with time zone not null,
          unique (ocr_result_id, page_number),
          unique (ocr_page_id, ocr_result_id, workspace_id),
          unique (ocr_page_id, ocr_result_id, workspace_id, page_number),
          foreign key (ocr_result_id, workspace_id)
            references ocr_results (ocr_result_id, workspace_id),
          check (page_number > 0),
          check (word_count >= 0),
          check (source_kind in ('pdf_text_layer', 'tesseract_tsv'))
        )
        """);
    jdbcTemplate.update(
        """
        create table ocr_result_words (
          ocr_word_id varchar(64) primary key,
          ocr_page_id varchar(64) not null,
          ocr_result_id varchar(64) not null,
          workspace_id varchar(128) not null,
          reading_order integer not null,
          page_number integer not null,
          page_word_order integer not null,
          block_number integer not null,
          paragraph_number integer not null,
          line_number integer not null,
          word_number integer not null,
          word_text text not null,
          confidence decimal(5,2),
          left_px integer,
          top_px integer,
          width_px integer,
          height_px integer,
          source_kind varchar(32) not null,
          created_at timestamp with time zone not null,
          unique (ocr_result_id, reading_order),
          unique (ocr_word_id, ocr_result_id, workspace_id),
          foreign key (ocr_page_id, ocr_result_id, workspace_id, page_number)
            references ocr_result_pages (ocr_page_id, ocr_result_id, workspace_id, page_number),
          check (reading_order >= 0),
          check (page_number > 0),
          check (page_word_order >= 0),
          check (block_number >= 0),
          check (paragraph_number >= 0),
          check (line_number >= 0),
          check (word_number >= 0),
          check (confidence is null or (confidence >= 0 and confidence <= 100)),
          check (
            (left_px is null and top_px is null and width_px is null and height_px is null)
            or
            (left_px is not null and top_px is not null and width_px is not null and height_px is not null)
          ),
          check (source_kind in ('pdf_text_layer', 'tesseract_tsv'))
        )
        """);
    jdbcTemplate.update(
        """
        create table event_outbox (
          event_id varchar(64) primary key,
          event_type varchar(128) not null,
          version integer not null,
          occurred_at timestamp with time zone not null,
          workspace_id varchar(128) not null,
          actor_id varchar(128) not null,
          correlation_id varchar(128) not null,
          causation_id varchar(128),
          source varchar(128) not null,
          idempotency_key varchar(256) not null unique,
          payload_json text not null,
          envelope_json text not null,
          published_at timestamp with time zone,
          created_at timestamp with time zone not null
        )
        """);
    jdbcTemplate.update(
        """
        create table audit_records (
          audit_id varchar(64) primary key,
          action varchar(128) not null,
          resource_type varchar(128) not null,
          resource_id varchar(128),
          workspace_id varchar(128) not null,
          actor_id varchar(128) not null,
          correlation_id varchar(128) not null,
          occurred_at timestamp with time zone not null,
          outcome varchar(32) not null,
          attributes_json text not null
        )
        """);
    jdbcTemplate.update(
        """
        create table event_consumptions (
          consumer_name varchar(128) not null,
          idempotency_key varchar(256) not null,
          event_id varchar(64) not null,
          consumed_at timestamp with time zone not null,
          primary key (consumer_name, idempotency_key)
        )
        """);
  }

  @Test
  void completesQueuedJobAndSkipsDuplicateDelivery() {
    insertJob("ocr_success", "file_success", 2);
    OcrRequestedEvent event = requestedEvent("evt_requested", "ocr_success", "file_success");
    double completedBefore = ocrCounter("completed");
    double noOpBefore = ocrCounter("no-op");

    OcrProcessingResult firstResult = processor.process(event);
    OcrProcessingResult duplicateResult = processor.process(event);

    assertThat(firstResult.outcome()).isEqualTo(OcrProcessingOutcome.COMPLETED);
    assertThat(duplicateResult.outcome()).isEqualTo(OcrProcessingOutcome.NO_OP);

    Map<String, Object> job =
        jdbcTemplate.queryForMap("select * from ocr_jobs where job_id = ?", "ocr_success");
    assertThat(job.get("status")).isEqualTo("completed");
    assertThat(job.get("provider")).isEqualTo("test");
    assertThat(job.get("attempt_count")).isEqualTo(1);
    assertThat(job.get("extracted_text")).asString().contains("Test OCR text");
    assertThat(jdbcTemplate.queryForObject("select count(*) from ocr_results", Integer.class))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("select count(*) from ocr_result_words", Integer.class))
        .isEqualTo(1);
    Map<String, Object> result =
        jdbcTemplate.queryForMap("select * from ocr_results where job_id = ?", "ocr_success");
    assertThat(result.get("word_count")).isEqualTo(1);
    assertThat(result.get("updated_at")).isNotNull();
    Map<String, Object> page = jdbcTemplate.queryForMap("select * from ocr_result_pages");
    assertThat(page.get("ocr_page_id")).isNotNull();
    assertThat(page.get("workspace_id")).isEqualTo("wrk_123");
    assertThat(page.get("word_count")).isEqualTo(1);
    Map<String, Object> word = jdbcTemplate.queryForMap("select * from ocr_result_words");
    assertThat(word.get("ocr_word_id")).isNotNull();
    assertThat(word.get("source_kind")).isEqualTo("pdf_text_layer");
    assertThat(word.get("left_px")).isEqualTo(1);
    assertThat(word.get("top_px")).isEqualTo(2);
    assertThat(word.get("width_px")).isEqualTo(3);
    assertThat(word.get("height_px")).isEqualTo(4);

    Integer started =
        jdbcTemplate.queryForObject(
            "select count(*) from event_outbox where event_type = 'OcrStarted'", Integer.class);
    Integer completed =
        jdbcTemplate.queryForObject(
            "select count(*) from event_outbox where event_type = 'OcrCompleted'", Integer.class);
    Integer consumptions =
        jdbcTemplate.queryForObject("select count(*) from event_consumptions", Integer.class);

    assertThat(started).isEqualTo(1);
    assertThat(completed).isEqualTo(1);
    assertThat(consumptions).isEqualTo(1);
    assertThat(ocrCounter("completed")).isEqualTo(completedBefore + 1);
    assertThat(ocrCounter("no-op")).isEqualTo(noOpBefore + 1);
  }

  @Test
  void assignsDocumentGlobalReadingOrderWhileRetainingPageOrder() {
    provider.multiPage.set(true);
    insertJob("ocr_orders", "file_orders", 2);

    OcrProcessingResult result =
        processor.process(requestedEvent("evt_orders", "ocr_orders", "file_orders"));

    assertThat(result.outcome()).isEqualTo(OcrProcessingOutcome.COMPLETED);
    assertThat(
            jdbcTemplate.queryForList(
                "select reading_order, page_number, page_word_order from ocr_result_words order by"
                    + " reading_order"))
        .extracting(
            row -> row.get("reading_order"),
            row -> row.get("page_number"),
            row -> row.get("page_word_order"))
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(1, 1, 1),
            org.assertj.core.groups.Tuple.tuple(2, 2, 1));
  }

  @Test
  void keepsOcrEventsAndAuditsMetadataOnly() {
    insertJob("ocr_metadata", "file_metadata", 2);

    processor.process(requestedEvent("evt_metadata", "ocr_metadata", "file_metadata"));

    String eventPayload =
        jdbcTemplate.queryForObject(
            "select payload_json from event_outbox where event_type = 'OcrCompleted'",
            String.class);
    String auditAttributes =
        jdbcTemplate.queryForObject(
            "select attributes_json from audit_records where action = 'media.ocr.job.completed'",
            String.class);
    assertThat(eventPayload).doesNotContain("Test OCR text", "storageKey");
    assertThat(auditAttributes).doesNotContain("Test OCR text", "extractedTextLength");
  }

  @Test
  void sanitizesUnknownProviderFailures() {
    provider.unsafeFailure.set(true);
    insertJob("ocr_sanitized", "file_sanitized", 1);

    OcrProcessingResult result =
        processor.process(requestedEvent("evt_sanitized", "ocr_sanitized", "file_sanitized"));

    assertThat(result.outcome()).isEqualTo(OcrProcessingOutcome.DEAD_LETTER);
    assertThat(
            jdbcTemplate.queryForObject(
                "select failure_message from ocr_jobs where job_id = ?",
                String.class,
                "ocr_sanitized"))
        .isEqualTo("OCR processing failed");
    assertThat(
            jdbcTemplate.queryForObject(
                "select payload_json from event_outbox where event_type = 'OcrFailed'",
                String.class))
        .doesNotContain("private-path", "private-document");
  }

  @Test
  void rejectsLineageMismatchBeforeProviderInvocation() {
    insertJob("ocr_lineage", "file_lineage", 2);
    OcrRequestedEvent mismatched =
        new OcrRequestedEvent(
            "evt_lineage",
            1,
            Instant.parse("2026-05-22T10:01:00Z"),
            "wrk_123",
            "usr_123",
            "corr_123",
            "evt_uploaded",
            "media:ocr:ocr_lineage:requested:v1",
            "ocr_lineage",
            "file_lineage",
            "application/pdf",
            "workspaces/wrk_123/drive/file_lineage/replaced",
            0,
            2,
            Instant.parse("2026-05-22T10:01:00Z"));

    OcrProcessingResult result = processor.process(mismatched);

    assertThat(result.outcome()).isEqualTo(OcrProcessingOutcome.RETRY);
    assertThat(provider.calls.get()).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from ocr_jobs where job_id = ?", String.class, "ocr_lineage"))
        .isEqualTo("queued");
  }

  @Test
  void recoversOnlyStaleProcessingClaims() {
    insertJob("ocr_stale", "file_stale", 3);
    jdbcTemplate.update(
        "update ocr_jobs set status = 'processing', attempt_count = 1, processing_started_at = ?"
            + " where job_id = ?",
        java.sql.Timestamp.from(Instant.now().minusSeconds(7_200)),
        "ocr_stale");

    OcrProcessingResult result =
        processor.process(requestedEvent("evt_stale", "ocr_stale", "file_stale"));

    assertThat(result.outcome()).isEqualTo(OcrProcessingOutcome.COMPLETED);
    assertThat(
            jdbcTemplate.queryForObject(
                "select attempt_count from ocr_jobs where job_id = ?", Integer.class, "ocr_stale"))
        .isEqualTo(2);
  }

  @Test
  void retriesFreshProcessingClaimsWithoutInvokingTheProvider() {
    insertJob("ocr_fresh", "file_fresh", 3);
    jdbcTemplate.update(
        "update ocr_jobs set status = 'processing', attempt_count = 1, processing_started_at = ?"
            + " where job_id = ?",
        java.sql.Timestamp.from(Instant.now()),
        "ocr_fresh");

    OcrProcessingResult result =
        processor.process(requestedEvent("evt_fresh", "ocr_fresh", "file_fresh"));

    assertThat(result.outcome()).isEqualTo(OcrProcessingOutcome.RETRY);
    assertThat(provider.calls.get()).isZero();
  }

  @Test
  void staleFailureCannotRequeueACompletedJob() {
    insertJob("ocr_fenced", "file_fenced", 3);
    provider.fail.set(true);
    provider.onExtract =
        () ->
            jdbcTemplate.update(
                """
                update ocr_jobs
                set status = 'completed', attempt_count = 2, completed_at = ?, updated_at = ?
                where job_id = ?
                """,
                java.sql.Timestamp.from(Instant.now()),
                java.sql.Timestamp.from(Instant.now()),
                "ocr_fenced");

    OcrProcessingResult result =
        processor.process(requestedEvent("evt_fenced", "ocr_fenced", "file_fenced"));

    assertThat(result.outcome()).isEqualTo(OcrProcessingOutcome.RETRY);
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from ocr_jobs where job_id = ?", String.class, "ocr_fenced"))
        .isEqualTo("completed");
  }

  @Test
  void loadsTheNormalS3RuntimeConfiguration() {
    assertThat(environment.getProperty("openecosystem.storage.s3.endpoint"))
        .isEqualTo("http://localhost:9000");
    assertThat(environment.getProperty("openecosystem.storage.s3.bucket"))
        .isEqualTo("openecosystem");
  }

  @Test
  void retriesProviderFailureThenFailsAndEmitsFailedEvent() {
    provider.fail.set(true);
    insertJob("ocr_fail", "file_fail", 2);
    OcrRequestedEvent event = requestedEvent("evt_requested_fail", "ocr_fail", "file_fail");
    double retryBefore = ocrCounter("retry");
    double deadLetterBefore = ocrCounter("dead-letter");

    OcrProcessingResult retryResult = processor.process(event);
    OcrProcessingResult finalResult = processor.process(event);

    assertThat(retryResult.outcome()).isEqualTo(OcrProcessingOutcome.RETRY);
    assertThat(finalResult.outcome()).isEqualTo(OcrProcessingOutcome.DEAD_LETTER);

    Map<String, Object> job =
        jdbcTemplate.queryForMap("select * from ocr_jobs where job_id = ?", "ocr_fail");
    assertThat(job.get("status")).isEqualTo("failed");
    assertThat(job.get("attempt_count")).isEqualTo(2);
    assertThat(job.get("failure_code")).isEqualTo("TEST_OCR_FAILED");

    Integer failed =
        jdbcTemplate.queryForObject(
            "select count(*) from event_outbox where event_type = 'OcrFailed'", Integer.class);
    Integer consumptions =
        jdbcTemplate.queryForObject("select count(*) from event_consumptions", Integer.class);

    assertThat(failed).isEqualTo(1);
    assertThat(consumptions).isEqualTo(1);
    assertThat(ocrCounter("retry")).isEqualTo(retryBefore + 1);
    assertThat(ocrCounter("dead-letter")).isEqualTo(deadLetterBefore + 1);
  }

  private void insertJob(String jobId, String fileId, int maxAttempts) {
    Instant now = Instant.parse("2026-05-22T10:00:00Z");
    jdbcTemplate.update(
        "insert into drive_files (file_id, workspace_id) values (?, ?)", fileId, "wrk_123");
    jdbcTemplate.update(
        """
        insert into ocr_jobs (
          job_id,
          file_id,
          workspace_id,
          actor_id,
          source_event_id,
          correlation_id,
          content_type,
          storage_key,
          status,
          provider,
          attempt_count,
          max_attempts,
          extracted_text,
          extracted_text_length,
          failure_code,
          failure_message,
          queued_at,
          processing_started_at,
          completed_at,
          failed_at,
          next_attempt_at,
          created_at,
          updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, null, 0, ?, null, null, null, null, ?, null, null, null, ?, ?, ?)
        """,
        jobId,
        fileId,
        "wrk_123",
        "usr_123",
        "evt_uploaded",
        "corr_123",
        "application/pdf",
        "workspaces/wrk_123/drive/" + fileId + "/original",
        "queued",
        maxAttempts,
        now,
        now,
        now,
        now);
  }

  private OcrRequestedEvent requestedEvent(String eventId, String jobId, String fileId) {
    Instant now = Instant.parse("2026-05-22T10:01:00Z");
    return new OcrRequestedEvent(
        eventId,
        1,
        now,
        "wrk_123",
        "usr_123",
        "corr_123",
        "evt_uploaded",
        "media:ocr:" + jobId + ":requested:v1",
        jobId,
        fileId,
        "application/pdf",
        "workspaces/wrk_123/drive/" + fileId + "/original",
        0,
        2,
        now);
  }

  private double ocrCounter(String outcome) {
    var counter =
        meterRegistry.find("openecosystem.worker.ocr.jobs").tag("outcome", outcome).counter();
    return counter == null ? 0 : counter.count();
  }

  @TestConfiguration
  static class OcrJobProcessorTestConfiguration {

    @Bean
    @Primary
    TestOcrProvider testOcrProvider() {
      return new TestOcrProvider();
    }
  }

  static class TestOcrProvider implements OcrProvider {

    private final AtomicBoolean fail = new AtomicBoolean(false);
    private final AtomicBoolean unsafeFailure = new AtomicBoolean(false);
    private final AtomicBoolean multiPage = new AtomicBoolean(false);
    private final AtomicInteger calls = new AtomicInteger();
    private Runnable onExtract = () -> {};

    @Override
    public String name() {
      return "test";
    }

    @Override
    public OcrDocumentResult extract(OcrJob job, OcrExecutionDeadline deadline) {
      calls.incrementAndGet();
      onExtract.run();
      if (unsafeFailure.get()) {
        throw new IllegalStateException("private-path/private-document");
      }
      if (fail.get()) {
        throw new OcrProviderException("TEST_OCR_FAILED", "OCR test failure");
      }
      OcrWord word =
          new OcrWord(
              1, 1, 1, 0, 0, 0, 1, "Test", new BigDecimal("98.50"), new OcrBoundingBox(1, 2, 3, 4));
      if (multiPage.get()) {
        return OcrDocumentResult.of(
            "test",
            "test-v1",
            java.util.List.of(
                new OcrPageResult(
                    1, OcrSourceKind.PDF_TEXT_LAYER, "First page", java.util.List.of(word)),
                new OcrPageResult(
                    2,
                    OcrSourceKind.TESSERACT_TSV,
                    "Second page",
                    java.util.List.of(
                        new OcrWord(
                            1,
                            2,
                            1,
                            0,
                            0,
                            0,
                            1,
                            "Second",
                            new BigDecimal("88.20"),
                            new OcrBoundingBox(5, 6, 7, 8))))));
      }
      return OcrDocumentResult.of(
          "test",
          "test-v1",
          java.util.List.of(
              new OcrPageResult(
                  1,
                  OcrSourceKind.PDF_TEXT_LAYER,
                  "Test OCR text for " + job.fileId(),
                  java.util.List.of(word))));
    }
  }
}
