package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.common.security.AuthorizationDecision;
import com.openecosystem.os.common.security.ResourceAction;
import com.openecosystem.os.common.security.ResourceAuthorizationService;
import com.openecosystem.os.common.security.ResourceType;
import com.openecosystem.os.drive.crypto.FileEncryptionService;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;

class DriveArchiveServiceTest {
  @TempDir Path temporary;
  final AuthenticationContext authentication = mock(AuthenticationContext.class);
  final ResourceAuthorizationService authorization = mock(ResourceAuthorizationService.class);
  final DriveFolderRepository folders = mock(DriveFolderRepository.class);
  final DriveFileRepository files = mock(DriveFileRepository.class);
  final DriveFileContentService content = mock(DriveFileContentService.class);
  final org.springframework.transaction.PlatformTransactionManager transactions =
      mock(org.springframework.transaction.PlatformTransactionManager.class);
  final FileEncryptionService encryption =
      new FileEncryptionService(new DriveProperties(null, null, null));
  final AuthenticatedPrincipal actor =
      new AuthenticatedPrincipal("usr_synthetic", "wrk_synthetic", Set.of("VIEWER"), true);

  @BeforeEach
  void setup() {
    when(transactions.getTransaction(any()))
        .thenReturn(new org.springframework.transaction.support.SimpleTransactionStatus());
    when(authentication.currentPrincipal()).thenReturn(actor);
    when(folders.isActiveMember(actor.workspaceId(), actor.actorId())).thenReturn(true);
    when(authorization.decide(any(), anyString(), any(), anyString(), any()))
        .thenReturn(new AuthorizationDecision(true, null));
    folder("root", null, "Synthetic projects");
  }

  @Test
  void escapesPortableNamesReversiblyWithoutChangingDriveMetadata() throws Exception {
    folder("root", null, "Reports.");
    var child = folder("child", "root", "Invoices:2026");
    when(folders.listChildren(eq(actor.workspaceId()), eq("root"), anyInt()))
        .thenReturn(List.of(child));
    file("one", "child", "report. ", new byte[] {1, 2});
    file("two", "root", "Invoices%3A2026", new byte[] {3});
    try (var zip = service(1000, 1024, Duration.ofMinutes(2)).prepareZip("root")) {
      assertThat(zip.filename()).isEqualTo("Reports%2E.zip");
      var entries = unzip(Files.readAllBytes(zip.path()));
      assertThat(entries.keySet())
          .containsExactlyInAnyOrder(
              "Invoices%3A2026/", "Invoices%3A2026/report%2E%20", "Invoices%253A2026");
      assertThat(entries.get("Invoices%3A2026/report%2E%20")).containsExactly(1, 2);
    }
    assertClean();
  }

  @Test
  void escapesPortablePunctuationAndDeviceNamesWithoutCollisions() throws Exception {
    for (var example :
        Map.of(
                "C:secret",
                "C%3Asecret",
                "CON.txt",
                "%43ON.txt",
                "LPT¹",
                "%4CPT¹",
                "a?*<>|\"",
                "a%3F%2A%3C%3E%7C%22",
                "trailing.",
                "trailing%2E")
            .entrySet()) {
      file("one", "root", example.getKey(), new byte[] {7});
      try (var zip = service(1000, 1024, Duration.ofMinutes(2)).prepareZip("root")) {
        var entries = unzip(Files.readAllBytes(zip.path()));
        assertThat(entries.keySet()).containsExactly(example.getValue());
        assertThat(entries.get(example.getValue())).containsExactly(7);
      }
    }
    assertClean();
  }

  @Test
  void snapshotCommitFailureCreatesNoTemporaryArchive() throws Exception {
    file("one", "root", "synthetic.txt", new byte[] {1});
    doThrow(new IllegalStateException("Synthetic database failure"))
        .when(transactions)
        .commit(any());
    assertFailure(service(1000, 1024, Duration.ofMinutes(2)), HttpStatus.INTERNAL_SERVER_ERROR);
    verifyNoInteractions(content);
    assertClean();
  }

