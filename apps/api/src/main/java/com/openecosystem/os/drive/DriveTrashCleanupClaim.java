package com.openecosystem.os.drive;

import java.time.Instant;

public record DriveTrashCleanupClaim(
    DriveTrashGroup group,
    String token,
    Instant leaseUntil,
    int attempt,
    boolean objectsDeleted,
    boolean derivedDeleted,
    boolean searchDeleted) {}
