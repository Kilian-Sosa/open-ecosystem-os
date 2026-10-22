package com.openecosystem.os.drive;

import com.openecosystem.os.audit.AuditOutcome;
import com.openecosystem.os.audit.AuditRecord;
import com.openecosystem.os.audit.JdbcAuditRecordRepository;
import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.common.ids.Ids;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import com.openecosystem.os.common.security.CorrelationContext;
import com.openecosystem.os.common.security.ResourceAction;
import com.openecosystem.os.common.security.ResourceAuthorizationService;
import com.openecosystem.os.common.security.ResourceType;
import com.openecosystem.os.drive.crypto.FileEncryptionService;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriveFolderService {
  private final AuthenticationContext authentication;
  private final DriveFolderRepository folders;
  private final DriveFileRepository files;
  private final FileEncryptionService encryption;
  private final DriveDirectoryNamePolicy names;
  private final ResourceAuthorizationService authorization;
  private final JdbcAuditRecordRepository audits;

  public DriveFolderService(
      AuthenticationContext authentication,
      DriveFolderRepository folders,
      DriveFileRepository files,
      FileEncryptionService encryption,
      DriveDirectoryNamePolicy names,
      ResourceAuthorizationService authorization,
      JdbcAuditRecordRepository audits) {
    this.authentication = authentication;
    this.folders = folders;
    this.files = files;
    this.encryption = encryption;
    this.names = names;
    this.authorization = authorization;
    this.audits = audits;
  }

  @Transactional
  public DriveFolderResponse createFolder(String name, String parentFolderId) {
    var principal = authentication.currentPrincipal();
    String parent = rootOrId(parentFolderId);
    names.normalize(name);
    folders.lockWorkspaceForMutation(principal.workspaceId());
    requireOwner(parent, principal);
    assertAvailable(name, parent, principal.workspaceId());
    var encrypted = encryption.encryptText(name);
    var now = Instant.now();
    var folder =
        new DriveFolderMetadata(
            Ids.newId("folder"),
            principal.workspaceId(),
            principal.actorId(),
            parent,
            encrypted.ciphertextBase64(),
            encrypted.ivBase64(),
            now,
            now);
    folders.save(folder);
    audits.save(
        new AuditRecord(
            Ids.newId("aud"),
            "drive.folder.created",
            "folder",
            folder.folderId(),
            principal.workspaceId(),
            principal.actorId(),
            CorrelationContext.currentOrCreate(),
            now,
            AuditOutcome.SUCCESS,
            Map.of()));
    return toResponse(folder);
  }

  public DriveFolderResponse getFolder(String folderId) {
    var p = authentication.currentPrincipal();
    return toResponse(requireOwner(folderId, p));
  }

  @Transactional
  public DriveFolderResponse renameFolder(String folderId, String name) {
    var p = authentication.currentPrincipal();
    folders.lockWorkspaceForMutation(p.workspaceId());
    var source = requireMutationOwner(folderId, p);
    requireOwner(source.parentFolderId(), p);
    assertAvailable(name, source.parentFolderId(), p.workspaceId(), null, folderId);
    var encrypted = encryption.encryptText(name);
    var now = Instant.now();
    if (!folders.updateName(
        p.workspaceId(), folderId, encrypted.ciphertextBase64(), encrypted.ivBase64(), now))
      throw notFound();
    recordMutation("drive.folder.renamed", folderId, p, now);
    return toResponse(
        folders
            .findByIdForWorkspace(folderId, p.workspaceId())
            .orElseThrow(DriveFolderService::notFound));
  }

  @Transactional
  public DriveFolderResponse moveFolder(String folderId, String destinationFolderId) {
    var p = authentication.currentPrincipal();
    folders.lockWorkspaceForMutation(p.workspaceId());
    var source = requireMutationOwner(folderId, p);
    requireOwner(source.parentFolderId(), p);
    String destination = rootOrId(destinationFolderId);
    var visited = new HashSet<String>();
    String ancestor = destination;
    while (ancestor != null) {
      if (ancestor.equals(folderId) || !visited.add(ancestor))
        throw new ApiException(
            HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, "Drive folder move would create a cycle");
      ancestor = requireOwner(ancestor, p).parentFolderId();
    }
    String name = encryption.decryptText(source.encryptedName(), source.nameIv());
    assertMoveAvailable(name, destination, p.workspaceId(), null, folderId);
    var now = Instant.now();
    if (!folders.updateParent(p.workspaceId(), folderId, destination, now)) throw notFound();
    recordMutation("drive.folder.moved", folderId, p, now);
    return toResponse(
        folders
            .findByIdForWorkspace(folderId, p.workspaceId())
            .orElseThrow(DriveFolderService::notFound));
  }

  private DriveFolderMetadata requireMutationOwner(String folderId, AuthenticatedPrincipal p) {
    if (!p.authenticated() || !folders.isActiveMember(p.workspaceId(), p.actorId()))
      throw notFound();
    var source = requireOwner(folderId, p);
    if (source == null) throw notFound();
    return source;
  }

  private void recordMutation(
      String action, String folderId, AuthenticatedPrincipal p, Instant now) {
    audits.save(
        new AuditRecord(
            Ids.newId("aud"),
            action,
            "folder",
            folderId,
            p.workspaceId(),
            p.actorId(),
            CorrelationContext.currentOrCreate(),
            now,
            AuditOutcome.SUCCESS,
            Map.of()));
  }

  public DriveDirectoryListResponse listFolderContents(String folderId) {
    var p = authentication.currentPrincipal();
    String parent = rootOrId(folderId);
    requireOwner(parent, p);
    var children =
        folders.listChildren(p.workspaceId(), parent).stream()
            .filter(f -> f.ownerId().equals(p.actorId()))
            .map(this::toResponse)
            .toList();
    var candidates = files.listByFolder(p.workspaceId(), parent);
    var allowed =
        authorization.allowedResourceIds(
            p,
            p.workspaceId(),
            ResourceType.FILE,
            candidates.stream().map(DriveFileMetadata::fileId).toList(),
            ResourceAction.VIEW);
    return new DriveDirectoryListResponse(
        children,
        candidates.stream()
            .filter(f -> allowed.contains(f.fileId()))
            .map(this::fileResponse)
            .toList());
  }

  DriveFolderMetadata requireOwner(String folderId, AuthenticatedPrincipal p) {
    if (folderId == null) return null;
    return folders
        .findByIdForWorkspace(folderId, p.workspaceId())
        .filter(f -> f.ownerId().equals(p.actorId()))
        .filter(f -> folders.isActiveFolder(p.workspaceId(), folderId))
        .orElseThrow(DriveFolderService::notFound);
  }

  void assertAvailable(String name, String parent, String workspaceId) {
    assertAvailable(name, parent, workspaceId, null, null);
  }

  void assertAvailable(
      String name,
      String parent,
      String workspaceId,
      String excludedFileId,
      String excludedFolderId) {
    assertComparisonAvailable(
        names.normalize(name), parent, workspaceId, excludedFileId, excludedFolderId);
  }

  void assertMoveAvailable(
      String name,
      String parent,
      String workspaceId,
      String excludedFileId,
      String excludedFolderId) {
    assertComparisonAvailable(
        names.comparisonKey(name), parent, workspaceId, excludedFileId, excludedFolderId);
  }

  private void assertComparisonAvailable(
      String key,
      String parent,
      String workspaceId,
      String excludedFileId,
      String excludedFolderId) {
    String ownerId = authentication.currentPrincipal().actorId();
    boolean conflict =
        folders.listChildren(workspaceId, parent).stream()
                .filter(f -> f.ownerId().equals(ownerId))
                .filter(f -> !f.folderId().equals(excludedFolderId))
                .anyMatch(
                    f ->
                        key.equals(
                            names.comparisonKey(
                                encryption.decryptText(f.encryptedName(), f.nameIv()))))
            || files.listByFolder(workspaceId, parent).stream()
                .filter(f -> f.ownerId().equals(ownerId))
                .filter(f -> !f.fileId().equals(excludedFileId))
                .anyMatch(
                    f ->
                        key.equals(
                            names.comparisonKey(
                                encryption.decryptText(f.encryptedName(), f.nameIv()))));
    if (conflict)
      throw new ApiException(
          HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, "Drive name is unavailable");
  }

  static String rootOrId(String id) {
    return id == null || id.isBlank() ? null : id;
  }

  private static ApiException notFound() {
    return new ApiException(
        HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "Drive folder was not found");
  }

  private DriveFolderResponse toResponse(DriveFolderMetadata f) {
    return new DriveFolderResponse(
        f.folderId(),
        f.parentFolderId(),
        encryption.decryptText(f.encryptedName(), f.nameIv()),
        f.createdAt(),
        f.updatedAt());
  }

  DriveFileResponse fileResponse(DriveFileMetadata f) {
    return new DriveFileResponse(
        f.fileId(),
        f.visibility().value(),
        f.folderId(),
        encryption.decryptText(f.encryptedName(), f.nameIv()),
        f.contentType(),
        f.sizeBytes(),
        f.checksumSha256(),
        true,
        f.createdAt(),
        f.updatedAt());
  }
}
