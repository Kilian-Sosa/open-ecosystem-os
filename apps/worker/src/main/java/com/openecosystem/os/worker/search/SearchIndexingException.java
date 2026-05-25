package com.openecosystem.os.worker.search;

public class SearchIndexingException extends RuntimeException {

  private final String code;

  public SearchIndexingException(String code, String message) {
    super(message);
    this.code = code == null || code.isBlank() ? "SEARCH_INDEXING_FAILED" : code;
  }

  public String code() {
    return code;
  }
}
