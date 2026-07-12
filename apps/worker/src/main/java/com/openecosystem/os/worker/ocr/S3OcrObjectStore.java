package com.openecosystem.os.worker.ocr;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

@Component
public class S3OcrObjectStore implements OcrObjectStore {
  private final S3Client s3Client;
  private final WorkerS3Properties properties;

  public S3OcrObjectStore(S3Client s3Client, WorkerS3Properties properties) {
    this.s3Client = s3Client;
    this.properties = properties;
  }

  @Override
  public InputStream open(String storageKey) throws IOException {
    try {
      ResponseInputStream<GetObjectResponse> response =
          s3Client.getObject(
              GetObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build());
      return response;
    } catch (RuntimeException exception) {
      throw new IOException("S3 object could not be read", exception);
    }
  }
}
