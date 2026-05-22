package com.openecosystem.os.drive;

import java.time.Instant;

public record DriveFileResponse(
    String fileId,
    String name,
    String contentType,
    long sizeBytes,
    String checksumSha256,
    boolean encrypted,
    Instant uploadedAt,
    Instant updatedAt) {}
