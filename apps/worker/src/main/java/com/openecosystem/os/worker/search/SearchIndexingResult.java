package com.openecosystem.os.worker.search;

public record SearchIndexingResult(SearchIndexingOutcome outcome, String searchDocumentId) {

  public boolean retry() {
    return outcome == SearchIndexingOutcome.RETRY;
  }

  public boolean deadLetter() {
    return outcome == SearchIndexingOutcome.DEAD_LETTER;
  }
}
