package com.openecosystem.os.worker.search;

public class SearchSubmissionException extends RuntimeException {
  private final boolean ambiguous;

  public SearchSubmissionException(boolean ambiguous) {
    super(
        ambiguous
            ? "Remote search submission outcome is unknown"
            : "Remote search submission was rejected");
    this.ambiguous = ambiguous;
  }

  public boolean ambiguous() {
    return ambiguous;
  }
}
