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
import com.openecosystem.os.drive.crypto.FileEncryptionService;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriveTrashService {
  private final AuthenticationContext authentication;
  private final DriveTrashRepository trash;
  private final DriveFileRepository files;
  private final DriveFolderRepository folders;
  private final JdbcAuditRecordRepository audits;
  private final Clock clock;
  private final DriveFolderService directory;
  private final FileEncryptionService encryption;

  public DriveTrashService(
      AuthenticationContext authentication,
      DriveTrashRepository trash,
      DriveFileRepository files,
      DriveFolderRepository folders,
      JdbcAuditRecordRepository audits,
      Clock clock,
      DriveFolderService directory,
      FileEncryptionService encryption) {
    this.authentication = authentication;
    this.trash = trash;
    this.files = files;
    this.folders = folders;
    this.audits = audits;
    this.clock = clock;
    this.directory = directory;
    this.encryption = encryption;
  }

  @Transactional
  public DriveTrashGroup trashFile(String fileId) {
    var actor = mutationActor();
    folders.lockWorkspaceForMutation(actor.workspaceId());
    var file =
        files
            .findByIdForWorkspace(fileId, actor.workspaceId())
            .filter(f -> f.ownerId().equals(actor.actorId()))
            .orElseThrow(DriveTrashService::notFound);
    if (file.deletionGroupId() != null)
      return repeated(file.deletionGroupId(), "file", fileId, actor);
    if (!trash.isActiveFile(actor.workspaceId(), fileId, clock.instant())) throw notFound();
    var group =
        DriveTrashGroup.trashed(
            Ids.newId("trash"),
            actor.workspaceId(),
            "file",
            fileId,
            file.folderId(),
            actor.actorId(),
            clock.instant());
    trash.createGroup(group);
    if (!trash.attachFile(actor.workspaceId(), fileId, group.groupId())) throw notFound();
    audit(group, actor);
    return group;
  }

  @Transactional
  public DriveTrashGroup trashFolder(String folderId) {
    var actor = mutationActor();
    folders.lockWorkspaceForMutation(actor.workspaceId());
    var root =
        folders
            .findByIdForWorkspace(folderId, actor.workspaceId())
            .filter(f -> f.ownerId().equals(actor.actorId()))
            .orElseThrow(DriveTrashService::notFound);
    if (root.deletionGroupId() != null)
      return repeated(root.deletionGroupId(), "folder", folderId, actor);
    if (!trash.isActiveFolder(actor.workspaceId(), folderId, clock.instant())) throw notFound();
    var pending = new ArrayDeque<DriveFolderMetadata>();
    pending.add(root);
    var directoryMembers = new ArrayList<String>();
    var fileMembers = new ArrayList<String>();
    var visited = new HashSet<String>();
    while (!pending.isEmpty()) {
      var directory = pending.removeFirst();
      if (!visited.add(directory.folderId()) || !directory.ownerId().equals(actor.actorId()))
        throw notFound();
      directoryMembers.add(directory.folderId());
      pending.addAll(folders.listChildren(actor.workspaceId(), directory.folderId()));
      for (var file : files.listByFolder(actor.workspaceId(), directory.folderId())) {
        if (!file.ownerId().equals(actor.actorId())) throw notFound();
        fileMembers.add(file.fileId());
      }
    }
    var group =
        DriveTrashGroup.trashed(
            Ids.newId("trash"),
            actor.workspaceId(),
            "folder",
            folderId,
            root.parentFolderId(),
            actor.actorId(),
            clock.instant());
    trash.createGroup(group);
    directoryMembers.stream()
        .sorted()
        .forEach(
            id -> {
              if (!trash.attachFolder(actor.workspaceId(), id, group.groupId())) throw notFound();
            });
    fileMembers.stream()
        .sorted()
        .forEach(
            id -> {
              if (!trash.attachFile(actor.workspaceId(), id, group.groupId())) throw notFound();
            });
    audit(group, actor);
    return group;
  }

  public List<DriveTrashResponse> listTrash() {
    var actor = mutationActor();
    return trash.findVisibleGroups(actor.workspaceId()).stream()
        .filter(group -> rootOwnedBy(group, actor))
        .map(group -> response(group, actor))
        .toList();
  }

  @Transactional
  public DriveTrashResponse restore(String groupId, DriveRestoreDestination destination) {
    var actor = mutationActor();
    folders.lockWorkspaceForMutation(actor.workspaceId());
    var group =
        trash.lockGroup(groupId, actor.workspaceId()).orElseThrow(DriveTrashService::notFound);
    if (!rootOwnedBy(group, actor)) throw notFound();
    if (group.state() == DriveTrashState.RESTORED) return response(group, actor);
    var now = clock.instant();
    if (!group.canRestoreAt(now)) throw conflict("Drive item can no longer be restored");
    for (var file : trash.filesInGroup(actor.workspaceId(), groupId))
      if (!file.ownerId().equals(actor.actorId())) throw notFound();
    for (var folder : trash.foldersInGroup(actor.workspaceId(), groupId))
      if (!folder.ownerId().equals(actor.actorId())) throw notFound();
    String target =
        destination.supplied() ? destination.folderId() : group.originalParentFolderId();
    if (!destination.supplied()
        && target != null
        && (!folders.isActiveFolder(actor.workspaceId(), target)
            || folders
                .findByIdForWorkspace(target, actor.workspaceId())
                .filter(f -> f.ownerId().equals(actor.actorId()))
                .isEmpty())) throw conflict("Choose an accessible restore destination");
    directory.requireOwner(target, actor);
    String fileId = null, folderId = null;
    String name;
    if (group.rootItemType().equals("file")) {
      var file =
          files
              .findByIdForWorkspace(group.rootItemId(), actor.workspaceId())
              .orElseThrow(DriveTrashService::notFound);
      fileId = file.fileId();
      name = encryption.decryptText(file.encryptedName(), file.nameIv());
    } else {
      var folder =
          folders
              .findByIdForWorkspace(group.rootItemId(), actor.workspaceId())
              .orElseThrow(DriveTrashService::notFound);
      folderId = folder.folderId();
      name = encryption.decryptText(folder.encryptedName(), folder.nameIv());
    }
    directory.assertMoveAvailable(name, target, actor.workspaceId(), fileId, folderId);
    if (fileId != null) files.updateFolder(actor.workspaceId(), fileId, target, now);
    else folders.updateParent(actor.workspaceId(), folderId, target, now);
    if (!trash.restoreGroup(groupId, actor.workspaceId(), now))
      throw conflict("Drive item can no longer be restored");
    audits.save(
        new AuditRecord(
            Ids.newId("aud"),
            "drive." + group.rootItemType() + ".restored",
            group.rootItemType(),
            group.rootItemId(),
            actor.workspaceId(),
            actor.actorId(),
            CorrelationContext.currentOrCreate(),
            now,
            AuditOutcome.SUCCESS,
            Map.of("groupId", groupId, "status", "RESTORED")));
    return response(trash.findByIdForWorkspace(groupId, actor.workspaceId()).orElseThrow(), actor);
  }

  private boolean rootOwnedBy(DriveTrashGroup group, AuthenticatedPrincipal actor) {
    if (group.rootItemType().equals("file"))
      return files
          .findByIdForWorkspace(group.rootItemId(), actor.workspaceId())
          .filter(f -> f.ownerId().equals(actor.actorId()))
          .isPresent();
    return folders
        .findByIdForWorkspace(group.rootItemId(), actor.workspaceId())
        .filter(f -> f.ownerId().equals(actor.actorId()))
        .isPresent();
  }

  private DriveTrashResponse response(DriveTrashGroup group, AuthenticatedPrincipal actor) {
    String name;
    if (group.rootItemType().equals("file")) {
      var root =
          files
              .findByIdForWorkspace(group.rootItemId(), actor.workspaceId())
              .orElseThrow(DriveTrashService::notFound);
      name = encryption.decryptText(root.encryptedName(), root.nameIv());
    } else {
      var root =
          folders
              .findByIdForWorkspace(group.rootItemId(), actor.workspaceId())
              .orElseThrow(DriveTrashService::notFound);
      name = encryption.decryptText(root.encryptedName(), root.nameIv());
    }
    String location = null;
    if (group.originalParentFolderId() == null) location = "Drive";
    else {
      var parent =
          folders
              .findByIdForWorkspace(group.originalParentFolderId(), actor.workspaceId())
              .filter(f -> f.ownerId().equals(actor.actorId()));
      if (parent.isPresent())
        location = encryption.decryptText(parent.get().encryptedName(), parent.get().nameIv());
    }
    return new DriveTrashResponse(
        group.groupId(),
        group.rootItemType(),
        group.rootItemId(),
        name,
        group.originalParentFolderId(),
        location,
        group.trashedAt(),
        group.purgeAfter(),
        group.state(),
        group.canRestoreAt(clock.instant()));
  }

  private static ApiException conflict(String message) {
    return new ApiException(HttpStatus.CONFLICT, ApiErrorCode.CONFLICT, message);
  }

  private AuthenticatedPrincipal mutationActor() {
    var actor = authentication.currentPrincipal();
    if (!actor.authenticated() || !folders.isActiveMember(actor.workspaceId(), actor.actorId()))
      throw notFound();
    return actor;
  }

  private DriveTrashGroup repeated(
      String groupId, String type, String rootId, AuthenticatedPrincipal actor) {
    return trash
        .findByIdForWorkspace(groupId, actor.workspaceId())
        .filter(g -> g.rootItemType().equals(type) && g.rootItemId().equals(rootId))
        .filter(g -> g.state() == DriveTrashState.TRASHED || g.state() == DriveTrashState.PURGING)
        .orElseThrow(DriveTrashService::notFound);
  }

  private void audit(DriveTrashGroup group, AuthenticatedPrincipal actor) {
    audits.save(
        new AuditRecord(
            Ids.newId("aud"),
            "drive." + group.rootItemType() + ".trashed",
            group.rootItemType(),
            group.rootItemId(),
            actor.workspaceId(),
            actor.actorId(),
            CorrelationContext.currentOrCreate(),
            clock.instant(),
            AuditOutcome.SUCCESS,
            Map.of("groupId", group.groupId(), "status", group.state().name())));
  }

  static ApiException notFound() {
    return new ApiException(
        HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND, "Drive item was not found");
  }
}
