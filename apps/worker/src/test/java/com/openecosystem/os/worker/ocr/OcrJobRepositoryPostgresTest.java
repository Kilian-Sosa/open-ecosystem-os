package com.openecosystem.os.worker.ocr;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OcrJobRepositoryPostgresTest {

  private static final Instant STARTED_AT = Instant.parse("2026-07-29T12:00:00Z");

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  private JdbcTemplate jdbcTemplate;
  private OcrJobRepository repository;

  @BeforeEach
  void createSchema() {
    jdbcTemplate =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
    repository = new OcrJobRepository(jdbcTemplate);
    jdbcTemplate.execute("drop table if exists ocr_jobs");
    jdbcTemplate.execute("drop table if exists drive_files");
    jdbcTemplate.execute(
        """
        create table drive_files (
          file_id varchar(64) primary key,
          workspace_id varchar(128) not null,
          unique (file_id, workspace_id)
        )
        """);
    jdbcTemplate.execute(
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
  }

  @AfterEach
  void cleanSchema() {
    jdbcTemplate.execute("drop table if exists ocr_jobs");
    jdbcTemplate.execute("drop table if exists drive_files");
  }

  @Test
  void allowsExactlyOneConcurrentOwnerForAQueuedClaim() throws Exception {
    insertJob("ocr_concurrent", "file_concurrent", "queued", null);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
      List<Future<Optional<OcrJob>>> claims =
          List.of(
              executor.submit(() -> claimWhenReleased(ready, start)),
              executor.submit(() -> claimWhenReleased(ready, start)));
      assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
      start.countDown();

      long successfulClaims = 0;
      for (Future<Optional<OcrJob>> claim : claims) {
        if (claim.get().isPresent()) {
          successfulClaims++;
        }
      }
      assertThat(successfulClaims).isEqualTo(1);
    }

    assertThat(jdbcTemplate.queryForObject("select attempt_count from ocr_jobs", Integer.class))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("select status from ocr_jobs", String.class))
        .isEqualTo("processing");
  }

  @Test
  void reclaimsOnlyClaimsStrictlyOlderThanTheLegalClaimBoundary() {
    insertJob("ocr_live", "file_live", "processing", STARTED_AT);

    assertThat(
            repository.claimForProcessing(
                "ocr_live", "tesseract", STARTED_AT.plusSeconds(720), STARTED_AT))
        .isEmpty();

    assertThat(
            repository.claimForProcessing(
                "ocr_live", "tesseract", STARTED_AT.plusSeconds(720), STARTED_AT.plusSeconds(1)))
        .isPresent();
  }

  private Optional<OcrJob> claimWhenReleased(CountDownLatch ready, CountDownLatch start)
      throws InterruptedException {
    ready.countDown();
    start.await();
    return repository.claimForProcessing(
        "ocr_concurrent", "tesseract", STARTED_AT, STARTED_AT.minusSeconds(1));
  }

  private void insertJob(String jobId, String fileId, String status, Instant processingStartedAt) {
    jdbcTemplate.update(
        "insert into drive_files (file_id, workspace_id) values (?, 'wrk_test')", fileId);
    jdbcTemplate.update(
        """
        insert into ocr_jobs (
          job_id, file_id, workspace_id, actor_id, source_event_id, correlation_id,
          content_type, storage_key, status, provider, attempt_count, max_attempts,
          queued_at, processing_started_at, created_at, updated_at
        ) values (?, ?, 'wrk_test', 'usr_test', 'evt_test', 'corr_test', 'application/pdf',
          'workspaces/wrk_test/drive/file/original', ?, null, 0, 3, ?, ?, ?, ?)
        """,
        jobId,
        fileId,
        status,
        java.sql.Timestamp.from(STARTED_AT),
        processingStartedAt == null ? null : java.sql.Timestamp.from(processingStartedAt),
        java.sql.Timestamp.from(STARTED_AT),
        java.sql.Timestamp.from(STARTED_AT));
  }
}
