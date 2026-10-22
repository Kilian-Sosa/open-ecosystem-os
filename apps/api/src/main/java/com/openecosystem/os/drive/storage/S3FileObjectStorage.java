package com.openecosystem.os.drive.storage;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class S3FileObjectStorage implements FileObjectStorage {

  private static final String ENCRYPTED_CONTENT_TYPE = "application/octet-stream";

  private final S3Client s3Client;
  private final S3StorageProperties properties;
  private final AtomicBoolean bucketChecked = new AtomicBoolean(false);

  public S3FileObjectStorage(S3Client s3Client, S3StorageProperties properties) {
    this.s3Client = s3Client;
    this.properties = properties;
  }

  @Override
  public byte[] getEncryptedObject(String storageKey, long maxCiphertextBytes) {
    if (maxCiphertextBytes <= 0 || maxCiphertextBytes >= Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Invalid encrypted object size limit");
    }
    var request = GetObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build();
    try (var stream = s3Client.getObject(request)) {
      try {
        Long length = stream.response().contentLength();
        if (length != null && (length < 0 || length > maxCiphertextBytes)) {
          throw new IllegalStateException("Encrypted object exceeds size limit");
        }
        byte[] bytes = stream.readNBytes((int) maxCiphertextBytes + 1);
        if (bytes.length > maxCiphertextBytes || (length != null && bytes.length != length)) {
          throw new IllegalStateException("Encrypted object size is invalid");
        }
        return bytes;
      } catch (IOException | RuntimeException exception) {
        stream.abort();
        throw new IllegalStateException("Encrypted object could not be read");
      }
    } catch (IOException exception) {
      throw new IllegalStateException("Encrypted object could not be read");
    }
  }

  @Override
  public void putEncryptedObject(
      String storageKey, byte[] encryptedContent, String originalContentType, String contentIv) {
    ensureBucket();
    PutObjectRequest request =
        PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(storageKey)
            .contentType(ENCRYPTED_CONTENT_TYPE)
            .metadata(
                Map.of(
                    "openecosystem-encrypted",
                    "true",
                    "openecosystem-original-content-type",
                    originalContentType,
                    "openecosystem-content-iv",
                    contentIv))
            .build();

    s3Client.putObject(request, RequestBody.fromBytes(encryptedContent));
  }

  @Override
  public void deleteObjectIfExists(String storageKey) {
    try {
      s3Client.deleteObject(
          DeleteObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build());
    } catch (S3Exception ignored) {
      // Best-effort cleanup after a database failure; do not mask the original upload error.
    }
  }

  @Override
  public void deleteEncryptedObject(String storageKey) {
    s3Client.deleteObject(
        DeleteObjectRequest.builder().bucket(properties.bucket()).key(storageKey).build());
  }

  private void ensureBucket() {
    if (bucketChecked.get()) {
      return;
    }

    try {
      s3Client.headBucket(HeadBucketRequest.builder().bucket(properties.bucket()).build());
      bucketChecked.set(true);
    } catch (NoSuchBucketException exception) {
      createBucket();
    } catch (S3Exception exception) {
      if (exception.statusCode() == 404) {
        createBucket();
      } else {
        throw exception;
      }
    }
  }

  private void createBucket() {
    s3Client.createBucket(CreateBucketRequest.builder().bucket(properties.bucket()).build());
    bucketChecked.set(true);
  }
}
