package com.openecosystem.os.flows;

public enum WorkflowStepExecutionStatus {
  RUNNING("running"),
  COMPLETED("completed"),
  FAILED("failed");

  private final String value;

  WorkflowStepExecutionStatus(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static WorkflowStepExecutionStatus fromValue(String value) {
    for (WorkflowStepExecutionStatus status : values())
      if (status.value.equals(value)) return status;
    throw new IllegalArgumentException("Unknown workflow step execution status: " + value);
  }
}
