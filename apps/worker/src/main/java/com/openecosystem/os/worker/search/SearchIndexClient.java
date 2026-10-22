package com.openecosystem.os.worker.search;

import java.time.Instant;
import java.util.OptionalLong;

public interface SearchIndexClient {
  void index(SearchDocument document);

  long submitUpsert(SearchDocument document, String operationId);

  long submitRemoval(String documentId, String operationId);

  TaskState taskState(long taskUid);

  TaskLookup findTask(String operationId, Instant startedAt, Long cursor);

  boolean isAbsent(String documentId);

  enum TaskState {
    PENDING,
    SUCCEEDED,
    FAILED
  }

  record TaskLookup(OptionalLong taskUid, Long nextCursor) {}
}
