package com.openecosystem.os.drive;

public record FileViewAuthorizationFacts(
    String fileId,
    String workspaceId,
    String ownerId,
    DriveFileVisibility visibility,
    boolean activeMember,
    boolean workspaceVisibilityRole,
    boolean activeUserGrant) {}
