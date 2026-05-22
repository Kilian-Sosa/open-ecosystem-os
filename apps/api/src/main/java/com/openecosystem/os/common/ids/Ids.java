package com.openecosystem.os.common.ids;

import java.util.UUID;

public final class Ids {

  private Ids() {}

  public static String newId(String prefix) {
    String value = UUID.randomUUID().toString().replace("-", "");
    return prefix + "_" + value;
  }
}
