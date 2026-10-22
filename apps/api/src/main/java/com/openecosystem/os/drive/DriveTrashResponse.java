package com.openecosystem.os.drive;

import java.time.Instant;

public record DriveTrashResponse(
    String groupId,
    String rootItemType,
    String rootItemId,
    String name,
    String originalParentFolderId,
    String originalLocation,
    Instant trashedAt,
    Instant purgeAfter,
    DriveTrashState state,
    boolean canRestore) {}
