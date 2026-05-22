package com.openecosystem.os.drive.crypto;

public record EncryptedBytes(byte[] ciphertext, String ivBase64) {}
