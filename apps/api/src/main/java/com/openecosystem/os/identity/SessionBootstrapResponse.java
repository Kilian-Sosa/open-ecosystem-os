package com.openecosystem.os.identity;

import java.util.List;

public record SessionBootstrapResponse(
    String authMode,
    boolean authenticated,
    SessionActorResponse actor,
    SessionWorkspaceResponse workspace,
    List<String> roles) {

  public static SessionBootstrapResponse from(SeededSession session, boolean authenticated) {
    return new SessionBootstrapResponse(
        session.authMode(),
        authenticated,
        new SessionActorResponse(
            session.actorId(),
            session.displayName(),
            session.email(),
            session.avatarInitials()),
        new SessionWorkspaceResponse(
            session.workspaceId(), session.workspaceName(), session.workspaceSlug()),
        session.roles().stream().sorted().toList());
  }
}
