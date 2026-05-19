package com.openecosystem.os.common.security;

import java.util.Set;

public record AuthenticatedPrincipal(
    String actorId, String workspaceId, Set<String> roles, boolean authenticated) {

  public AuthenticatedPrincipal {
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }
}
