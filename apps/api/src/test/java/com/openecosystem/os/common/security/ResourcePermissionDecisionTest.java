package com.openecosystem.os.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.drive.DriveFileMetadata;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ResourcePermissionDecisionTest {

  private static final Instant NOW = Instant.parse("2026-07-13T12:00:00Z");

  private final ResourcePermissionDecision decision = new ResourcePermissionDecision();

  @Test
  void permitsPrivateFileOwnerToViewTheFile() {
    assertThat(
            decision.mayViewFile(principal("usr_owner", "wrk_test"), file("wrk_test", "usr_owner")))
        .isTrue();
  }

  @Test
  void deniesUnsharedWorkspaceMemberFromViewingPrivateFile() {
    assertThat(
            decision.mayViewFile(
                principal("usr_unshared", "wrk_test"), file("wrk_test", "usr_owner")))
        .isFalse();
  }

  @Test
  void deniesPrincipalFromAnotherWorkspace() {
    assertThat(
            decision.mayViewFile(
                principal("usr_owner", "wrk_other"), file("wrk_test", "usr_owner")))
        .isFalse();
  }

  private AuthenticatedPrincipal principal(String actorId, String workspaceId) {
    return new AuthenticatedPrincipal(actorId, workspaceId, Set.of("VIEWER"), true);
  }

  private DriveFileMetadata file(String workspaceId, String ownerId) {
    return new DriveFileMetadata(
        "file_test",
        workspaceId,
        ownerId,
        "encrypted-name",
        "application/pdf",
        10,
        "checksum",
        "storage-key",
        "AES-256-GCM",
        "key-id",
        "content-iv",
        "name-iv",
        NOW,
        NOW);
  }
}
