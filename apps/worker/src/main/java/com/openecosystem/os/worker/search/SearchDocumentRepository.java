package com.openecosystem.os.worker.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SearchDocumentRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public SearchDocumentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public Optional<SearchDocument> findById(String searchDocumentId) {
    List<SearchDocument> results =
        jdbcTemplate.query(
            """
            select *
            from search_documents
            where search_document_id = ?
            """,
            this::mapRow,
            searchDocumentId);
    return results.stream().findFirst();
  }

  public Optional<SearchDocument> startAttempt(String searchDocumentId, Instant now) {
    jdbcTemplate.update(
        """
        update search_documents
        set status = ?,
            attempt_count = attempt_count + 1,
            updated_at = ?
        where search_document_id = ? and status in (?, ?)
        """,
        SearchDocumentStatus.INDEXING.value(),
        Timestamp.from(now),
        searchDocumentId,
        SearchDocumentStatus.PENDING.value(),
        SearchDocumentStatus.INDEXING.value());
    return findById(searchDocumentId);
  }

  public void queueRetry(
      String searchDocumentId, String errorCode, String errorMessage, Instant now) {
    jdbcTemplate.update(
        """
        update search_documents
        set status = ?,
            failure_code = ?,
            failure_message = ?,
            updated_at = ?
        where search_document_id = ?
        """,
        SearchDocumentStatus.PENDING.value(),
        errorCode,
        trim(errorMessage),
        Timestamp.from(now),
        searchDocumentId);
  }

  public void complete(String searchDocumentId, Instant now) {
    jdbcTemplate.update(
        """
        update search_documents
        set status = ?,
            failure_code = null,
            failure_message = null,
            indexed_at = ?,
            failed_at = null,
            updated_at = ?
        where search_document_id = ?
        """,
        SearchDocumentStatus.INDEXED.value(),
        Timestamp.from(now),
        Timestamp.from(now),
        searchDocumentId);
  }

  public void fail(String searchDocumentId, String errorCode, String errorMessage, Instant now) {
    jdbcTemplate.update(
        """
        update search_documents
        set status = ?,
            failure_code = ?,
            failure_message = ?,
            failed_at = ?,
            updated_at = ?
        where search_document_id = ?
        """,
        SearchDocumentStatus.FAILED.value(),
        errorCode,
        trim(errorMessage),
        Timestamp.from(now),
        Timestamp.from(now),
        searchDocumentId);
  }

  private SearchDocument mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
    return new SearchDocument(
        resultSet.getString("search_document_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("source_type"),
        resultSet.getString("source_id"),
        resultSet.getString("title"),
        resultSet.getString("summary"),
        resultSet.getString("content"),
        resultSet.getString("resource_href"),
        resultSet.getString("correlation_id"),
        SearchDocumentStatus.fromValue(resultSet.getString("status")),
        resultSet.getInt("attempt_count"),
        resultSet.getInt("max_attempts"),
        resultSet.getString("failure_code"),
        resultSet.getString("failure_message"),
        jsonNode(resultSet.getString("metadata_json")),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant(),
        instantOrNull(resultSet, "indexed_at"),
        instantOrNull(resultSet, "failed_at"));
  }

  private JsonNode jsonNode(String json) {
    try {
      return objectMapper.readTree(json);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Search document metadata could not be parsed", exception);
    }
  }

  private static Instant instantOrNull(ResultSet resultSet, String column) throws SQLException {
    Timestamp timestamp = resultSet.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  private static String trim(String value) {
    if (value == null)
      return null;
    return value.length() > 512 ? value.substring(0, 512) : value;
  }
}
