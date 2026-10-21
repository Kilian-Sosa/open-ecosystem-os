package com.openecosystem.os.drive;

import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.common.security.ResourceAction;
import com.openecosystem.os.common.security.ResourceAuthorizationService;
import com.openecosystem.os.common.security.ResourceType;
import com.openecosystem.os.drive.crypto.FileEncryptionService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class DriveArchiveService {
  private final AuthenticationContext authentication;
  private final ResourceAuthorizationService authorization;
  private final DriveFolderRepository folders;
  private final DriveFileRepository files;
  private final FileEncryptionService encryption;
  private final DriveFileContentService content;
  private final DriveArchiveProperties properties;
  private final TransactionTemplate transactions;

  public DriveArchiveService(
      AuthenticationContext authentication,
      ResourceAuthorizationService authorization,
      DriveFolderRepository folders,
      DriveFileRepository files,
      FileEncryptionService encryption,
      DriveFileContentService content,
      DriveArchiveProperties properties,
      PlatformTransactionManager transactionManager) {
    this.authentication = authentication;
    this.authorization = authorization;
    this.folders = folders;
    this.files = files;
    this.encryption = encryption;
    this.content = content;
    this.properties = properties;
    this.transactions = new TransactionTemplate(transactionManager);
    this.transactions.setTimeout(
        (int)
            Math.min(Integer.MAX_VALUE, Math.max(1, properties.preparationTimeout().toSeconds())));
  }

  public DriveZipArchive prepareZip(String folderId) {
    long started = System.nanoTime();
    DriveZipArchive archive = null;
    boolean prepared = false;
    try {
      var actor = authentication.currentPrincipal();
      if (!actor.authenticated() || !folders.isActiveMember(actor.workspaceId(), actor.actorId()))
        throw denied();
      var snapshot = transactions.execute(status -> snapshot(folderId, actor, started));
      checkBudget(started);
      archive = DriveZipArchive.create(properties.temporaryDirectory(), snapshot.filename());
      try (var zip =
          new ZipOutputStream(Files.newOutputStream(archive.path()), StandardCharsets.UTF_8)) {
        for (var entry : snapshot.entries()) {
          checkBudget(started);
          zip.putNextEntry(new ZipEntry(entry.path() + (entry.file() == null ? "/" : "")));
          if (entry.file() != null) {
            byte[] bytes = content.read(entry.file().fileId()).bytes();
            checkBudget(started);
            if (bytes.length != entry.file().sizeBytes()) throw new IllegalStateException();
            for (int offset = 0; offset < bytes.length; offset += 65536) {
              checkBudget(started);
              zip.write(bytes, offset, Math.min(65536, bytes.length - offset));
            }
          }
          zip.closeEntry();
        }
      }
      checkBudget(started);
      prepared = true;
      return archive;
    } catch (IOException | RuntimeException exception) {
      if (exception instanceof ApiException api) throw api;
      throw failed();
    } finally {
      if (archive != null && !prepared) {
        try {
          archive.close();
        } catch (IOException cleanupFailure) {
          throw failed();
        }
      }
    }
  }

  private Snapshot snapshot(String folderId, AuthenticatedPrincipal actor, long started) {
    folders.lockWorkspaceForMutation(actor.workspaceId());
    checkBudget(started);
    var root =
        folders
            .findByIdForWorkspace(folderId, actor.workspaceId())
            .orElseThrow(DriveArchiveService::denied);
    requireOwner(root, actor);
    String rootName = safeSegment(encryption.decryptText(root.encryptedName(), root.nameIv()));
    var pending = new ArrayDeque<Directory>();
    pending.add(new Directory(root, ""));
    var visited = new HashSet<String>();
    var paths = new HashSet<String>();
    var entries = new ArrayList<Entry>();
    long totalBytes = 0;
    while (!pending.isEmpty()) {
      checkBudget(started);
      var directory = pending.removeFirst();
      if (!visited.add(directory.folder().folderId())) throw invalid();
      requireOwner(directory.folder(), actor);
      for (var child :
          folders.listChildren(
              actor.workspaceId(), directory.folder().folderId(), readLimit(entries))) {
        checkBudget(started);
        requireOwner(child, actor);
        String path =
            directory.path()
                + safeSegment(encryption.decryptText(child.encryptedName(), child.nameIv()));
        addEntry(entries, paths, path, null);
        pending.addLast(new Directory(child, path + "/"));
      }
      for (var file :
          files.listByFolder(
              actor.workspaceId(), directory.folder().folderId(), readLimit(entries))) {
        checkBudget(started);
        if (!authorization
            .decide(
                actor, actor.workspaceId(), ResourceType.FILE, file.fileId(), ResourceAction.VIEW)
            .allowed()) throw denied();
        if (file.sizeBytes() < 0
            || file.sizeBytes() > properties.maxUncompressedSize().toBytes() - totalBytes)
          throw tooLarge();
        totalBytes += file.sizeBytes();
        String path =
            directory.path()
                + safeSegment(encryption.decryptText(file.encryptedName(), file.nameIv()));
        addEntry(entries, paths, path, file);
      }
    }
    checkBudget(started);
    String filename = rootName.length() <= 251 ? rootName + ".zip" : "download.zip";
    return new Snapshot(filename, List.copyOf(entries));
  }

  private void addEntry(
      List<Entry> entries, Set<String> paths, String path, DriveFileMetadata file) {
    if (entries.size() >= properties.maxEntries()) throw tooLarge();
    if (path.getBytes(StandardCharsets.UTF_8).length > 4096
        || !paths.add(new DriveDirectoryNamePolicy().comparisonKey(path))) throw invalid();
    entries.add(new Entry(path, file));
  }

  private int readLimit(List<Entry> entries) {
    return (int) Math.min(Integer.MAX_VALUE, (long) properties.maxEntries() - entries.size() + 1);
  }

  private static String safeSegment(String name) {
    try {
      new DriveDirectoryNamePolicy().normalize(name);
    } catch (ApiException exception) {
      throw invalid();
    }
    String normalized = Normalizer.normalize(name, Normalizer.Form.NFC);
    int trailing = normalized.length();
    while (trailing > 0 && ". ".indexOf(normalized.charAt(trailing - 1)) >= 0) trailing--;
    boolean reserved = normalized.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9¹²³]|LPT[1-9¹²³])([. ].*)?");
    var encoded = new StringBuilder();
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if ("%:\"<>|?*".indexOf(c) >= 0 || i >= trailing || (reserved && i == 0)) {
        for (byte b : String.valueOf(c).getBytes(StandardCharsets.UTF_8)) {
          encoded.append('%');
          encoded.append("0123456789ABCDEF".charAt((b & 0xff) >>> 4));
          encoded.append("0123456789ABCDEF".charAt(b & 0xf));
        }
      } else encoded.append(c);
    }
    return encoded.toString();
  }

  private void checkBudget(long started) {
    if (Thread.currentThread().isInterrupted()
        || System.nanoTime() - started >= properties.preparationTimeout().toNanos())
      throw new ApiException(
          HttpStatus.SERVICE_UNAVAILABLE,
          ApiErrorCode.INTERNAL_ERROR,
          "Drive archive preparation did not complete");
  }

  private static void requireOwner(DriveFolderMetadata folder, AuthenticatedPrincipal actor) {
    if (!folder.workspaceId().equals(actor.workspaceId())
        || !folder.ownerId().equals(actor.actorId())) throw denied();
  }

  private static ApiException denied() {
    return new ApiException(
        HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "Drive folder was not found");
  }

  private static ApiException invalid() {
    return new ApiException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        ApiErrorCode.BAD_REQUEST,
        "Drive archive contains invalid entries");
  }

  private static ApiException tooLarge() {
    return new ApiException(
        HttpStatus.PAYLOAD_TOO_LARGE,
        ApiErrorCode.BAD_REQUEST,
        "Drive archive exceeds configured limits");
  }

  private static ApiException failed() {
    return new ApiException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ApiErrorCode.INTERNAL_ERROR,
        "Drive archive could not be prepared");
  }

  private record Directory(DriveFolderMetadata folder, String path) {}

  private record Snapshot(String filename, List<Entry> entries) {}

  private record Entry(String path, DriveFileMetadata file) {}
}
