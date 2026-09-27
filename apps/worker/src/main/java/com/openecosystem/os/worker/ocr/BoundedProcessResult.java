package com.openecosystem.os.worker.ocr;

import java.nio.charset.StandardCharsets;

public record BoundedProcessResult(int exitCode, byte[] stdout) {

  public BoundedProcessResult {
    stdout = stdout == null ? new byte[0] : stdout.clone();
  }

  @Override
  public byte[] stdout() {
    return stdout.clone();
  }

  public String stdoutUtf8() {
    return new String(stdout, StandardCharsets.UTF_8);
  }
}
