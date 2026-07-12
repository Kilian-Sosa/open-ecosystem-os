package com.openecosystem.os.worker.ocr;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DriveOcrSourceReader implements OcrSourceReader {

  private static final String ENCRYPTION_ALGORITHM = "AES-256-GCM";
  private static final int GCM_TAG_BITS = 128;
  private static final int GCM_IV_BYTES = 12;

  private final DriveOcrMetadataRepository metadataRepository;
  private final OcrObjectStore objectStore;
  private final String configuredKeyId;
  private final byte[] configuredKey;
  private final long maxInputBytes;
  private final Path temporaryRoot;

  @Autowired
  public DriveOcrSourceReader(
      DriveOcrMetadataRepository metadataRepository,
      OcrObjectStore objectStore,
      WorkerDriveProperties driveProperties,
      WorkerOcrProperties ocrProperties) {
    this(
        metadataRepository,
        objectStore,
        driveProperties.encryption().keyId(),
        driveProperties.encryption().keyBase64(),
        ocrProperties.maxInputBytes(),
        Path.of(System.getProperty("java.io.tmpdir")));
  }

  DriveOcrSourceReader(
      DriveOcrMetadataRepository metadataRepository,
      OcrObjectStore objectStore,
      String configuredKeyId,
      String configuredKeyBase64,
      long maxInputBytes,
      Path temporaryRoot) {
    this.metadataRepository = metadataRepository;
    this.objectStore = objectStore;
    this.configuredKeyId = required(configuredKeyId);
    this.configuredKey = decodeKey(configuredKeyBase64);
    if (maxInputBytes <= 0) {
      throw new IllegalArgumentException("OCR input limit must be positive");
    }
    this.maxInputBytes = maxInputBytes;
    this.temporaryRoot = temporaryRoot;
  }

  @Override
  public OcrSource open(OcrJob job) {
    DriveOcrFileMetadata metadata = loadAndValidateMetadata(job);
    Path ownedDirectory = null;
    try {
      ownedDirectory = createOwnedDirectory();
      Path plaintextPath = ownedDirectory.resolve("input.bin");
      decryptAndVerify(metadata, plaintextPath);
      return new OcrSource(plaintextPath, ownedDirectory, metadata.contentType());
    } catch (OcrProviderException exception) {
      deleteDirectory(ownedDirectory);
      throw exception;
    } catch (IOException | GeneralSecurityException exception) {
      deleteDirectory(ownedDirectory);
      throw failure("OCR_SOURCE_READ_FAILED", "OCR source could not be read");
    }
  }

  private DriveOcrFileMetadata loadAndValidateMetadata(OcrJob job) {
    Optional<DriveOcrFileMetadata> metadata =
        metadataRepository.findByFileIdAndWorkspaceId(job.fileId(), job.workspaceId());
    if (metadata.isEmpty()
        || !same(metadata.get().fileId(), job.fileId())
        || !same(metadata.get().workspaceId(), job.workspaceId())
        || !same(metadata.get().contentType(), job.contentType())
        || !same(metadata.get().storageKey(), job.storageKey())) {
      throw failure("OCR_SOURCE_METADATA_MISMATCH", "OCR source metadata did not match the job");
    }
    DriveOcrFileMetadata value = metadata.get();
    if (!ENCRYPTION_ALGORITHM.equals(value.encryptionAlgorithm())
        || !same(configuredKeyId, value.encryptionKeyId())
        || value.sizeBytes() < 0
        || value.sizeBytes() > maxInputBytes
        || !validSha256(value.checksumSha256())
        || !validIv(value.contentIv())) {
      throw failure("OCR_SOURCE_ENCRYPTION_MISMATCH", "OCR source encryption metadata was invalid");
    }
    return value;
  }

  private void decryptAndVerify(DriveOcrFileMetadata metadata, Path plaintextPath)
      throws IOException, GeneralSecurityException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    long decryptedBytes = 0;
    try (InputStream encrypted = objectStore.open(metadata.storageKey());
        InputStream boundedEncrypted = new MaxBytesInputStream(encrypted, maxInputBytes);
        InputStream plaintext =
            new CipherInputStream(boundedEncrypted, decryptCipher(metadata.contentIv()));
        var output = Files.newOutputStream(plaintextPath)) {
      byte[] buffer = new byte[8_192];
      int read;
      while ((read = plaintext.read(buffer)) != -1) {
        decryptedBytes += read;
        if (decryptedBytes > maxInputBytes) {
          throw failure("OCR_SOURCE_SIZE_LIMIT", "OCR source exceeded the configured size limit");
        }
        digest.update(buffer, 0, read);
        output.write(buffer, 0, read);
      }
    } catch (MaxBytesExceededException exception) {
      throw failure("OCR_SOURCE_SIZE_LIMIT", "OCR source exceeded the configured size limit");
    } catch (IOException exception) {
      if (exception.getCause() instanceof GeneralSecurityException) {
        throw failure("OCR_SOURCE_DECRYPTION_FAILED", "OCR source could not be authenticated");
      }
      throw exception;
    }
    if (decryptedBytes != metadata.sizeBytes()) {
      throw failure("OCR_SOURCE_SIZE_MISMATCH", "OCR source size verification failed");
    }
    if (!MessageDigest.isEqual(
        digest.digest(), HexFormat.of().parseHex(metadata.checksumSha256()))) {
      throw failure("OCR_SOURCE_CHECKSUM_MISMATCH", "OCR source checksum verification failed");
    }
  }

  private Cipher decryptCipher(String ivBase64) throws GeneralSecurityException {
    byte[] iv = Base64.getDecoder().decode(ivBase64);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        new SecretKeySpec(configuredKey, "AES"),
        new GCMParameterSpec(GCM_TAG_BITS, iv));
    return cipher;
  }

  private Path createOwnedDirectory() throws IOException {
    Path directory = Files.createTempDirectory(temporaryRoot, "ocr-");
    try {
      Files.setPosixFilePermissions(
          directory,
          Set.of(
              PosixFilePermission.OWNER_READ,
              PosixFilePermission.OWNER_WRITE,
              PosixFilePermission.OWNER_EXECUTE));
    } catch (UnsupportedOperationException ignored) {
      // Windows ACLs retain ownership of the newly created temporary directory.
    }
    return directory;
  }

  private void deleteDirectory(Path directory) {
    if (directory == null) {
      return;
    }
    new OcrSource(directory.resolve("input.bin"), directory, "application/octet-stream").close();
  }

  private boolean validIv(String value) {
    try {
      return Base64.getDecoder().decode(value).length == GCM_IV_BYTES;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private boolean validSha256(String value) {
    return value != null && value.matches("[0-9a-fA-F]{64}");
  }

  private boolean same(String left, String right) {
    return left != null && left.equals(right);
  }

  private String required(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Drive OCR encryption configuration is required");
    }
    return value;
  }

  private byte[] decodeKey(String value) {
    try {
      byte[] decoded = Base64.getDecoder().decode(required(value));
      if (decoded.length != 32) {
        throw new IllegalStateException("Drive OCR encryption key must be 32 bytes");
      }
      return decoded;
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException("Drive OCR encryption configuration is invalid");
    }
  }

  private OcrProviderException failure(String code, String summary) {
    return new OcrProviderException(code, summary);
  }

  private static final class MaxBytesInputStream extends InputStream {

    private final InputStream delegate;
    private final long maxBytes;
    private long readBytes;

    private MaxBytesInputStream(InputStream delegate, long maxBytes) {
      this.delegate = delegate;
      this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
      byte[] single = new byte[1];
      return read(single) == -1 ? -1 : Byte.toUnsignedInt(single[0]);
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int count = delegate.read(buffer, offset, length);
      if (count > 0) {
        readBytes += count;
        if (readBytes > maxBytes) {
          throw new MaxBytesExceededException();
        }
      }
      return count;
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }

  private static final class MaxBytesExceededException extends IOException {}
}
