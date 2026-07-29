package com.openecosystem.os.drive;

import java.time.Instant;

public record DriveFileUserGrant(
    String grantId,
    String workspaceId,
    String fileId,
    String granteeUserId,
    String action,
    String grantedByUserId,
    Instant grantedAt,
    String revokedByUserId,
    Instant revokedAt,
    Instant createdAt,
    Instant updatedAt) {

  public boolean active() {
    return revokedAt == null;
  }
}
