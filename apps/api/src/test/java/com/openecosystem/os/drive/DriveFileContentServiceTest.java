package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import com.openecosystem.os.drive.storage.FileObjectStorage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class DriveFileContentServiceTest {
  private final AuthenticationContext authentication = mock(AuthenticationContext.class);
  private final ResourceAuthorizationService authorization =
      mock(ResourceAuthorizationService.class);
  private final DriveFileRepository repository = mock(DriveFileRepository.class);
  private final FileObjectStorage storage = mock(FileObjectStorage.class);
  private final DriveProperties properties = new DriveProperties(null, Set.of("text/plain"), null);
  private final FileEncryptionService encryption = new FileEncryptionService(properties);
  private final AuthenticatedPrincipal actor =
      new AuthenticatedPrincipal("usr_synthetic", "wrk_synthetic", Set.of("VIEWER"), true);
  private DriveFileContentService service;

  @BeforeEach
  void setUp() {
    when(authentication.currentPrincipal()).thenReturn(actor);
    service =
        new DriveFileContentService(
            authentication,
            authorization,
            repository,
            storage,
            encryption,
            properties,
            new DriveUploadContentValidator());
  }

  @Test
  void viewerWithoutFileViewCannotFetchBytes() {
    when(authorization.decide(
            actor, actor.workspaceId(), ResourceType.FILE, "file_synthetic", ResourceAction.VIEW))
        .thenReturn(new AuthorizationDecision(false, null));
    assertThatThrownBy(() -> service.read("file_synthetic"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    verifyNoInteractions(repository, storage);
  }

  @Test
  void originalDownloadBytesMatchUpload() throws Exception {
    byte[] original = "synthetic original\n".getBytes(StandardCharsets.UTF_8);
    prepare(original, original.length, false);
    DriveFileContent result = service.read("file_synthetic");
    assertThat(result.bytes()).isEqualTo(original);
    assertThat(result.name()).isEqualTo("synthetic.txt");
    assertThat(result.contentType()).isEqualTo("text/plain");
    verify(storage).getEncryptedObject("synthetic/key", original.length + 16L);
  }

  @Test
  void tamperedCiphertextFailsWithoutLeak() throws Exception {
    prepare("synthetic secret".getBytes(StandardCharsets.UTF_8), 16, true);
    assertThatThrownBy(() -> service.read("file_synthetic"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
              assertThat(e.getMessage()).isEqualTo("Drive file content could not be read");
              assertThat(e.getCause()).isNull();
              assertThat(e.details()).isEmpty();
            });
  }

  @Test
  void oversizedObjectRejectedBeforeBuffering() throws Exception {
    prepare(new byte[] {1}, properties.maxUploadBytes() + 1, false);
    assertThatThrownBy(() -> service.read("file_synthetic")).isInstanceOf(ApiException.class);
    verifyNoInteractions(storage);
  }

  @Test
  void sourceLengthAndChecksumMismatchFailClosed() throws Exception {
    prepare("synthetic".getBytes(StandardCharsets.UTF_8), 3, false);
    assertThatThrownBy(() -> service.read("file_synthetic")).isInstanceOf(ApiException.class);
    prepare("synthetic".getBytes(StandardCharsets.UTF_8), 9, false);
    var replacement = encryption.encryptBytes("different".getBytes(StandardCharsets.UTF_8));
    // Use a different authenticated object with the same IV stored in its metadata.
    var metadata =
        repository.findByIdForWorkspace("file_synthetic", actor.workspaceId()).orElseThrow();
    when(repository.findByIdForWorkspace("file_synthetic", actor.workspaceId()))
        .thenReturn(
            Optional.of(
                new DriveFileMetadata(
                    metadata.fileId(),
                    metadata.workspaceId(),
                    metadata.ownerId(),
                    metadata.visibility(),
                    null,
                    metadata.encryptedName(),
                    metadata.contentType(),
                    9,
                    metadata.checksumSha256(),
                    metadata.storageKey(),
                    metadata.encryptionAlgorithm(),
                    metadata.encryptionKeyId(),
                    replacement.ivBase64(),
                    metadata.nameIv(),
                    metadata.createdAt(),
                    metadata.updatedAt())));
    when(storage.getEncryptedObject("synthetic/key", 25)).thenReturn(replacement.ciphertext());
    assertThatThrownBy(() -> service.read("file_synthetic")).isInstanceOf(ApiException.class);
  }

  private void prepare(byte[] original, long declaredSize, boolean tamper) throws Exception {
    when(authorization.decide(
            actor, actor.workspaceId(), ResourceType.FILE, "file_synthetic", ResourceAction.VIEW))
        .thenReturn(new AuthorizationDecision(true, null));
    var name = encryption.encryptText("synthetic.txt");
    var content = encryption.encryptBytes(original);
    byte[] ciphertext = content.ciphertext().clone();
    if (tamper) ciphertext[ciphertext.length - 1] ^= 1;
    when(repository.findByIdForWorkspace("file_synthetic", actor.workspaceId()))
        .thenReturn(
            Optional.of(
                new DriveFileMetadata(
                    "file_synthetic",
                    actor.workspaceId(),
                    actor.actorId(),
                    DriveFileVisibility.PRIVATE,
                    null,
                    name.ciphertextBase64(),
                    "text/plain",
                    declaredSize,
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)),
                    "synthetic/key",
                    FileEncryptionService.ALGORITHM,
                    properties.encryption().keyId(),
                    content.ivBase64(),
                    name.ivBase64(),
                    Instant.now(),
                    Instant.now())));
    when(storage.getEncryptedObject("synthetic/key", declaredSize + 16)).thenReturn(ciphertext);
  }
}
