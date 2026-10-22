package com.openecosystem.os.drive;

import java.time.Instant;

public record DriveTrashMutationResponse(String groupId, Instant trashedAt, Instant purgeAfter) {
  static DriveTrashMutationResponse from(DriveTrashGroup group) {
    return new DriveTrashMutationResponse(group.groupId(), group.trashedAt(), group.purgeAfter());
  }
}
