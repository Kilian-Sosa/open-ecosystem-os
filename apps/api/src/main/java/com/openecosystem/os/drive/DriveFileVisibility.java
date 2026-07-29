package com.openecosystem.os.drive;

import java.util.Arrays;

public enum DriveFileVisibility {
  PRIVATE("private"),
  WORKSPACE("workspace");

  private final String value;

  DriveFileVisibility(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static DriveFileVisibility fromValue(String value) {
    return Arrays.stream(values())
        .filter(visibility -> visibility.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown Drive file visibility: " + value));
  }
}
