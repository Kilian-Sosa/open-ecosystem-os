package com.openecosystem.os.drive;

import java.time.Instant;

public record DriveFileMetadata(
    String fileId,
    String workspaceId,
    String ownerId,
    DriveFileVisibility visibility,
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
    Instant updatedAt) {}
