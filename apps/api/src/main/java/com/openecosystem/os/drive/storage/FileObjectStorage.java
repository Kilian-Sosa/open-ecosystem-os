package com.openecosystem.os.drive.storage;

public interface FileObjectStorage {

  void putEncryptedObject(
      String storageKey, byte[] encryptedContent, String originalContentType, String contentIv);

  void deleteObjectIfExists(String storageKey);
}
