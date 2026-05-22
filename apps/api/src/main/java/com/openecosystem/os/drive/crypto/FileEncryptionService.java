package com.openecosystem.os.drive.crypto;

import com.openecosystem.os.drive.DriveProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class FileEncryptionService {

  public static final String ALGORITHM = "AES-256-GCM";

  private static final int GCM_TAG_BITS = 128;
  private static final int IV_BYTES = 12;
  private static final String CIPHER_NAME = "AES/GCM/NoPadding";

  private final SecretKeySpec key;
  private final SecureRandom secureRandom = new SecureRandom();

  public FileEncryptionService(DriveProperties driveProperties) {
    byte[] decodedKey = Base64.getDecoder().decode(driveProperties.encryption().keyBase64());
    if (decodedKey.length != 32) {
      throw new IllegalStateException("Drive encryption key must be 32 bytes after Base64 decode");
    }
    this.key = new SecretKeySpec(decodedKey, "AES");
  }

  public EncryptedText encryptText(String value) {
    EncryptedBytes encryptedBytes = encrypt(value.getBytes(StandardCharsets.UTF_8));
    return new EncryptedText(
        Base64.getEncoder().encodeToString(encryptedBytes.ciphertext()), encryptedBytes.ivBase64());
  }

  public String decryptText(String ciphertextBase64, String ivBase64) {
    byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);
    byte[] plaintext = decrypt(ciphertext, ivBase64);
    return new String(plaintext, StandardCharsets.UTF_8);
  }

  public EncryptedBytes encryptBytes(byte[] plaintext) {
    return encrypt(plaintext);
  }

  private EncryptedBytes encrypt(byte[] plaintext) {
    byte[] iv = randomIv();
    try {
      Cipher cipher = Cipher.getInstance(CIPHER_NAME);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      return new EncryptedBytes(cipher.doFinal(plaintext), Base64.getEncoder().encodeToString(iv));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Drive file encryption failed", exception);
    }
  }

  private byte[] decrypt(byte[] ciphertext, String ivBase64) {
    byte[] iv = Base64.getDecoder().decode(ivBase64);
    try {
      Cipher cipher = Cipher.getInstance(CIPHER_NAME);
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
      return cipher.doFinal(ciphertext);
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Drive file name decryption failed", exception);
    }
  }

  private byte[] randomIv() {
    byte[] iv = new byte[IV_BYTES];
    secureRandom.nextBytes(iv);
    return iv;
  }
}
