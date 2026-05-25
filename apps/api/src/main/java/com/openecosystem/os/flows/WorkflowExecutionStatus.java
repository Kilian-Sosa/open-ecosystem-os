package com.openecosystem.os.flows;

public enum WorkflowExecutionStatus {
  RUNNING("running"),
  COMPLETED("completed"),
  FAILED("failed");

  private final String value;

  WorkflowExecutionStatus(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static WorkflowExecutionStatus fromValue(String value) {
    for (WorkflowExecutionStatus status : values()) {
      if (status.value.equals(value)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Unknown workflow execution status: " + value);
  }
}
