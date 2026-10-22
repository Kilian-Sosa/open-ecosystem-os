package com.openecosystem.os.drive;

import java.time.Instant;

public record DriveFileMetadata(
    String fileId,
    String workspaceId,
    String ownerId,
    DriveFileVisibility visibility,
    String folderId,
    String encryptedName,
    String contentType,
    long sizeBytes,
    String checksumSha256,
    String storageKey,
    String encryptionAlgorithm,
    String encryptionKeyId,
    String contentIv,
    String nameIv,
    Instant createdAt,
    Instant updatedAt,
    String deletionGroupId,
    String originalFolderId) {
  public DriveFileMetadata(
      String fileId,
      String workspaceId,
      String ownerId,
      DriveFileVisibility visibility,
      String folderId,
      String encryptedName,
      String contentType,
      long sizeBytes,
      String checksumSha256,
      String storageKey,
      String encryptionAlgorithm,
      String encryptionKeyId,
      String contentIv,
      String nameIv,
      Instant createdAt,
      Instant updatedAt) {
    this(
        fileId,
        workspaceId,
        ownerId,
        visibility,
        folderId,
        encryptedName,
        contentType,
        sizeBytes,
        checksumSha256,
        storageKey,
        encryptionAlgorithm,
        encryptionKeyId,
        contentIv,
        nameIv,
        createdAt,
        updatedAt,
        null,
        null);
  }
}
