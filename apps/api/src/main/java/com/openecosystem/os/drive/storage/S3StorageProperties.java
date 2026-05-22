package com.openecosystem.os.drive.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("openecosystem.storage.s3")
public record S3StorageProperties(
    String endpoint,
    String region,
    String bucket,
    String accessKey,
    String secretKey,
    boolean pathStyleAccessEnabled) {

  public S3StorageProperties {
    region = region == null || region.isBlank() ? "local" : region;
    bucket = bucket == null || bucket.isBlank() ? "openecosystem" : bucket;
  }
}
