package com.openecosystem.os.worker.ocr;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("openecosystem.storage.s3")
public record WorkerS3Properties(
    String endpoint,
    String region,
    String bucket,
    String accessKey,
    String secretKey,
    boolean pathStyleAccessEnabled) {
  public WorkerS3Properties {
    endpoint = endpoint == null || endpoint.isBlank() ? "http://localhost:9000" : endpoint;
    region = region == null || region.isBlank() ? "local" : region;
    bucket = bucket == null || bucket.isBlank() ? "openecosystem" : bucket;
    accessKey = accessKey == null || accessKey.isBlank() ? "openecosystem" : accessKey;
    secretKey = secretKey == null || secretKey.isBlank() ? "openecosystem_dev_password" : secretKey;
  }
}
