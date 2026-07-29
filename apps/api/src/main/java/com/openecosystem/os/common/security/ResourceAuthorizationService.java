package com.openecosystem.os.common.security;

import java.util.Collection;
import java.util.Set;

public interface ResourceAuthorizationService {

  AuthorizationDecision decide(
      AuthenticatedPrincipal principal,
      String workspaceId,
      ResourceType resourceType,
      String resourceId,
      ResourceAction action);

  Set<String> allowedResourceIds(
      AuthenticatedPrincipal principal,
      String workspaceId,
      ResourceType resourceType,
      Collection<String> resourceIds,
      ResourceAction action);
}
