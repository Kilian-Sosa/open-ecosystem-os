package com.openecosystem.os.common.security;

import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.identity.SeededSession;
import com.openecosystem.os.identity.SeededSessionDefaults;
import com.openecosystem.os.identity.SeededSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class PlaceholderAuthenticationContext implements AuthenticationContext {

  public static final String ACTOR_HEADER = "X-Actor-Id";
  public static final String WORKSPACE_HEADER = "X-Workspace-Id";
  public static final String DEFAULT_ACTOR_ID = SeededSessionDefaults.DEFAULT_ACTOR_ID;
  public static final String DEFAULT_WORKSPACE_ID = SeededSessionDefaults.DEFAULT_WORKSPACE_ID;

  private final SeededSessionRepository sessionRepository;

  public PlaceholderAuthenticationContext(SeededSessionRepository sessionRepository) {
    this.sessionRepository = sessionRepository;
  }

  @Override
  public AuthenticatedPrincipal currentPrincipal() {
    HttpServletRequest request = currentRequest();
    String actorId =
        request == null
            ? DEFAULT_ACTOR_ID
            : valueOrDefault(request, ACTOR_HEADER, DEFAULT_ACTOR_ID);
    String workspaceId =
        request == null
            ? DEFAULT_WORKSPACE_ID
            : valueOrDefault(request, WORKSPACE_HEADER, DEFAULT_WORKSPACE_ID);

    return sessionRepository
        .findActiveSession(actorId, workspaceId)
        .or(() -> defaultSession(actorId, workspaceId))
        .map(
            session ->
                new AuthenticatedPrincipal(
                    session.actorId(), session.workspaceId(), session.roles(), true))
        .orElseThrow(() -> forbidden(actorId, workspaceId));
  }

  private HttpServletRequest currentRequest() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)
      return attributes.getRequest();
    return null;
  }

  private String valueOrDefault(
      HttpServletRequest request, String headerName, String defaultValue) {
    String value = request.getHeader(headerName);
    return value == null || value.isBlank() ? defaultValue : value.trim();
  }

  private Optional<SeededSession> defaultSession(String actorId, String workspaceId) {
    if (!SeededSessionDefaults.isDefaultMembership(actorId, workspaceId)) return Optional.empty();
    return Optional.of(SeededSessionDefaults.defaultSession());
  }

  private ApiException forbidden(String actorId, String workspaceId) {
    return new ApiException(
        HttpStatus.FORBIDDEN,
        ApiErrorCode.FORBIDDEN,
        "Actor is not a member of the workspace",
        Map.of("actorId", actorId, "workspaceId", workspaceId));
  }
}
