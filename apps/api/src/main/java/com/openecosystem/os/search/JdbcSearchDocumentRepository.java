package com.openecosystem.os.search;

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
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSearchDocumentRepository {

  private static final RowMapper<SearchDocument> ROW_MAPPER = JdbcSearchDocumentRepository::mapRow;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcSearchDocumentRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public void save(SearchDocument document) {
    jdbcTemplate.update(
        """
        insert into search_documents (
          search_document_id,
          workspace_id,
          source_type,
          source_id,
          title,
          summary,
          content,
          resource_href,
          correlation_id,
          status,
          attempt_count,
          max_attempts,
          failure_code,
          failure_message,
          metadata_json,
          created_at,
          updated_at,
          indexed_at,
          failed_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        document.searchDocumentId(),
        document.workspaceId(),
        document.sourceType(),
        document.sourceId(),
        document.title(),
        document.summary(),
        document.content(),
        document.resourceHref(),
        document.correlationId(),
        document.status().value(),
        document.attemptCount(),
        document.maxAttempts(),
        document.failureCode(),
        document.failureMessage(),
        json(document.metadata()),
        timestamp(document.createdAt()),
        timestamp(document.updatedAt()),
        timestamp(document.indexedAt()),
        timestamp(document.failedAt()));
  }

  public Optional<SearchDocument> findBySource(
      String workspaceId, String sourceType, String sourceId) {
    List<SearchDocument> results =
        jdbcTemplate.query(
            """
            select *
            from search_documents
            where workspace_id = ? and source_type = ? and source_id = ?
            """,
            ROW_MAPPER,
            workspaceId,
            sourceType,
            sourceId);
    return results.stream().findFirst();
  }

  public Optional<SearchDocument> findLatestByCorrelationId(
      String workspaceId, String correlationId) {
    List<SearchDocument> results =
        jdbcTemplate.query(
            """
            select *
            from search_documents
            where workspace_id = ? and correlation_id = ?
            order by created_at desc
            limit 1
            """,
            ROW_MAPPER,
            workspaceId,
            correlationId);
    return results.stream().findFirst();
  }

  public List<SearchDocument> searchLocal(String workspaceId, String query) {
    String normalized = "%" + (query == null ? "" : query.trim().toLowerCase()) + "%";
    return jdbcTemplate.query(
        """
        select *
        from search_documents
        where workspace_id = ?
          and (
            lower(title) like ?
            or lower(summary) like ?
            or lower(content) like ?
          )
        order by created_at desc
        limit 25
        """,
        ROW_MAPPER,
        workspaceId,
        normalized,
        normalized,
        normalized);
  }

  private String json(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(
          "Search document metadata could not be serialized", exception);
    }
  }

  private static SearchDocument mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
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

  private static JsonNode jsonNode(String json) {
    try {
      return new ObjectMapper().readTree(json);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Search document metadata could not be parsed", exception);
    }
  }

  private static Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private static Instant instantOrNull(ResultSet resultSet, String column) throws SQLException {
    Timestamp timestamp = resultSet.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }
}
