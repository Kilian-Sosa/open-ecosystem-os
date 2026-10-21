package com.openecosystem.os.drive;

import java.time.Instant;

public record DriveFolderResponse(
    String folderId, String parentFolderId, String name, Instant createdAt, Instant updatedAt) {}
