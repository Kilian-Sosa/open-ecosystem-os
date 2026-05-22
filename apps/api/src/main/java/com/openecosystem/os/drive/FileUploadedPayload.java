package com.openecosystem.os.drive;

import java.time.Instant;

public record FileUploadedPayload(
    String fileId,
    String contentType,
    long sizeBytes,
    String checksumSha256,
    String storageKey,
    String encryptionAlgorithm,
    String encryptionKeyId,
    String contentIv,
    Instant uploadedAt) {}
