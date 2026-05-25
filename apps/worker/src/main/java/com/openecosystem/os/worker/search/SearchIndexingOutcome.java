package com.openecosystem.os.worker.search;

public enum SearchIndexingOutcome {
  INDEXED,
  RETRY,
  DEAD_LETTER,
  NO_OP
}
