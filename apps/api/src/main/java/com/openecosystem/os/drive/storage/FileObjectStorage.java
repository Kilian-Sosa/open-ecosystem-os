package com.openecosystem.os.drive.storage;

public interface FileObjectStorage {

  byte[] getEncryptedObject(String storageKey, long maxCiphertextBytes);

  void putEncryptedObject(
      String storageKey, byte[] encryptedContent, String originalContentType, String contentIv);

  void deleteObjectIfExists(String storageKey);

  void deleteEncryptedObject(String storageKey);
}
