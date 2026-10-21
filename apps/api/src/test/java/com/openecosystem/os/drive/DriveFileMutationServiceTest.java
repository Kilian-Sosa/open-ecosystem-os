package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openecosystem.os.audit.JdbcAuditRecordRepository;
import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.common.security.AuthorizationDecision;
import com.openecosystem.os.common.security.AuthorizationDecisionCode;
import com.openecosystem.os.common.security.ResourceAction;
import com.openecosystem.os.common.security.ResourceAuthorizationService;
import com.openecosystem.os.common.security.ResourceType;
import com.openecosystem.os.drive.crypto.FileEncryptionService;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DriveFileMutationServiceTest {
  final AuthenticationContext authentication = mock(AuthenticationContext.class);
  final ResourceAuthorizationService authorization = mock(ResourceAuthorizationService.class);
  final DriveFileRepository files = mock(DriveFileRepository.class);
  final DriveFolderRepository folders = mock(DriveFolderRepository.class);
  final DriveFolderService directory = mock(DriveFolderService.class);
  final JdbcAuditRecordRepository audits = mock(JdbcAuditRecordRepository.class);
  final FileEncryptionService encryption =
      new FileEncryptionService(new DriveProperties(null, Set.of("text/plain"), null));
  final AuthenticatedPrincipal actor =
      new AuthenticatedPrincipal("usr_synthetic", "wrk_synthetic", Set.of("VIEWER"), true);
  DriveFileMutationService service;

  @BeforeEach
  void setUp() {
    when(authentication.currentPrincipal()).thenReturn(actor);
    service =
        new DriveFileMutationService(
            authentication, authorization, files, folders, directory, encryption, audits);
  }

  @Test
  void viewOnlyUserCannotRenameOrMove() {
    when(authorization.decide(
            actor, actor.workspaceId(), ResourceType.FILE, "file_synthetic", ResourceAction.VIEW))
        .thenReturn(AuthorizationDecision.allow(AuthorizationDecisionCode.ALLOW_USER_GRANT));
    assertThatThrownBy(() -> service.renameFile("file_synthetic", "secret.txt"))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.moveFile("file_synthetic", null))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(files, directory, audits);
  }

  @Test
  void inactiveOwnerCannotMutate() {
    when(authorization.decide(
            actor, actor.workspaceId(), ResourceType.FILE, "file_synthetic", ResourceAction.VIEW))
        .thenReturn(AuthorizationDecision.deny(AuthorizationDecisionCode.DENY_INACTIVE_MEMBERSHIP));
    assertThatThrownBy(() -> service.renameFile("file_synthetic", "new.txt"))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> service.moveFile("file_synthetic", "folder_destination"))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(files, directory, audits);
  }

  @Test
  void renameEncryptsOnlyNameAndAuthorizesBeforeDecryption() {
    var encrypted = encryption.encryptText("synthetic.txt");
    var now = Instant.now();
    var metadata =
        new DriveFileMetadata(
            "file_synthetic",
            actor.workspaceId(),
            actor.actorId(),
            DriveFileVisibility.PRIVATE,
            null,
            encrypted.ciphertextBase64(),
            "text/plain",
            9,
            "synthetic-checksum",
            "synthetic/object",
            FileEncryptionService.ALGORITHM,
            "synthetic-key",
            "synthetic-content-iv",
            encrypted.ivBase64(),
            now,
            now);
    when(authorization.decide(
            actor, actor.workspaceId(), ResourceType.FILE, metadata.fileId(), ResourceAction.VIEW))
        .thenReturn(AuthorizationDecision.allow(AuthorizationDecisionCode.ALLOW_OWNER));
    when(files.findByIdForWorkspace(metadata.fileId(), actor.workspaceId()))
        .thenReturn(Optional.of(metadata));
    when(files.updateName(anyString(), anyString(), anyString(), anyString(), any()))
        .thenReturn(true);
    service.renameFile(metadata.fileId(), "renamed.txt");
    var ciphertext = ArgumentCaptor.forClass(String.class);
    var iv = ArgumentCaptor.forClass(String.class);
    verify(files)
        .updateName(
            eq(actor.workspaceId()),
            eq(metadata.fileId()),
            ciphertext.capture(),
            iv.capture(),
            any());
    assertThat(encryption.decryptText(ciphertext.getValue(), iv.getValue()))
        .isEqualTo("renamed.txt");
    assertThat(ciphertext.getValue()).doesNotContain("renamed");
    var order = inOrder(folders, authorization, files, directory);
    order.verify(folders).lockWorkspaceForMutation(actor.workspaceId());
    order
        .verify(authorization)
        .decide(
            actor, actor.workspaceId(), ResourceType.FILE, metadata.fileId(), ResourceAction.VIEW);
    order.verify(files).findByIdForWorkspace(metadata.fileId(), actor.workspaceId());
    order.verify(directory).requireOwner(null, actor);
    verify(files, never()).updateFolder(anyString(), anyString(), any(), any());
    verify(audits).save(any());
  }
}
