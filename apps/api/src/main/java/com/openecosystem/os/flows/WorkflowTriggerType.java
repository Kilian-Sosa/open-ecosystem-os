package com.openecosystem.os.flows;

public enum WorkflowTriggerType {
  MANUAL("manual"),
  EVENT("event");

  private final String value;

  WorkflowTriggerType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
