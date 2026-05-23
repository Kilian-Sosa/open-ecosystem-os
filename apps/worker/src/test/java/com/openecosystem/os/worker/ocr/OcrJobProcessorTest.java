package com.openecosystem.os.worker.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.worker.OpenEcosystemWorkerApplication;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    classes = {
      OpenEcosystemWorkerApplication.class,
      OcrJobProcessorTest.OcrJobProcessorTestConfiguration.class
    })
class OcrJobProcessorTest {

  @Autowired private OcrJobProcessor processor;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TestOcrProvider provider;

  @BeforeEach
  void createSchema() {
    provider.fail.set(false);
    jdbcTemplate.update("drop table if exists event_consumptions");
    jdbcTemplate.update("drop table if exists event_outbox");
    jdbcTemplate.update("drop table if exists audit_records");
    jdbcTemplate.update("drop table if exists ocr_jobs");
    jdbcTemplate.update(
        """
        create table ocr_jobs (
          job_id varchar(64) primary key,
          file_id varchar(64) not null unique,
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
          updated_at timestamp with time zone not null
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
  }

  @Test
  void retriesProviderFailureThenFailsAndEmitsFailedEvent() {
    provider.fail.set(true);
    insertJob("ocr_fail", "file_fail", 2);
    OcrRequestedEvent event = requestedEvent("evt_requested_fail", "ocr_fail", "file_fail");

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
  }

  private void insertJob(String jobId, String fileId, int maxAttempts) {
    Instant now = Instant.parse("2026-05-22T10:00:00Z");
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

    @Override
    public String name() {
      return "test";
    }

    @Override
    public OcrProviderResult extractText(OcrJob job) {
      if (fail.get()) {
        throw new OcrProviderException("TEST_OCR_FAILED", "Test OCR failure");
      }
      return new OcrProviderResult("Test OCR text for " + job.fileId());
    }
  }
}
