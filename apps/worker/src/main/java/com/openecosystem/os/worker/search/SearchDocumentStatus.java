package com.openecosystem.os.worker.search;

public enum SearchDocumentStatus {
  PENDING("pending"),
  INDEXING("indexing"),
  INDEXED("indexed"),
  FAILED("failed");

  private final String value;

  SearchDocumentStatus(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static SearchDocumentStatus fromValue(String value) {
    for (SearchDocumentStatus status : values())
      if (status.value.equals(value))
        return status;
    throw new IllegalArgumentException("Unsupported search document status: " + value);
  }
}
