package com.openecosystem.os.worker.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("openecosystem.drive")
public record WorkerDriveProperties(Encryption encryption) {

  public WorkerDriveProperties {
    encryption = encryption == null ? new Encryption(null, null) : encryption;
  }

  public record Encryption(String keyId, String keyBase64) {}
}
