package com.openecosystem.os.worker.ocr;

public record DriveOcrFileMetadata(
    String fileId,
    String workspaceId,
    String contentType,
    long sizeBytes,
    String checksumSha256,
    String storageKey,
    String encryptionAlgorithm,
    String encryptionKeyId,
    String contentIv) {}
