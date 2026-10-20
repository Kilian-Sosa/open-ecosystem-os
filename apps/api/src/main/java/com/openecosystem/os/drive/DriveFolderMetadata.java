package com.openecosystem.os.drive;

import java.time.Instant;

public record DriveFolderMetadata(
    String folderId,
    String workspaceId,
    String ownerId,
    String parentFolderId,
    String encryptedName,
    String nameIv,
    Instant createdAt,
    Instant updatedAt) {}
