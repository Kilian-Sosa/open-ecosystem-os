package com.openecosystem.os.knowledge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcKnowledgeItemRepository {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcKnowledgeItemRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public void save(KnowledgeItem item) {
    jdbcTemplate.update(
        """
        insert into knowledge_items (
          knowledge_item_id,
          workspace_id,
          title,
          summary,
          source_file_id,
          source_ocr_job_id,
          source_workflow_execution_id,
          source_event_id,
          metadata_json,
          created_by,
          correlation_id,
          idempotency_key,
          created_at,
          updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        item.knowledgeItemId(),
        item.workspaceId(),
        item.title(),
        item.summary(),
        item.sourceFileId(),
        item.sourceOcrJobId(),
        item.sourceWorkflowExecutionId(),
        item.sourceEventId(),
        json(item.metadata()),
        item.createdBy(),
        item.correlationId(),
        item.idempotencyKey(),
        Timestamp.from(item.createdAt()),
        Timestamp.from(item.updatedAt()));
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Knowledge item metadata could not be serialized", exception);
    }
  }
}
