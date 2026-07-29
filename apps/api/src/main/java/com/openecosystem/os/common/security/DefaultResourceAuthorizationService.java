package com.openecosystem.os.common.security;

import com.openecosystem.os.drive.DriveFileVisibility;
import com.openecosystem.os.drive.FileViewAuthorizationFacts;
import com.openecosystem.os.drive.FileViewAuthorizationRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DefaultResourceAuthorizationService implements ResourceAuthorizationService {

  private static final int FILE_VIEW_BATCH_CHUNK_SIZE = 500;

  private final FileViewAuthorizationRepository fileViewAuthorizationRepository;

  public DefaultResourceAuthorizationService(
      FileViewAuthorizationRepository fileViewAuthorizationRepository) {
    this.fileViewAuthorizationRepository = fileViewAuthorizationRepository;
  }

  @Override
  public AuthorizationDecision decide(
      AuthenticatedPrincipal principal,
      String workspaceId,
      ResourceType resourceType,
      String resourceId,
      ResourceAction action) {
    if (principal == null || !principal.authenticated()) {
      return AuthorizationDecision.deny(AuthorizationDecisionCode.DENY_UNAUTHENTICATED);
    }
    if (!Objects.equals(workspaceId, principal.workspaceId())) {
      return AuthorizationDecision.deny(AuthorizationDecisionCode.DENY_WORKSPACE_MISMATCH);
    }
    if (resourceType != ResourceType.FILE || action != ResourceAction.VIEW) {
      return AuthorizationDecision.deny(AuthorizationDecisionCode.DENY_UNSUPPORTED);
    }

    FileViewAuthorizationFacts facts =
        fileViewAuthorizationRepository
            .findFacts(principal.actorId(), workspaceId, resourceId)
            .orElse(null);
    if (facts == null) {
      return AuthorizationDecision.deny(AuthorizationDecisionCode.DENY_RESOURCE_NOT_FOUND);
    }
    if (!facts.activeMember()) {
      return AuthorizationDecision.deny(AuthorizationDecisionCode.DENY_INACTIVE_MEMBERSHIP);
    }
    if (principal.actorId().equals(facts.ownerId())) {
      return AuthorizationDecision.allow(AuthorizationDecisionCode.ALLOW_OWNER);
    }
    if (facts.visibility() == DriveFileVisibility.WORKSPACE && facts.workspaceVisibilityRole()) {
      return AuthorizationDecision.allow(AuthorizationDecisionCode.ALLOW_WORKSPACE_VISIBILITY);
    }
    if (facts.activeUserGrant()) {
      return AuthorizationDecision.allow(AuthorizationDecisionCode.ALLOW_USER_GRANT);
    }
    return AuthorizationDecision.deny(AuthorizationDecisionCode.DENY_NO_POLICY);
  }

  @Override
  public Set<String> allowedResourceIds(
      AuthenticatedPrincipal principal,
      String workspaceId,
      ResourceType resourceType,
      Collection<String> resourceIds,
      ResourceAction action) {
    if (principal == null
        || !principal.authenticated()
        || !Objects.equals(workspaceId, principal.workspaceId())
        || resourceType != ResourceType.FILE
        || action != ResourceAction.VIEW
        || resourceIds == null
        || resourceIds.isEmpty()) {
      return Set.of();
    }

    Set<String> candidateIds = new LinkedHashSet<>();
    for (String resourceId : resourceIds) {
      if (resourceId != null && !resourceId.isBlank()) {
        candidateIds.add(resourceId);
      }
    }
    if (candidateIds.isEmpty()) {
      return Set.of();
    }

    List<String> candidates = new ArrayList<>(candidateIds);
    Set<String> allowedIds = new LinkedHashSet<>();
    for (int start = 0; start < candidates.size(); start += FILE_VIEW_BATCH_CHUNK_SIZE) {
      int end = Math.min(start + FILE_VIEW_BATCH_CHUNK_SIZE, candidates.size());
      allowedIds.addAll(
          fileViewAuthorizationRepository.findAllowedFileIds(
              principal.actorId(),
              workspaceId,
              new LinkedHashSet<>(candidates.subList(start, end))));
    }
    return Set.copyOf(allowedIds);
  }
}