  @Test
  void zipPreservesSubfoldersEmptyDirsAndExactSourceBytes() throws Exception {
    var child = folder("child", "root", "Invoices");
    var empty = folder("empty", "root", "Empty");
    when(folders.listChildren(eq(actor.workspaceId()), eq("root"), anyInt()))
        .thenReturn(List.of(child, empty));
    file("one", "root", "café.txt", new byte[] {0, 1, 2, -1});
    file("two", "child", "synthetic.txt", "Synthetic original".getBytes());
    Path path;
    try (var zip = service(1000, 1024, Duration.ofMinutes(2)).prepareZip("root")) {
      path = zip.path();
      assertThat(zip.filename()).isEqualTo("Synthetic projects.zip");
      var entries = unzip(Files.readAllBytes(path));
      assertThat(entries.keySet())
          .containsExactlyInAnyOrder("Invoices/", "Empty/", "café.txt", "Invoices/synthetic.txt");
      assertThat(entries.get("café.txt")).containsExactly(0, 1, 2, -1);
      assertThat(entries.get("Invoices/synthetic.txt")).isEqualTo("Synthetic original".getBytes());
      assertPrivate(path);
    }
    assertThat(path).doesNotExist();
    assertClean();
  }

  @Test
  void emptySelectedFolderProducesReadableEmptyZip() throws Exception {
    try (var zip = service(1, 1, Duration.ofMinutes(2)).prepareZip("root")) {
      assertThat(unzip(Files.readAllBytes(zip.path()))).isEmpty();
    }
    assertClean();
  }

  @Test
  void rejectDotDotAndAbsoluteArchiveEntries() throws Exception {
    for (String name :
        List.of(
            "..",
            ".",
            "../secret",
            "/absolute",
            "C:\\secret",
            "\\\\server\\share",
            "bad\nname",
            "bad/child",
            "bad\\child")) {
      file("one", "root", name, new byte[] {1});
      assertFailure(service(1000, 1024, Duration.ofMinutes(2)), HttpStatus.UNPROCESSABLE_CONTENT);
    }
    verifyNoInteractions(content);
    assertClean();
  }

  @Test
  void rejectsNormalizedEntryCollisionsAndUnsafeFolderNames() throws Exception {
    var child = folder("child", "root", "CAFÉ");
    when(folders.listChildren(eq(actor.workspaceId()), eq("root"), anyInt()))
        .thenReturn(List.of(child));
    file("one", "root", "cafe\u0301", new byte[] {1});
    assertFailure(service(1000, 1024, Duration.ofMinutes(2)), HttpStatus.UNPROCESSABLE_CONTENT);
    var unsafe = folder("child", "root", "../secret");
    when(folders.listChildren(eq(actor.workspaceId()), eq("root"), anyInt()))
        .thenReturn(List.of(unsafe));
    assertFailure(service(1000, 1024, Duration.ofMinutes(2)), HttpStatus.UNPROCESSABLE_CONTENT);
    verifyNoInteractions(content);
    assertClean();
  }

  @Test
  void rejectAnyDeniedDescendant() throws Exception {
    file("one", "root", "Synthetic secret.txt", new byte[] {1});
    when(authorization.decide(
            actor, actor.workspaceId(), ResourceType.FILE, "one", ResourceAction.VIEW))
        .thenReturn(new AuthorizationDecision(false, null));
    assertFailure(service(1000, 1024, Duration.ofMinutes(2)), HttpStatus.NOT_FOUND);
    verifyNoInteractions(content);
    assertClean();
  }

