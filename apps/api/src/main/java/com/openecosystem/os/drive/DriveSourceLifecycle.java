package com.openecosystem.os.drive;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriveSourceLifecycle {
  private final DriveFolderRepository folders;
  private final DriveTrashRepository trash;
  private final Clock clock;

  public DriveSourceLifecycle(
      DriveFolderRepository folders, DriveTrashRepository trash, Clock clock) {
    this.folders = folders;
    this.trash = trash;
    this.clock = clock;
  }

  // The caller's transaction retains this fence until all derived writes commit.
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean lockActiveFile(String workspaceId, String fileId) {
    if (!trash.isActiveFile(workspaceId, fileId, clock.instant())) return false;
    folders.lockWorkspaceForMutation(workspaceId);
    return trash.isActiveFile(workspaceId, fileId, clock.instant());
  }
}
