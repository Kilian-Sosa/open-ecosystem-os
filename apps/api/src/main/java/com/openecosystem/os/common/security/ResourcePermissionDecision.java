package com.openecosystem.os.common.security;

import com.openecosystem.os.drive.DriveFileMetadata;
import org.springframework.stereotype.Component;

@Component
public class ResourcePermissionDecision {

  public boolean mayViewFile(AuthenticatedPrincipal principal, DriveFileMetadata file) {
    return principal.workspaceId().equals(file.workspaceId())
        && principal.actorId().equals(file.ownerId());
  }
}