  @Test
  void rejectsForeignFolderDescendantAndInactiveOrMissingOwner() throws Exception {
    var child = folder("child", "root", "Synthetic secret");
    var foreign =
        new DriveFolderMetadata(
            child.folderId(),
            child.workspaceId(),
            "usr_other",
            child.parentFolderId(),
            child.encryptedName(),
            child.nameIv(),
            child.createdAt(),
            child.updatedAt());
    when(folders.listChildren(eq(actor.workspaceId()), eq("root"), anyInt()))
        .thenReturn(List.of(foreign));
    assertFailure(service(1000, 1024, Duration.ofMinutes(2)), HttpStatus.NOT_FOUND);
    when(folders.isActiveMember(actor.workspaceId(), actor.actorId())).thenReturn(false);
    assertFailure(service(1000, 1024, Duration.ofMinutes(2)), HttpStatus.NOT_FOUND);
    when(folders.isActiveMember(actor.workspaceId(), actor.actorId())).thenReturn(true);
    assertThatThrownBy(() -> service(1000, 1024, Duration.ofMinutes(2)).prepareZip("missing"))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(content);
    assertClean();
  }

  @Test
  void tooManyFilesOrBytesFailsBeforeResponse() throws Exception {
    var a = folder("a", "root", "A");
    var b = folder("b", "root", "B");
    when(folders.listChildren(eq(actor.workspaceId()), eq("root"), anyInt()))
        .thenReturn(List.of(a, b));
    assertFailure(service(1, 1024, Duration.ofMinutes(2)), HttpStatus.PAYLOAD_TOO_LARGE);
    when(folders.listChildren(eq(actor.workspaceId()), eq("root"), anyInt())).thenReturn(List.of());
    file("one", "root", "synthetic.txt", new byte[10]);
    assertFailure(service(1000, 9, Duration.ofMinutes(2)), HttpStatus.PAYLOAD_TOO_LARGE);
    verifyNoInteractions(content);
    assertClean();
  }

