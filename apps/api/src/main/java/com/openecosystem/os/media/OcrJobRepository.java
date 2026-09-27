package com.openecosystem.os.media;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OcrJobRepository {

  private static final String DETAIL_COLUMNS =
      """
      job_id, file_id, workspace_id, actor_id, source_event_id, correlation_id, content_type,
      storage_key, status, provider, attempt_count, max_attempts, extracted_text,
      extracted_text_length, failure_code, failure_message, queued_at, processing_started_at,
      completed_at, failed_at, next_attempt_at, created_at, updated_at
      """;
  private static final RowMapper<OcrJob> ROW_MAPPER = OcrJobRepository::mapRow;
  private static final RowMapper<OcrJobSourceReference> SOURCE_REFERENCE_ROW_MAPPER =
      (resultSet, rowNumber) ->
          new OcrJobSourceReference(
              resultSet.getString("job_id"),
              resultSet.getString("file_id"),
              resultSet.getString("workspace_id"));
  private static final RowMapper<OcrJobSummary> SUMMARY_ROW_MAPPER =
      OcrJobRepository::mapSummaryRow;

  private final JdbcTemplate jdbcTemplate;

  public OcrJobRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void saveQueued(OcrJob job) {
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
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        job.jobId(),
        job.fileId(),
        job.workspaceId(),
        job.actorId(),
        job.sourceEventId(),
        job.correlationId(),
        job.contentType(),
        job.storageKey(),
        job.status().value(),
        job.provider(),
        job.attemptCount(),
        job.maxAttempts(),
        job.extractedText(),
        job.extractedTextLength(),
        job.failureCode(),
        job.failureMessage(),
        timestamp(job.queuedAt()),
        timestamp(job.processingStartedAt()),
        timestamp(job.completedAt()),
        timestamp(job.failedAt()),
        timestamp(job.nextAttemptAt()),
        timestamp(job.createdAt()),
        timestamp(job.updatedAt()));
  }

  public List<OcrJobSourceReference> listSourceReferencesByWorkspace(String workspaceId) {
    return jdbcTemplate.query(
        """
        select job_id, file_id, workspace_id
        from ocr_jobs
        where workspace_id = ?
        order by created_at desc
        """,
        SOURCE_REFERENCE_ROW_MAPPER,
        workspaceId);
  }

  public Optional<OcrJobSourceReference> findSourceReferenceByIdForWorkspace(
      String jobId, String workspaceId) {
    List<OcrJobSourceReference> results =
        jdbcTemplate.query(
            """
            select job_id, file_id, workspace_id
            from ocr_jobs
            where job_id = ? and workspace_id = ?
            """,
            SOURCE_REFERENCE_ROW_MAPPER,
            jobId,
            workspaceId);
    return results.stream().findFirst();
  }

  public List<OcrJobSummary> findSummariesByIdsForWorkspace(
      Collection<String> jobIds, String workspaceId) {
    if (jobIds.isEmpty()) {
      return List.of();
    }
    String placeholders = String.join(", ", Collections.nCopies(jobIds.size(), "?"));
    Object[] arguments = new Object[jobIds.size() + 1];
    arguments[0] = workspaceId;
    int index = 1;
    for (String jobId : jobIds) {
      arguments[index++] = jobId;
    }
    return jdbcTemplate.query(
        """
        select job_id, file_id, workspace_id, content_type, status, provider, attempt_count,
               max_attempts, extracted_text_length, failure_code, correlation_id, queued_at,
               processing_started_at, completed_at, failed_at, updated_at
        from ocr_jobs
        where workspace_id = ? and job_id in (\
        """
            + placeholders
            + ") order by created_at desc",
        SUMMARY_ROW_MAPPER,
        arguments);
  }

  public Optional<OcrJob> findDetailByIdForWorkspace(String jobId, String workspaceId) {
    List<OcrJob> results =
        jdbcTemplate.query(
            "select " + DETAIL_COLUMNS + " from ocr_jobs where job_id = ? and workspace_id = ?",
            ROW_MAPPER,
            jobId,
            workspaceId);
    return results.stream().findFirst();
  }

  public boolean existsByFileId(String fileId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "select exists (select 1 from ocr_jobs where file_id = ?)", Boolean.class, fileId));
  }

  private static OcrJob mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
    return new OcrJob(
        resultSet.getString("job_id"),
        resultSet.getString("file_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("actor_id"),
        resultSet.getString("source_event_id"),
        resultSet.getString("correlation_id"),
        resultSet.getString("content_type"),
        resultSet.getString("storage_key"),
        OcrJobStatus.fromValue(resultSet.getString("status")),
        resultSet.getString("provider"),
        resultSet.getInt("attempt_count"),
        resultSet.getInt("max_attempts"),
        resultSet.getString("extracted_text"),
        integerOrNull(resultSet, "extracted_text_length"),
        resultSet.getString("failure_code"),
        resultSet.getString("failure_message"),
        instantOrNull(resultSet, "queued_at"),
        instantOrNull(resultSet, "processing_started_at"),
        instantOrNull(resultSet, "completed_at"),
        instantOrNull(resultSet, "failed_at"),
        instantOrNull(resultSet, "next_attempt_at"),
        instantOrNull(resultSet, "created_at"),
        instantOrNull(resultSet, "updated_at"));
  }

  private static OcrJobSummary mapSummaryRow(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new OcrJobSummary(
        resultSet.getString("job_id"),
        resultSet.getString("file_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("content_type"),
        OcrJobStatus.fromValue(resultSet.getString("status")),
        resultSet.getString("provider"),
        resultSet.getInt("attempt_count"),
        resultSet.getInt("max_attempts"),
        integerOrNull(resultSet, "extracted_text_length"),
        resultSet.getString("failure_code"),
        resultSet.getString("correlation_id"),
        instantOrNull(resultSet, "queued_at"),
        instantOrNull(resultSet, "processing_started_at"),
        instantOrNull(resultSet, "completed_at"),
        instantOrNull(resultSet, "failed_at"),
        instantOrNull(resultSet, "updated_at"));
  }

  private static Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private static Instant instantOrNull(ResultSet resultSet, String column) throws SQLException {
    Timestamp timestamp = resultSet.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  private static Integer integerOrNull(ResultSet resultSet, String column) throws SQLException {
    int value = resultSet.getInt(column);
    return resultSet.wasNull() ? null : value;
  }
}
