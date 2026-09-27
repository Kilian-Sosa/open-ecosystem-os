package com.openecosystem.os.worker.ocr;

import com.openecosystem.os.worker.common.Ids;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OcrResultRepository {

  private final JdbcTemplate jdbcTemplate;

  public OcrResultRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void save(String resultId, OcrJob job, OcrDocumentResult result, Instant createdAt) {
    jdbcTemplate.update(
        """
        insert into ocr_results (
          ocr_result_id, job_id, workspace_id, file_id, provider, provider_version,
          document_text, page_count, word_count, created_at, updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        resultId,
        job.jobId(),
        job.workspaceId(),
        job.fileId(),
        result.provider(),
        result.providerVersion(),
        result.documentText(),
        result.pages().size(),
        wordCount(result),
        Timestamp.from(createdAt),
        Timestamp.from(createdAt));
    for (OcrPageResult page : result.pages()) {
      String pageId = Ids.newId("ocrpg");
      jdbcTemplate.update(
          """
          insert into ocr_result_pages (
            ocr_page_id, ocr_result_id, workspace_id, page_number, source_kind, page_text,
            word_count, created_at
          ) values (?, ?, ?, ?, ?, ?, ?, ?)
          """,
          pageId,
          resultId,
          job.workspaceId(),
          page.pageNumber(),
          page.sourceKind().value(),
          page.pageText(),
          page.words().size(),
          Timestamp.from(createdAt));
      for (OcrWord word : page.words()) {
        saveWord(Ids.newId("ocrword"), pageId, resultId, job.workspaceId(), page, word, createdAt);
      }
    }
  }

  private int wordCount(OcrDocumentResult result) {
    return result.pages().stream().mapToInt(page -> page.words().size()).sum();
  }

  private void saveWord(
      String wordId,
      String pageId,
      String resultId,
      String workspaceId,
      OcrPageResult page,
      OcrWord word,
      Instant createdAt) {
    OcrBoundingBox box = word.boundingBox();
    jdbcTemplate.update(
        """
        insert into ocr_result_words (
          ocr_word_id, ocr_page_id, ocr_result_id, workspace_id, reading_order, page_number,
          page_word_order, block_number,
          paragraph_number, line_number, word_number, word_text, confidence,
          left_px, top_px, width_px, height_px, source_kind, created_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        wordId,
        pageId,
        resultId,
        workspaceId,
        word.readingOrder(),
        word.pageNumber(),
        word.pageWordOrder(),
        word.blockNumber(),
        word.paragraphNumber(),
        word.lineNumber(),
        word.wordNumber(),
        word.text(),
        confidence(word.confidence()),
        box == null ? null : box.left(),
        box == null ? null : box.top(),
        box == null ? null : box.width(),
        box == null ? null : box.height(),
        page.sourceKind().value(),
        Timestamp.from(createdAt));
  }

  private BigDecimal confidence(BigDecimal value) {
    return value;
  }
}