  @Test
  void failedOrCancelledArchiveCleansTemporaryFiles() throws Exception {
    file("one", "root", "synthetic.txt", new byte[] {1});
    when(content.read("one")).thenThrow(new IllegalStateException("Synthetic private storage/key"));
    assertFailure(service(1000, 1024, Duration.ofMinutes(2)), HttpStatus.INTERNAL_SERVER_ERROR);
    assertClean();
    doAnswer(
            invocation -> {
              Thread.currentThread().interrupt();
              return new DriveFileContent(new byte[] {1}, "synthetic.txt", "text/plain");
            })
        .when(content)
        .read("one");
    try {
      assertFailure(service(1000, 1024, Duration.ofMinutes(2)), HttpStatus.SERVICE_UNAVAILABLE);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
    assertClean();
  }

  @Test
  void fatalContentFailureAlsoCleansTemporaryFiles() throws Exception {
    file("one", "root", "synthetic.txt", new byte[] {1});
    when(content.read("one")).thenThrow(new AssertionError("Synthetic fatal failure"));
    assertThatThrownBy(() -> service(1000, 1024, Duration.ofMinutes(2)).prepareZip("root"))
        .isInstanceOf(AssertionError.class);
    assertClean();
  }

  @Test
  void preparationTimeoutCleansTemporaryFiles() throws Exception {
    file("one", "root", "synthetic.txt", new byte[] {1});
    when(content.read("one"))
        .thenAnswer(
            invocation -> {
              Thread.sleep(50);
              return new DriveFileContent(new byte[] {1}, "synthetic.txt", "text/plain");
            });
    assertFailure(service(1000, 1024, Duration.ofMillis(10)), HttpStatus.SERVICE_UNAVAILABLE);
    assertClean();
  }

  @Test
  void responseWriteFailureClosesArchive() throws Exception {
    var archive = service(1000, 1024, Duration.ofMinutes(2)).prepareZip("root");
    var archiveService = mock(DriveArchiveService.class);
    when(archiveService.prepareZip("root")).thenReturn(archive);
    var response = mock(jakarta.servlet.http.HttpServletResponse.class);
    when(response.getOutputStream())
        .thenThrow(new java.io.IOException("Synthetic disconnected client"));
    assertThatThrownBy(
            () ->
                new DriveFolderController(mock(DriveFolderService.class), archiveService)
                    .downloadFolder("root", response))
        .isInstanceOf(java.io.IOException.class);
    assertThat(archive.path()).doesNotExist();
    assertClean();
  }

  @Test
  void defaultsAndInvalidConfigurationAreBounded() {
    var defaults = new DriveArchiveProperties(null, null, null, null);
    assertThat(defaults.maxEntries()).isEqualTo(1000);
    assertThat(defaults.maxUncompressedSize().toBytes()).isEqualTo(512L * 1024 * 1024);
    assertThat(defaults.preparationTimeout()).isEqualTo(Duration.ofMinutes(2));
    assertThatThrownBy(() -> service(0, 1, Duration.ofMinutes(2)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service(1, 0, Duration.ofMinutes(2)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service(1, 1, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  DriveArchiveService service(int entries, long bytes, Duration timeout) {
    return new DriveArchiveService(
        authentication,
        authorization,
        folders,
        files,
        encryption,
        content,
        new DriveArchiveProperties(entries, DataSize.ofBytes(bytes), timeout, temporary),
        transactions);
  }

  DriveFolderMetadata folder(String id, String parent, String name) {
    var encrypted = encryption.encryptText(name);
    var f =
        new DriveFolderMetadata(
            id,
            actor.workspaceId(),
            actor.actorId(),
            parent,
            encrypted.ciphertextBase64(),
            encrypted.ivBase64(),
            Instant.now(),
            Instant.now());
    when(folders.findByIdForWorkspace(id, actor.workspaceId())).thenReturn(Optional.of(f));
    return f;
  }

  void file(String id, String parent, String name, byte[] bytes) {
    var encrypted = encryption.encryptText(name);
    var f =
        new DriveFileMetadata(
            id,
            actor.workspaceId(),
            actor.actorId(),
            DriveFileVisibility.PRIVATE,
            parent,
            encrypted.ciphertextBase64(),
            "text/plain",
            bytes.length,
            "synthetic-checksum",
            "synthetic/key",
            FileEncryptionService.ALGORITHM,
            "dev-local",
            "synthetic-iv",
            encrypted.ivBase64(),
            Instant.now(),
            Instant.now());
    when(files.listByFolder(eq(actor.workspaceId()), eq(parent), anyInt())).thenReturn(List.of(f));
    when(content.read(id)).thenReturn(new DriveFileContent(bytes, name, "text/plain"));
  }

  void assertFailure(DriveArchiveService service, HttpStatus status) {
    assertThatThrownBy(() -> service.prepareZip("root"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status()).isEqualTo(status);
              assertThat(e.getMessage()).doesNotContain("secret", "storage/key", "..", "Synthetic");
              assertThat(e.getCause()).isNull();
              assertThat(e.details()).isEmpty();
            });
  }

  void assertClean() throws Exception {
    try (var paths = Files.list(temporary)) {
      assertThat(paths.toList()).isEmpty();
    }
  }

  static Map<String, byte[]> unzip(byte[] bytes) throws Exception {
    var entries = new LinkedHashMap<String, byte[]>();
    try (var input =
        new ZipInputStream(
            new ByteArrayInputStream(bytes), java.nio.charset.StandardCharsets.UTF_8)) {
      for (var entry = input.getNextEntry(); entry != null; entry = input.getNextEntry())
        entries.put(entry.getName(), input.readAllBytes());
    }
    return entries;
  }

  static void assertPrivate(Path path) throws Exception {
    if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
      assertThat(Files.getPosixFilePermissions(path))
          .containsExactlyInAnyOrder(
              java.nio.file.attribute.PosixFilePermission.OWNER_READ,
              java.nio.file.attribute.PosixFilePermission.OWNER_WRITE);
    } else {
      var owner = Files.getOwner(path);
      var acl =
          Files.getFileAttributeView(path, java.nio.file.attribute.AclFileAttributeView.class)
              .getAcl();
      assertThat(acl).allSatisfy(entry -> assertThat(entry.principal()).isEqualTo(owner));
    }
  }
}
