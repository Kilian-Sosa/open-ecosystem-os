package com.openecosystem.os.drive;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;

public final class DriveZipArchive implements Closeable {
  private final Path directory;
  private final Path path;
  private final String filename;

  private DriveZipArchive(Path directory, Path path, String filename) {
    this.directory = directory;
    this.path = path;
    this.filename = filename;
  }

  static DriveZipArchive create(Path parent, String filename) throws IOException {
    FileAttribute<?> directoryPermissions;
    FileAttribute<?> filePermissions;
    var store = Files.getFileStore(parent);
    if (store.supportsFileAttributeView("posix")) {
      directoryPermissions =
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
      filePermissions =
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
    } else if (store.supportsFileAttributeView("acl")) {
      var owner =
          parent
              .getFileSystem()
              .getUserPrincipalLookupService()
              .lookupPrincipalByName(System.getProperty("user.name"));
      var acl =
          List.of(
              AclEntry.newBuilder()
                  .setType(AclEntryType.ALLOW)
                  .setPrincipal(owner)
                  .setPermissions(Set.of(AclEntryPermission.values()))
                  .build());
      directoryPermissions =
          new FileAttribute<List<AclEntry>>() {
            public String name() {
              return "acl:acl";
            }

            public List<AclEntry> value() {
              return acl;
            }
          };
      filePermissions = directoryPermissions;
    } else {
      throw new IOException("Private temporary storage is unavailable");
    }
    Path directory = Files.createTempDirectory(parent, "drive-archive-", directoryPermissions);
    try {
      Path path = Files.createFile(directory.resolve("archive.zip"), filePermissions);
      return new DriveZipArchive(directory, path, filename);
    } catch (IOException | RuntimeException failure) {
      Files.deleteIfExists(directory);
      throw failure;
    }
  }

  public Path path() {
    return path;
  }

  public String filename() {
    return filename;
  }

  @Override
  public void close() throws IOException {
    Files.deleteIfExists(path);
    Files.deleteIfExists(directory);
  }
}
