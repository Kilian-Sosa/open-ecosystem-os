package com.openecosystem.os.invoice;

public enum InvoiceExtractionStatus {
  COMPLETED("completed"),
  REVIEW_REQUIRED("review_required");

  private final String value;

  InvoiceExtractionStatus(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
