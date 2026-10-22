package com.openecosystem.os.drive;

import java.time.Duration;
import java.time.Instant;

public record DriveTrashGroup(
    String groupId,
    String workspaceId,
    String rootItemType,
    String rootItemId,
    String originalParentFolderId,
    String actorId,
    Instant trashedAt,
    Instant purgeAfter,
    DriveTrashState state) {
  public static DriveTrashGroup trashed(
      String id,
      String workspace,
      String type,
      String root,
      String parent,
      String actor,
      Instant when) {
    return new DriveTrashGroup(
        id,
        workspace,
        type,
        root,
        parent,
        actor,
        when,
        when.plus(Duration.ofDays(30)),
        DriveTrashState.TRASHED);
  }

  public boolean canRestoreAt(Instant now) {
    return state == DriveTrashState.TRASHED && now.isBefore(purgeAfter);
  }
}
