package com.openecosystem.os.drive;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("openecosystem.drive")
public record DriveProperties(
    DataSize maxUploadSize, Set<String> allowedContentTypes, Encryption encryption) {

  public DriveProperties {
    maxUploadSize = maxUploadSize == null ? DataSize.ofMegabytes(25) : maxUploadSize;
    allowedContentTypes =
        allowedContentTypes == null
            ? Set.of()
            : allowedContentTypes.stream()
                .map(contentType -> contentType.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    encryption =
        encryption == null
            ? new Encryption("dev-local", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
            : encryption;
  }

  public long maxUploadBytes() {
    return maxUploadSize.toBytes();
  }

  public record Encryption(String keyId, String keyBase64) {

    public Encryption {
      keyId = keyId == null || keyId.isBlank() ? "dev-local" : keyId;
      keyBase64 =
          keyBase64 == null || keyBase64.isBlank()
              ? "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
              : keyBase64;
    }
  }
}
