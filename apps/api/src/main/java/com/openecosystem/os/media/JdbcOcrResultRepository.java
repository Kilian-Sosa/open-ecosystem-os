package com.openecosystem.os.media;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcOcrResultRepository {

  private final JdbcTemplate jdbcTemplate;

  public JdbcOcrResultRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Transactional
  public void save(OcrDocumentResult result) {
    validateLineage(result);
    jdbcTemplate.update(
        """
        insert into ocr_results (
          ocr_result_id, job_id, file_id, workspace_id, provider, provider_version,
          document_text, page_count, word_count, created_at, updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        result.ocrResultId(),
        result.jobId(),
        result.fileId(),
        result.workspaceId(),
        result.provider(),
        result.providerVersion(),
        result.documentText(),
        result.pageCount(),
        result.wordCount(),
        timestamp(result.createdAt()),
        timestamp(result.updatedAt()));
    for (OcrPageResult page : result.pages()) {
      savePage(page);
      for (OcrWord word : page.words()) {
        saveWord(word);
      }
    }
  }

  public Optional<OcrDocumentResult> findByJobIdForWorkspace(String jobId, String workspaceId) {
    return findHeader(
            """
            select * from ocr_results
            where job_id = ? and workspace_id = ?
            """,
            jobId,
            workspaceId)
        .map(this::loadDocument);
  }

  public Optional<OcrDocumentResult> findByIdForWorkspace(String ocrResultId, String workspaceId) {
    return findHeader(
            """
            select * from ocr_results
            where ocr_result_id = ? and workspace_id = ?
            """,
            ocrResultId,
            workspaceId)
        .map(this::loadDocument);
  }

  public Set<String> findPresentJobIdsForWorkspace(String workspaceId, List<String> jobIds) {
    if (jobIds.isEmpty()) return Set.of();
    String placeholders = jobIds.stream().map(ignored -> "?").collect(Collectors.joining(", "));
    List<Object> arguments = new ArrayList<>();
    arguments.add(workspaceId);
    arguments.addAll(jobIds);
    return new LinkedHashSet<>(
        jdbcTemplate.query(
            "select job_id from ocr_results where workspace_id = ? and job_id in ("
                + placeholders
                + ")",
            (resultSet, rowNumber) -> resultSet.getString("job_id"),
            arguments.toArray()));
  }

  private void savePage(OcrPageResult page) {
    jdbcTemplate.update(
        """
        insert into ocr_result_pages (
          ocr_page_id, ocr_result_id, workspace_id, page_number, source_kind,
          page_text, word_count, created_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        page.ocrPageId(),
        page.ocrResultId(),
        page.workspaceId(),
        page.pageNumber(),
        page.sourceKind(),
        page.pageText(),
        page.wordCount(),
        timestamp(page.createdAt()));
  }

  private void saveWord(OcrWord word) {
    jdbcTemplate.update(
        """
        insert into ocr_result_words (
          ocr_word_id, ocr_page_id, ocr_result_id, workspace_id, reading_order,
          page_number, page_word_order, block_number, paragraph_number, line_number,
          word_number, word_text, confidence, left_px, top_px, width_px, height_px,
          source_kind, created_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        word.ocrWordId(),
        word.ocrPageId(),
        word.ocrResultId(),
        word.workspaceId(),
        word.readingOrder(),
        word.pageNumber(),
        word.pageWordOrder(),
        word.blockNumber(),
        word.paragraphNumber(),
        word.lineNumber(),
        word.wordNumber(),
        word.wordText(),
        word.confidence(),
        word.leftPx(),
        word.topPx(),
        word.widthPx(),
        word.heightPx(),
        word.sourceKind(),
        timestamp(word.createdAt()));
  }

  private Optional<ResultHeader> findHeader(String sql, Object... arguments) {
    return jdbcTemplate.query(sql, JdbcOcrResultRepository::mapHeader, arguments).stream()
        .findFirst();
  }

  private OcrDocumentResult loadDocument(ResultHeader header) {
    List<OcrPageRow> pageRows =
        jdbcTemplate.query(
            """
            select * from ocr_result_pages
            where ocr_result_id = ? and workspace_id = ?
            order by page_number
            """,
            JdbcOcrResultRepository::mapPage,
            header.ocrResultId(),
            header.workspaceId());
    List<OcrWord> words =
        jdbcTemplate.query(
            """
            select * from ocr_result_words
            where ocr_result_id = ? and workspace_id = ?
            order by reading_order
            """,
            JdbcOcrResultRepository::mapWord,
            header.ocrResultId(),
            header.workspaceId());
    Map<String, List<OcrWord>> wordsByPage = new LinkedHashMap<>();
    for (OcrWord word : words) {
      wordsByPage.computeIfAbsent(word.ocrPageId(), ignored -> new ArrayList<>()).add(word);
    }
    List<OcrPageResult> pages =
        pageRows.stream()
            .map(
                page ->
                    new OcrPageResult(
                        page.ocrPageId(),
                        page.ocrResultId(),
                        page.workspaceId(),
                        page.pageNumber(),
                        page.sourceKind(),
                        page.pageText(),
                        page.wordCount(),
                        page.createdAt(),
                        wordsByPage.getOrDefault(page.ocrPageId(), List.of())))
            .toList();
    return new OcrDocumentResult(
        header.ocrResultId(),
        header.jobId(),
        header.fileId(),
        header.workspaceId(),
        header.provider(),
        header.providerVersion(),
        header.documentText(),
        header.pageCount(),
        header.wordCount(),
        header.createdAt(),
        header.updatedAt(),
        pages);
  }

  private static ResultHeader mapHeader(ResultSet resultSet, int rowNumber) throws SQLException {
    return new ResultHeader(
        resultSet.getString("ocr_result_id"),
        resultSet.getString("job_id"),
        resultSet.getString("file_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("provider"),
        resultSet.getString("provider_version"),
        resultSet.getString("document_text"),
        resultSet.getInt("page_count"),
        resultSet.getInt("word_count"),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant());
  }

  private static OcrPageRow mapPage(ResultSet resultSet, int rowNumber) throws SQLException {
    return new OcrPageRow(
        resultSet.getString("ocr_page_id"),
        resultSet.getString("ocr_result_id"),
        resultSet.getString("workspace_id"),
        resultSet.getInt("page_number"),
        resultSet.getString("source_kind"),
        resultSet.getString("page_text"),
        resultSet.getInt("word_count"),
        resultSet.getTimestamp("created_at").toInstant());
  }

  private static OcrWord mapWord(ResultSet resultSet, int rowNumber) throws SQLException {
    return new OcrWord(
        resultSet.getString("ocr_word_id"),
        resultSet.getString("ocr_page_id"),
        resultSet.getString("ocr_result_id"),
        resultSet.getString("workspace_id"),
        resultSet.getInt("reading_order"),
        resultSet.getInt("page_number"),
        resultSet.getInt("page_word_order"),
        resultSet.getInt("block_number"),
        resultSet.getInt("paragraph_number"),
        resultSet.getInt("line_number"),
        resultSet.getInt("word_number"),
        resultSet.getString("word_text"),
        resultSet.getBigDecimal("confidence"),
        integerOrNull(resultSet, "left_px"),
        integerOrNull(resultSet, "top_px"),
        integerOrNull(resultSet, "width_px"),
        integerOrNull(resultSet, "height_px"),
        resultSet.getString("source_kind"),
        resultSet.getTimestamp("created_at").toInstant());
  }

  private static void validateLineage(OcrDocumentResult result) {
    for (OcrPageResult page : result.pages()) {
      if (!result.ocrResultId().equals(page.ocrResultId())
          || !result.workspaceId().equals(page.workspaceId())) {
        throw new IllegalArgumentException("OCR result page lineage is invalid");
      }
      for (OcrWord word : page.words()) {
        if (!page.ocrPageId().equals(word.ocrPageId())
            || !result.ocrResultId().equals(word.ocrResultId())
            || !result.workspaceId().equals(word.workspaceId())
            || page.pageNumber() != word.pageNumber()) {
          throw new IllegalArgumentException("OCR result word lineage is invalid");
        }
      }
    }
  }

  private static Timestamp timestamp(Instant value) {
    return Timestamp.from(value);
  }

  private static Integer integerOrNull(ResultSet resultSet, String column) throws SQLException {
    int value = resultSet.getInt(column);
    return resultSet.wasNull() ? null : value;
  }

  private record ResultHeader(
      String ocrResultId,
      String jobId,
      String fileId,
      String workspaceId,
      String provider,
      String providerVersion,
      String documentText,
      int pageCount,
      int wordCount,
      Instant createdAt,
      Instant updatedAt) {}

  private record OcrPageRow(
      String ocrPageId,
      String ocrResultId,
      String workspaceId,
      int pageNumber,
      String sourceKind,
      String pageText,
      int wordCount,
      Instant createdAt) {}
}
