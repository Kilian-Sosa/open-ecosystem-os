package com.openecosystem.os.identity;

import java.util.Set;

public record SeededSession(
    String actorId,
    String displayName,
    String email,
    String avatarInitials,
    String workspaceId,
    String workspaceName,
    String workspaceSlug,
    Set<String> roles,
    String authMode) {

  public SeededSession {
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }
}
