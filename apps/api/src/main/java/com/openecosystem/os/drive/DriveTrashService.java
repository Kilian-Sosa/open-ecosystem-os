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
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
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

  public DriveTrashService(
      AuthenticationContext authentication,
      DriveTrashRepository trash,
      DriveFileRepository files,
      DriveFolderRepository folders,
      JdbcAuditRecordRepository audits,
      Clock clock) {
    this.authentication = authentication;
    this.trash = trash;
    this.files = files;
    this.folders = folders;
    this.audits = audits;
    this.clock = clock;
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
