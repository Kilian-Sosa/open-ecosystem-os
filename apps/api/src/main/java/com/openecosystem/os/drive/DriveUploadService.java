package com.openecosystem.os.drive;

import com.openecosystem.os.audit.AuditOutcome;
import com.openecosystem.os.audit.AuditRecord;
import com.openecosystem.os.audit.JdbcAuditRecordRepository;
import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.common.events.EventEnvelope;
import com.openecosystem.os.common.events.JdbcEventOutboxRepository;
import com.openecosystem.os.common.ids.Ids;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.common.security.CorrelationContext;
import com.openecosystem.os.drive.crypto.EncryptedBytes;
import com.openecosystem.os.drive.crypto.EncryptedText;
import com.openecosystem.os.drive.crypto.FileEncryptionService;
import com.openecosystem.os.drive.storage.FileObjectStorage;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DriveUploadService {

  private static final String FILE_UPLOADED = "FileUploaded";
  private static final String RESOURCE_TYPE_FILE = "file";
  private static final String UPLOAD_ACTION = "drive.file.uploaded";

  private final AuthenticationContext authenticationContext;
  private final DriveProperties driveProperties;
  private final DriveFileRepository driveFileRepository;
  private final JdbcAuditRecordRepository auditRecordRepository;
  private final JdbcEventOutboxRepository eventOutboxRepository;
  private final FileObjectStorage objectStorage;
  private final FileEncryptionService encryptionService;
  private final TransactionTemplate transactionTemplate;

  public DriveUploadService(
      AuthenticationContext authenticationContext,
      DriveProperties driveProperties,
      DriveFileRepository driveFileRepository,
      JdbcAuditRecordRepository auditRecordRepository,
      JdbcEventOutboxRepository eventOutboxRepository,
      FileObjectStorage objectStorage,
      FileEncryptionService encryptionService,
      TransactionTemplate transactionTemplate) {
    this.authenticationContext = authenticationContext;
    this.driveProperties = driveProperties;
    this.driveFileRepository = driveFileRepository;
    this.auditRecordRepository = auditRecordRepository;
    this.eventOutboxRepository = eventOutboxRepository;
    this.objectStorage = objectStorage;
    this.encryptionService = encryptionService;
    this.transactionTemplate = transactionTemplate;
  }

  public DriveFileResponse upload(MultipartFile file) {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    String correlationId = CorrelationContext.currentOrCreate();
    ValidatedUpload upload = validate(file);
    String fileId = Ids.newId("file");
    String storageKey = storageKey(principal.workspaceId(), fileId);
    Instant now = Instant.now();

    byte[] plaintext = fileBytes(file);
    String checksumSha256 = sha256Hex(plaintext);
    EncryptedText encryptedName = encryptionService.encryptText(upload.name());
    EncryptedBytes encryptedContent = encryptionService.encryptBytes(plaintext);

    DriveFileMetadata metadata =
        new DriveFileMetadata(
            fileId,
            principal.workspaceId(),
            principal.actorId(),
            encryptedName.ciphertextBase64(),
            upload.contentType(),
            plaintext.length,
            checksumSha256,
            storageKey,
            FileEncryptionService.ALGORITHM,
            driveProperties.encryption().keyId(),
            encryptedContent.ivBase64(),
            encryptedName.ivBase64(),
            now,
            now);

    boolean objectStored = false;
    try {
      objectStorage.putEncryptedObject(
          storageKey,
          encryptedContent.ciphertext(),
          upload.contentType(),
          encryptedContent.ivBase64());
      objectStored = true;
      transactionTemplate.executeWithoutResult(
          status -> {
            driveFileRepository.save(metadata);
            auditRecordRepository.save(uploadAuditRecord(metadata, principal, correlationId, now));
            eventOutboxRepository.save(
                fileUploadedEnvelope(metadata, principal, correlationId, now));
          });
      return toResponse(metadata);
    } catch (RuntimeException exception) {
      if (objectStored) {
        objectStorage.deleteObjectIfExists(storageKey);
      }
      throw exception;
    }
  }

  public DriveFileListResponse listFiles() {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    List<DriveFileResponse> files =
        driveFileRepository.listByWorkspace(principal.workspaceId()).stream()
            .map(this::toResponse)
            .toList();
    return new DriveFileListResponse(files);
  }

  public DriveFileResponse getFile(String fileId) {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    return driveFileRepository
        .findByIdForWorkspace(fileId, principal.workspaceId())
        .map(this::toResponse)
        .orElseThrow(
            () ->
                new ApiException(
                    HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "Drive file was not found"));
  }

  private ValidatedUpload validate(MultipartFile file) {
    if (file == null || file.isEmpty() || file.getSize() <= 0) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Uploaded file must not be empty");
    }
    if (file.getSize() > driveProperties.maxUploadBytes()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          ApiErrorCode.BAD_REQUEST,
          "Uploaded file exceeds the configured Drive size limit");
    }

    String contentType =
        file.getContentType() == null || file.getContentType().isBlank()
            ? "application/octet-stream"
            : file.getContentType().toLowerCase(Locale.ROOT);
    if (!driveProperties.allowedContentTypes().contains(contentType)) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          ApiErrorCode.BAD_REQUEST,
          "Uploaded file type is not allowed",
          Map.of("contentType", contentType));
    }

    return new ValidatedUpload(safeFileName(file.getOriginalFilename()), contentType);
  }

  private byte[] fileBytes(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException exception) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Uploaded file could not be read");
    }
  }

  private String safeFileName(String originalFilename) {
    String filename =
        originalFilename == null || originalFilename.isBlank() ? "uploaded-file" : originalFilename;
    filename = filename.replace('\\', '/');
    int slashIndex = filename.lastIndexOf('/');
    if (slashIndex >= 0) {
      filename = filename.substring(slashIndex + 1);
    }
    filename = StringUtils.cleanPath(filename).trim();
    if (filename.isBlank() || filename.contains("..")) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Uploaded file name is invalid");
    }
    return filename.length() > 512 ? filename.substring(0, 512) : filename;
  }

  private AuditRecord uploadAuditRecord(
      DriveFileMetadata metadata,
      AuthenticatedPrincipal principal,
      String correlationId,
      Instant occurredAt) {
    return new AuditRecord(
        Ids.newId("aud"),
        UPLOAD_ACTION,
        RESOURCE_TYPE_FILE,
        metadata.fileId(),
        principal.workspaceId(),
        principal.actorId(),
        correlationId,
        occurredAt,
        AuditOutcome.SUCCESS,
        Map.of(
            "contentType",
            metadata.contentType(),
            "sizeBytes",
            Long.toString(metadata.sizeBytes()),
            "encrypted",
            "true"));
  }

  private EventEnvelope<FileUploadedPayload> fileUploadedEnvelope(
      DriveFileMetadata metadata,
      AuthenticatedPrincipal principal,
      String correlationId,
      Instant occurredAt) {
    FileUploadedPayload payload =
        new FileUploadedPayload(
            metadata.fileId(),
            metadata.contentType(),
            metadata.sizeBytes(),
            metadata.checksumSha256(),
            metadata.storageKey(),
            metadata.encryptionAlgorithm(),
            metadata.encryptionKeyId(),
            metadata.contentIv(),
            metadata.createdAt());
    return new EventEnvelope<>(
        Ids.newId("evt"),
        FILE_UPLOADED,
        1,
        occurredAt,
        principal.workspaceId(),
        principal.actorId(),
        correlationId,
        null,
        DriveModule.NAME,
        "drive:" + metadata.fileId() + ":uploaded:v1",
        payload);
  }

  private DriveFileResponse toResponse(DriveFileMetadata metadata) {
    return new DriveFileResponse(
        metadata.fileId(),
        encryptionService.decryptText(metadata.encryptedName(), metadata.nameIv()),
        metadata.contentType(),
        metadata.sizeBytes(),
        metadata.checksumSha256(),
        true,
        metadata.createdAt(),
        metadata.updatedAt());
  }

  private String storageKey(String workspaceId, String fileId) {
    return "workspaces/" + workspaceId + "/drive/" + fileId + "/original";
  }

  private String sha256Hex(byte[] bytes) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(bytes));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is not available", exception);
    }
  }

  private record ValidatedUpload(String name, String contentType) {}
}
