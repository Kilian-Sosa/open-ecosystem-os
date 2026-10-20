package com.openecosystem.os.drive;

import com.openecosystem.os.audit.AuditOutcome;
import com.openecosystem.os.audit.AuditRecord;
import com.openecosystem.os.audit.JdbcAuditRecordRepository;
import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.common.ids.Ids;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.common.security.AuthorizationDecisionCode;
import com.openecosystem.os.common.security.CorrelationContext;
import com.openecosystem.os.common.security.ResourceAction;
import com.openecosystem.os.common.security.ResourceAuthorizationService;
import com.openecosystem.os.common.security.ResourceType;
import com.openecosystem.os.drive.crypto.FileEncryptionService;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriveFileMutationService {
  private final AuthenticationContext authentication;
  private final ResourceAuthorizationService authorization;
  private final DriveFileRepository files;
  private final DriveFolderRepository folders;
  private final DriveFolderService directory;
  private final FileEncryptionService encryption;
  private final JdbcAuditRecordRepository audits;

  public DriveFileMutationService(
      AuthenticationContext authentication,
      ResourceAuthorizationService authorization,
      DriveFileRepository files,
      DriveFolderRepository folders,
      DriveFolderService directory,
      FileEncryptionService encryption,
      JdbcAuditRecordRepository audits) {
    this.authentication = authentication;
    this.authorization = authorization;
    this.files = files;
    this.folders = folders;
    this.directory = directory;
    this.encryption = encryption;
    this.audits = audits;
  }

  @Transactional
  public DriveFileResponse renameFile(String fileId, String name) {
    var p = authentication.currentPrincipal();
    folders.lockWorkspaceForMutation(p.workspaceId());
    var source = requireOwner(fileId, p);
    directory.requireOwner(source.folderId(), p);
    directory.assertAvailable(name, source.folderId(), p.workspaceId(), fileId, null);
    var encrypted = encryption.encryptText(name);
    var now = Instant.now();
    if (!files.updateName(
        p.workspaceId(), fileId, encrypted.ciphertextBase64(), encrypted.ivBase64(), now))
      throw notFound();
    recordMutation("drive.file.renamed", fileId, p, now);
    return directory.fileResponse(
        files
            .findByIdForWorkspace(fileId, p.workspaceId())
            .orElseThrow(DriveFileMutationService::notFound));
  }

  @Transactional
  public DriveFileResponse moveFile(String fileId, String destinationFolderId) {
    var p = authentication.currentPrincipal();
    folders.lockWorkspaceForMutation(p.workspaceId());
    var source = requireOwner(fileId, p);
    directory.requireOwner(source.folderId(), p);
    String destination = DriveFolderService.rootOrId(destinationFolderId);
    directory.requireOwner(destination, p);
    String name = encryption.decryptText(source.encryptedName(), source.nameIv());
    directory.assertMoveAvailable(name, destination, p.workspaceId(), fileId, null);
    var now = Instant.now();
    if (!files.updateFolder(p.workspaceId(), fileId, destination, now)) throw notFound();
    recordMutation("drive.file.moved", fileId, p, now);
    return directory.fileResponse(
        files
            .findByIdForWorkspace(fileId, p.workspaceId())
            .orElseThrow(DriveFileMutationService::notFound));
  }

  private DriveFileMetadata requireOwner(String fileId, AuthenticatedPrincipal p) {
    var decision =
        authorization.decide(p, p.workspaceId(), ResourceType.FILE, fileId, ResourceAction.VIEW);
    if (!decision.allowed() || decision.code() != AuthorizationDecisionCode.ALLOW_OWNER)
      throw notFound();
    return files
        .findByIdForWorkspace(fileId, p.workspaceId())
        .filter(f -> f.ownerId().equals(p.actorId()))
        .orElseThrow(DriveFileMutationService::notFound);
  }

  private void recordMutation(String action, String fileId, AuthenticatedPrincipal p, Instant now) {
    audits.save(
        new AuditRecord(
            Ids.newId("aud"),
            action,
            "file",
            fileId,
            p.workspaceId(),
            p.actorId(),
            CorrelationContext.currentOrCreate(),
            now,
            AuditOutcome.SUCCESS,
            Map.of()));
  }

  private static ApiException notFound() {
    return new ApiException(
        HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "Drive file was not found");
  }
}
