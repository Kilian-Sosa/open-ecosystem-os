package com.openecosystem.os.worker.ocr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public final class OcrSource implements AutoCloseable {

  private final Path path;
  private final Path ownedDirectory;
  private final String contentType;

  OcrSource(Path path, Path ownedDirectory, String contentType) {
    this.path = path;
    this.ownedDirectory = ownedDirectory;
    this.contentType = contentType;
  }

  public Path path() {
    return path;
  }

  public String contentType() {
    return contentType;
  }

  @Override
  public void close() {
    try (var paths = Files.walk(ownedDirectory)) {
      paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
    } catch (IOException ignored) {
      deleteQuietly(path);
      deleteQuietly(ownedDirectory);
    }
  }

  private void deleteQuietly(Path target) {
    try {
      Files.deleteIfExists(target);
    } catch (IOException ignored) {
      // Best effort cleanup of a worker-owned temporary processing input.
    }
  }
}
