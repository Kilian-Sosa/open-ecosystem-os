package com.openecosystem.os.drive;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("openecosystem.drive.archive")
public record DriveArchiveProperties(
    Integer maxEntries,
    DataSize maxUncompressedSize,
    Duration preparationTimeout,
    Path temporaryDirectory) {
  public DriveArchiveProperties {
    maxEntries = maxEntries == null ? 1000 : maxEntries;
    maxUncompressedSize =
        maxUncompressedSize == null ? DataSize.ofMegabytes(512) : maxUncompressedSize;
    preparationTimeout = preparationTimeout == null ? Duration.ofMinutes(2) : preparationTimeout;
    temporaryDirectory =
        temporaryDirectory == null
            ? Path.of(System.getProperty("java.io.tmpdir"))
            : temporaryDirectory;
    if (maxEntries <= 0
        || maxUncompressedSize.toBytes() <= 0
        || preparationTimeout.isNegative()
        || preparationTimeout.isZero())
      throw new IllegalArgumentException("Drive archive limits must be positive");
    preparationTimeout.toNanos();
  }
}
