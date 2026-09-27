package com.openecosystem.os.invoice;

public enum InvoiceFieldStatus {
  EXTRACTED("extracted"),
  LOW_CONFIDENCE("low_confidence");

  private final String value;

  InvoiceFieldStatus(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
