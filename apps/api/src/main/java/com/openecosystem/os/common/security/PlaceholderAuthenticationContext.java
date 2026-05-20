package com.openecosystem.os.common.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class PlaceholderAuthenticationContext implements AuthenticationContext {

  public static final String ACTOR_HEADER = "X-Actor-Id";
  public static final String WORKSPACE_HEADER = "X-Workspace-Id";
  public static final String DEFAULT_ACTOR_ID = "usr_dev_placeholder";
  public static final String DEFAULT_WORKSPACE_ID = "wrk_dev_placeholder";

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

    return new AuthenticatedPrincipal(
        actorId, workspaceId, Set.of("DEVELOPMENT_PLACEHOLDER"), true);
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
}
