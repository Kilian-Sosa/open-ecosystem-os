package com.openecosystem.os.flows;

public enum WorkflowStatus {
  DRAFT("draft"),
  ACTIVE("active"),
  PAUSED("paused");

  private final String value;

  WorkflowStatus(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static WorkflowStatus fromValue(String value) {
    for (WorkflowStatus status : values())
      if (status.value.equals(value)) return status;
    throw new IllegalArgumentException("Unknown workflow status: " + value);
  }
}
