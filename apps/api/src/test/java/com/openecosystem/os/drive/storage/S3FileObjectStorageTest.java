package com.openecosystem.os.drive.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

class S3FileObjectStorageTest {
  private final S3Client s3 = mock(S3Client.class);
  private final S3FileObjectStorage storage =
      new S3FileObjectStorage(
          s3, new S3StorageProperties(null, null, "synthetic-bucket", null, null, false));

  @Test
  void oversizedObjectRejectedBeforeBuffering() {
    AtomicInteger reads = new AtomicInteger();
    AtomicInteger aborts = new AtomicInteger();
    var input =
        new InputStream() {
          @Override
          public int read() {
            reads.incrementAndGet();
            return 1;
          }
        };
    when(s3.getObject(any(GetObjectRequest.class)))
        .thenReturn(
            new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength(101L).build(),
                AbortableInputStream.create(input, aborts::incrementAndGet)));
    assertThatThrownBy(() -> storage.getEncryptedObject("synthetic/key", 100))
        .isInstanceOf(IllegalStateException.class);
    assertThat(reads).hasValue(0);
    assertThat(aborts).hasValue(1);
  }

  @Test
  void lyingOrMissingContentLengthCannotBypassStreamingBound() {
    for (Long length : new Long[] {1L, null}) {
      AtomicInteger reads = new AtomicInteger();
      AtomicInteger aborts = new AtomicInteger();
      var input =
          new InputStream() {
            @Override
            public int read() {
              reads.incrementAndGet();
              return 1;
            }
          };
      when(s3.getObject(any(GetObjectRequest.class)))
          .thenReturn(
              new ResponseInputStream<>(
                  GetObjectResponse.builder().contentLength(length).build(),
                  AbortableInputStream.create(input, aborts::incrementAndGet)));
      assertThatThrownBy(() -> storage.getEncryptedObject("synthetic/key", 100))
          .isInstanceOf(IllegalStateException.class);
      assertThat(reads.get()).isLessThanOrEqualTo(101);
      assertThat(aborts).hasValue(1);
    }
  }

  @Test
  void returnsExactBoundedCiphertextAndClosesStream() {
    byte[] bytes = {1, 2, 3, 4};
    var input = new ByteArrayInputStream(bytes);
    when(s3.getObject(any(GetObjectRequest.class)))
        .thenReturn(
            new ResponseInputStream<>(
                GetObjectResponse.builder().contentLength(4L).build(),
                AbortableInputStream.create(input)));
    assertThat(storage.getEncryptedObject("synthetic/key", 4)).isEqualTo(bytes);
    var request = org.mockito.ArgumentCaptor.forClass(GetObjectRequest.class);
    verify(s3).getObject(request.capture());
    assertThat(request.getValue().bucket()).isEqualTo("synthetic-bucket");
    assertThat(request.getValue().key()).isEqualTo("synthetic/key");
  }
}
