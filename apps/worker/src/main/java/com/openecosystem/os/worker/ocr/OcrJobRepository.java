package com.openecosystem.os.worker.ocr;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OcrJobRepository {

  private static final RowMapper<OcrJob> ROW_MAPPER = OcrJobRepository::mapRow;

  private final JdbcTemplate jdbcTemplate;

  public OcrJobRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<OcrJob> findById(String jobId) {
    List<OcrJob> results =
        jdbcTemplate.query(
            """
            select *
            from ocr_jobs
            where job_id = ?
            """,
            ROW_MAPPER,
            jobId);
    return results.stream().findFirst();
  }

  public Optional<OcrJob> claimForProcessing(String jobId, String provider, Instant startedAt) {
    int updated =
        jdbcTemplate.update(
            """
            update ocr_jobs
            set status = ?,
                provider = ?,
                attempt_count = attempt_count + 1,
                processing_started_at = ?,
                failure_code = null,
                failure_message = null,
                updated_at = ?
            where job_id = ?
              and status = ?
              and attempt_count < max_attempts
            """,
            OcrJobStatus.PROCESSING.value(),
            provider,
            timestamp(startedAt),
            timestamp(startedAt),
            jobId,
            OcrJobStatus.QUEUED.value());
    return updated == 0 ? Optional.empty() : findById(jobId);
  }

  public void complete(String jobId, String extractedText, Instant completedAt) {
    jdbcTemplate.update(
        """
        update ocr_jobs
        set status = ?,
            extracted_text = ?,
            extracted_text_length = ?,
            completed_at = ?,
            failed_at = null,
            next_attempt_at = null,
            updated_at = ?
        where job_id = ?
        """,
        OcrJobStatus.COMPLETED.value(),
        extractedText,
        extractedText.length(),
        timestamp(completedAt),
        timestamp(completedAt),
        jobId);
  }

  public void queueRetry(
      String jobId,
      String errorCode,
      String errorMessage,
      Instant nextAttemptAt,
      Instant updatedAt) {
    jdbcTemplate.update(
        """
        update ocr_jobs
        set status = ?,
            failure_code = ?,
            failure_message = ?,
            next_attempt_at = ?,
            updated_at = ?
        where job_id = ?
        """,
        OcrJobStatus.QUEUED.value(),
        errorCode,
        trimMessage(errorMessage),
        timestamp(nextAttemptAt),
        timestamp(updatedAt),
        jobId);
  }

  public void fail(String jobId, String errorCode, String errorMessage, Instant failedAt) {
    jdbcTemplate.update(
        """
        update ocr_jobs
        set status = ?,
            failure_code = ?,
            failure_message = ?,
            failed_at = ?,
            next_attempt_at = null,
            updated_at = ?
        where job_id = ?
        """,
        OcrJobStatus.FAILED.value(),
        errorCode,
        trimMessage(errorMessage),
        timestamp(failedAt),
        timestamp(failedAt),
        jobId);
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

  private static String trimMessage(String message) {
    if (message == null) {
      return null;
    }
    return message.length() > 512 ? message.substring(0, 512) : message;
  }
}
