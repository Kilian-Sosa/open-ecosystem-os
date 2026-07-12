package com.openecosystem.os.worker.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DriveOcrSourceReaderTest {

  private static final String KEY_ID = "key-test";
  private static final byte[] KEY_BYTES =
      "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
  private static final String KEY_BASE64 = Base64.getEncoder().encodeToString(KEY_BYTES);

  @TempDir Path tempRoot;

  @Test
  void rejectsMetadataMismatchBeforeReadingPrivateObject() {
    EncryptedFixture fixture = encrypted("safe test input");
    DriveOcrFileMetadata metadata = metadata(fixture, "image/png", "wrong/storage/key");
    AtomicInteger objectReads = new AtomicInteger();
    DriveOcrSourceReader reader =
        reader(
            (fileId, workspaceId) -> Optional.of(metadata),
            storageKey -> {
              objectReads.incrementAndGet();
              return new ByteArrayInputStream(fixture.ciphertext());
            },
            1024);

    assertFailure(
        () -> reader.open(job("application/pdf", expectedStorageKey())),
        "OCR_SOURCE_METADATA_MISMATCH",
        "OCR source metadata did not match the job");
    assertThat(objectReads).hasValue(0);
    assertTemporaryRootEmpty();
  }

  @Test
  void rejectsUnsupportedEncryptionOrConfiguredKeyBeforeObjectRead() {
    EncryptedFixture fixture = encrypted("safe test input");
    DriveOcrFileMetadata metadata =
        new DriveOcrFileMetadata(
            "file_123",
            "wrk_123",
            "application/pdf",
            fixture.plaintext().length,
            sha256(fixture.plaintext()),
            expectedStorageKey(),
            "AES-128-CBC",
            "different-key",
            fixture.ivBase64());
    AtomicInteger objectReads = new AtomicInteger();
    DriveOcrSourceReader reader =
        reader(
            (fileId, workspaceId) -> Optional.of(metadata),
            storageKey -> {
              objectReads.incrementAndGet();
              return new ByteArrayInputStream(fixture.ciphertext());
            },
            1024);

    assertFailure(
        () -> reader.open(job("application/pdf", expectedStorageKey())),
        "OCR_SOURCE_ENCRYPTION_MISMATCH",
        "OCR source encryption metadata was invalid");
    assertThat(objectReads).hasValue(0);
    assertTemporaryRootEmpty();
  }

  @Test
  void streamsAuthenticatesAndVerifiesSourceIntoSanitizedTemporaryFile() throws IOException {
    EncryptedFixture fixture = encrypted("plain test document");
    AtomicBoolean objectClosed = new AtomicBoolean(false);
    InputStream object =
        new ByteArrayInputStream(fixture.ciphertext()) {
          @Override
          public void close() throws IOException {
            objectClosed.set(true);
            super.close();
          }
        };
    DriveOcrSourceReader reader =
        reader(
            (fileId, workspaceId) ->
                Optional.of(metadata(fixture, "application/pdf", expectedStorageKey())),
            storageKey -> object,
            1024);

    Path plaintextPath;
    try (OcrSource source = reader.open(job("application/pdf", expectedStorageKey()))) {
      plaintextPath = source.path();
      assertThat(source.contentType()).isEqualTo("application/pdf");
      assertThat(source.path().getFileName().toString()).isEqualTo("input.bin");
      assertThat(source.path().toString()).doesNotContain("invoice", "file_123");
      assertThat(Files.readAllBytes(source.path())).isEqualTo(fixture.plaintext());
      assertThat(objectClosed).isTrue();
    }

    assertThat(Files.exists(plaintextPath)).isFalse();
    assertTemporaryRootEmpty();
  }

  @Test
  void enforcesEncryptedDownloadCapAndCleansPartialOutput() {
    EncryptedFixture fixture = encrypted("x");
    byte[] oversizedCiphertext = new byte[80];
    AtomicBoolean objectClosed = new AtomicBoolean(false);
    InputStream object =
        new ByteArrayInputStream(oversizedCiphertext) {
          @Override
          public void close() throws IOException {
            objectClosed.set(true);
            super.close();
          }
        };
    DriveOcrSourceReader reader =
        reader(
            (fileId, workspaceId) ->
                Optional.of(metadata(fixture, "application/pdf", expectedStorageKey())),
            storageKey -> object,
            32);

    assertFailure(
        () -> reader.open(job("application/pdf", expectedStorageKey())),
        "OCR_SOURCE_SIZE_LIMIT",
        "OCR source exceeded the configured size limit");
    assertThat(objectClosed).isTrue();
    assertTemporaryRootEmpty();
  }

  @Test
  void rejectsAesGcmAuthenticationFailureAndCleansPartialOutput() {
    EncryptedFixture fixture = encrypted("authenticated input");
    byte[] tampered = fixture.ciphertext().clone();
    tampered[tampered.length - 1] ^= 1;
    DriveOcrSourceReader reader =
        reader(
            (fileId, workspaceId) ->
                Optional.of(metadata(fixture, "application/pdf", expectedStorageKey())),
            storageKey -> new ByteArrayInputStream(tampered),
            1024);

    assertFailure(
        () -> reader.open(job("application/pdf", expectedStorageKey())),
        "OCR_SOURCE_DECRYPTION_FAILED",
        "OCR source could not be authenticated");
    assertTemporaryRootEmpty();
  }

  @Test
  void rejectsPlaintextSizeMismatch() {
    EncryptedFixture fixture = encrypted("size checked input");
    DriveOcrFileMetadata metadata =
        new DriveOcrFileMetadata(
            "file_123",
            "wrk_123",
            "application/pdf",
            fixture.plaintext().length + 1L,
            sha256(fixture.plaintext()),
            expectedStorageKey(),
            "AES-256-GCM",
            KEY_ID,
            fixture.ivBase64());
    DriveOcrSourceReader reader =
        reader(
            (fileId, workspaceId) -> Optional.of(metadata),
            storageKey -> new ByteArrayInputStream(fixture.ciphertext()),
            1024);

    assertFailure(
        () -> reader.open(job("application/pdf", expectedStorageKey())),
        "OCR_SOURCE_SIZE_MISMATCH",
        "OCR source size verification failed");
    assertTemporaryRootEmpty();
  }

  @Test
  void rejectsPlaintextChecksumMismatchWithoutExposingChecksum() {
    EncryptedFixture fixture = encrypted("checksum checked input");
    DriveOcrFileMetadata metadata =
        new DriveOcrFileMetadata(
            "file_123",
            "wrk_123",
            "application/pdf",
            fixture.plaintext().length,
            "f".repeat(64),
            expectedStorageKey(),
            "AES-256-GCM",
            KEY_ID,
            fixture.ivBase64());
    DriveOcrSourceReader reader =
        reader(
            (fileId, workspaceId) -> Optional.of(metadata),
            storageKey -> new ByteArrayInputStream(fixture.ciphertext()),
            1024);

    assertFailure(
        () -> reader.open(job("application/pdf", expectedStorageKey())),
        "OCR_SOURCE_CHECKSUM_MISMATCH",
        "OCR source checksum verification failed");
    assertTemporaryRootEmpty();
  }

  @Test
  void sanitizesObjectStoreFailureAndCleansOwnedDirectory() {
    EncryptedFixture fixture = encrypted("safe test input");
    DriveOcrSourceReader reader =
        reader(
            (fileId, workspaceId) ->
                Optional.of(metadata(fixture, "application/pdf", expectedStorageKey())),
            storageKey -> {
              throw new IOException("private bucket, object key, and credential details");
            },
            1024);

    assertFailure(
        () -> reader.open(job("application/pdf", expectedStorageKey())),
        "OCR_SOURCE_READ_FAILED",
        "OCR source could not be read");
    assertTemporaryRootEmpty();
  }

  private DriveOcrSourceReader reader(
      DriveOcrMetadataRepository metadataRepository,
      OcrObjectStore objectStore,
      long maxInputBytes) {
    return new DriveOcrSourceReader(
        metadataRepository, objectStore, KEY_ID, KEY_BASE64, maxInputBytes, tempRoot);
  }

  private DriveOcrFileMetadata metadata(
      EncryptedFixture fixture, String contentType, String storageKey) {
    return new DriveOcrFileMetadata(
        "file_123",
        "wrk_123",
        contentType,
        fixture.plaintext().length,
        sha256(fixture.plaintext()),
        storageKey,
        "AES-256-GCM",
        KEY_ID,
        fixture.ivBase64());
  }

  private OcrJob job(String contentType, String storageKey) {
    Instant now = Instant.parse("2026-07-10T10:00:00Z");
    return new OcrJob(
        "ocr_123",
        "file_123",
        "wrk_123",
        "usr_123",
        "evt_uploaded",
        "corr_123",
        contentType,
        storageKey,
        OcrJobStatus.PROCESSING,
        "tesseract",
        1,
        3,
        null,
        null,
        null,
        null,
        now,
        now,
        null,
        null,
        null,
        now,
        now);
  }

  private String expectedStorageKey() {
    return "workspaces/wrk_123/drive/file_123/original";
  }

  private EncryptedFixture encrypted(String value) {
    byte[] plaintext = value.getBytes(StandardCharsets.UTF_8);
    byte[] iv = "123456789012".getBytes(StandardCharsets.UTF_8);
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY_BYTES, "AES"), new GCMParameterSpec(128, iv));
      return new EncryptedFixture(
          plaintext, cipher.doFinal(plaintext), Base64.getEncoder().encodeToString(iv));
    } catch (GeneralSecurityException exception) {
      throw new AssertionError(exception);
    }
  }

  private String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (GeneralSecurityException exception) {
      throw new AssertionError(exception);
    }
  }

  private void assertFailure(
      ThrowingAction action, String expectedCode, String expectedSafeSummary) {
    assertThatThrownBy(action::run)
        .isInstanceOf(OcrProviderException.class)
        .hasMessage(expectedSafeSummary)
        .extracting(exception -> ((OcrProviderException) exception).code())
        .isEqualTo(expectedCode);
  }

  private void assertTemporaryRootEmpty() {
    try (var entries = Files.list(tempRoot)) {
      assertThat(entries).isEmpty();
    } catch (IOException exception) {
      throw new AssertionError(exception);
    }
  }

  @FunctionalInterface
  private interface ThrowingAction {
    void run();
  }

  private record EncryptedFixture(byte[] plaintext, byte[] ciphertext, String ivBase64) {}
}
