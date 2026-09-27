package com.openecosystem.os.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openecosystem.os.drive.DriveFileVisibility;
import com.openecosystem.os.drive.FileViewAuthorizationFacts;
import com.openecosystem.os.drive.FileViewAuthorizationRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultResourceAuthorizationServiceTest {

  private static final String ACTOR_ID = "usr_actor";
  private static final String WORKSPACE_ID = "wrk_one";
  private static final String FILE_ID = "file_one";

  @Mock private FileViewAuthorizationRepository repository;

  private DefaultResourceAuthorizationService service;

  @BeforeEach
  void setUp() {
    service = new DefaultResourceAuthorizationService(repository);
  }

  @Test
  void allowsAnActiveSameWorkspaceOwner() {
    stubFacts(facts(ACTOR_ID, DriveFileVisibility.PRIVATE, true, false, false));

    assertThat(decide()).isEqualTo(allowed(AuthorizationDecisionCode.ALLOW_OWNER));
  }

  @Test
  void doesNotAllowAPrivateFileToAnAdministrator() {
    stubFacts(facts("usr_owner", DriveFileVisibility.PRIVATE, true, true, false));

    assertThat(decide()).isEqualTo(denied(AuthorizationDecisionCode.DENY_NO_POLICY));
  }

  @Test
  void doesNotAllowAPrivateFileToWorkspaceContentRoles() {
    stubFacts(facts("usr_owner", DriveFileVisibility.PRIVATE, true, true, false));

    assertThat(decide()).isEqualTo(denied(AuthorizationDecisionCode.DENY_NO_POLICY));
  }

  @Test
  void allowsAWorkspaceVisibleFileToAnEligibleActiveMember() {
    stubFacts(facts("usr_owner", DriveFileVisibility.WORKSPACE, true, true, false));

    assertThat(decide()).isEqualTo(allowed(AuthorizationDecisionCode.ALLOW_WORKSPACE_VISIBILITY));
  }

  @Test
  void deniesAWorkspaceVisibleFileToAGuestWithoutAGrant() {
    stubFacts(facts("usr_owner", DriveFileVisibility.WORKSPACE, true, false, false));

    assertThat(decide()).isEqualTo(denied(AuthorizationDecisionCode.DENY_NO_POLICY));
  }

  @Test
  void deniesAWorkspaceVisibleFileToAnAuditorWithoutAGrant() {
    stubFacts(facts("usr_owner", DriveFileVisibility.WORKSPACE, true, false, false));

    assertThat(decide()).isEqualTo(denied(AuthorizationDecisionCode.DENY_NO_POLICY));
  }

  @Test
  void allowsAnActiveExplicitUserGrantForAPrivateFile() {
    stubFacts(facts("usr_owner", DriveFileVisibility.PRIVATE, true, false, true));

    assertThat(decide()).isEqualTo(allowed(AuthorizationDecisionCode.ALLOW_USER_GRANT));
  }

  @Test
  void allowsAnActiveExplicitGuestOrAuditorGrant() {
    stubFacts(facts("usr_owner", DriveFileVisibility.WORKSPACE, true, false, true));

    assertThat(decide()).isEqualTo(allowed(AuthorizationDecisionCode.ALLOW_USER_GRANT));
  }

  @Test
  void deniesARevokedOrAbsentGrant() {
    stubFacts(facts("usr_owner", DriveFileVisibility.PRIVATE, true, false, false));

    assertThat(decide()).isEqualTo(denied(AuthorizationDecisionCode.DENY_NO_POLICY));
  }

  @Test
  void deniesAnInactiveMemberBeforeOwnershipOrAGrant() {
    stubFacts(facts(ACTOR_ID, DriveFileVisibility.PRIVATE, false, false, true));

    assertThat(decide()).isEqualTo(denied(AuthorizationDecisionCode.DENY_INACTIVE_MEMBERSHIP));
  }

  @Test
  void deniesAPrincipalWhoseWorkspaceDoesNotMatchTheRequest() {
    assertThat(
            service.decide(
                principal(), "wrk_other", ResourceType.FILE, FILE_ID, ResourceAction.VIEW))
        .isEqualTo(denied(AuthorizationDecisionCode.DENY_WORKSPACE_MISMATCH));
    verify(repository, never()).findFacts(any(), any(), any());
  }

  @Test
  void deniesAMissingOrForeignWorkspaceFile() {
    when(repository.findFacts(ACTOR_ID, WORKSPACE_ID, FILE_ID)).thenReturn(Optional.empty());

    assertThat(decide()).isEqualTo(denied(AuthorizationDecisionCode.DENY_RESOURCE_NOT_FOUND));
  }

  @Test
  void deniesUnsupportedResourceAndActionCombinations() {
    assertThat(service.decide(principal(), WORKSPACE_ID, null, FILE_ID, ResourceAction.VIEW))
        .isEqualTo(denied(AuthorizationDecisionCode.DENY_UNSUPPORTED));
    assertThat(service.decide(principal(), WORKSPACE_ID, ResourceType.FILE, FILE_ID, null))
        .isEqualTo(denied(AuthorizationDecisionCode.DENY_UNSUPPORTED));
    verify(repository, never()).findFacts(any(), any(), any());
  }

  @Test
  void returnsOwnerBeforeWorkspaceVisibilityAndUserGrant() {
    stubFacts(facts(ACTOR_ID, DriveFileVisibility.WORKSPACE, true, true, true));

    assertThat(decide()).isEqualTo(allowed(AuthorizationDecisionCode.ALLOW_OWNER));
  }

  @Test
  void returnsWorkspaceVisibilityBeforeAnExplicitGrant() {
    stubFacts(facts("usr_owner", DriveFileVisibility.WORKSPACE, true, true, true));

    assertThat(decide()).isEqualTo(allowed(AuthorizationDecisionCode.ALLOW_WORKSPACE_VISIBILITY));
  }

  @Test
  void deniesNullOrUnauthenticatedPrincipals() {
    assertThat(service.decide(null, WORKSPACE_ID, ResourceType.FILE, FILE_ID, ResourceAction.VIEW))
        .isEqualTo(denied(AuthorizationDecisionCode.DENY_UNAUTHENTICATED));
    assertThat(
            service.decide(
                new AuthenticatedPrincipal(ACTOR_ID, WORKSPACE_ID, Set.of(), false),
                WORKSPACE_ID,
                ResourceType.FILE,
                FILE_ID,
                ResourceAction.VIEW))
        .isEqualTo(denied(AuthorizationDecisionCode.DENY_UNAUTHENTICATED));
    verify(repository, never()).findFacts(any(), any(), any());
  }

  @Test
  void returnsAnEmptyImmutableSetForUnsupportedMismatchAndEmptyBatchRequests() {
    assertThat(
            service.allowedResourceIds(
                principal(), WORKSPACE_ID, null, List.of(FILE_ID), ResourceAction.VIEW))
        .isEmpty();
    assertThat(
            service.allowedResourceIds(
                principal(), "wrk_other", ResourceType.FILE, List.of(FILE_ID), ResourceAction.VIEW))
        .isEmpty();
    Set<String> empty =
        service.allowedResourceIds(
            principal(), WORKSPACE_ID, ResourceType.FILE, List.of(), ResourceAction.VIEW);
    assertThat(empty).isEmpty();
    assertThatThrownBy(() -> empty.add(FILE_ID)).isInstanceOf(UnsupportedOperationException.class);
    verify(repository, never()).findAllowedFileIds(any(), any(), any());
  }

  @Test
  void delegatesDistinctNonBlankBatchIdsAndReturnsAnImmutableSet() {
    when(repository.findAllowedFileIds(ACTOR_ID, WORKSPACE_ID, Set.of("file_one", "file_two")))
        .thenReturn(Set.of("file_two"));

    Set<String> allowed =
        service.allowedResourceIds(
            principal(),
            WORKSPACE_ID,
            ResourceType.FILE,
            java.util.Arrays.asList(FILE_ID, " ", null, "file_two", FILE_ID),
            ResourceAction.VIEW);

    assertThat(allowed).containsExactly("file_two");
    assertThatThrownBy(() -> allowed.add(FILE_ID))
        .isInstanceOf(UnsupportedOperationException.class);
    verify(repository).findAllowedFileIds(ACTOR_ID, WORKSPACE_ID, Set.of("file_one", "file_two"));
  }

  private AuthorizationDecision decide() {
    return service.decide(
        principal(), WORKSPACE_ID, ResourceType.FILE, FILE_ID, ResourceAction.VIEW);
  }

  private void stubFacts(FileViewAuthorizationFacts facts) {
    when(repository.findFacts(ACTOR_ID, WORKSPACE_ID, FILE_ID)).thenReturn(Optional.of(facts));
  }

  private AuthenticatedPrincipal principal() {
    return new AuthenticatedPrincipal(ACTOR_ID, WORKSPACE_ID, Set.of("WORKSPACE_ADMIN"), true);
  }

  private FileViewAuthorizationFacts facts(
      String ownerId,
      DriveFileVisibility visibility,
      boolean activeMember,
      boolean workspaceVisibilityRole,
      boolean activeUserGrant) {
    return new FileViewAuthorizationFacts(
        FILE_ID,
        WORKSPACE_ID,
        ownerId,
        visibility,
        activeMember,
        workspaceVisibilityRole,
        activeUserGrant);
  }

  private AuthorizationDecision allowed(AuthorizationDecisionCode code) {
    return new AuthorizationDecision(true, code);
  }

  private AuthorizationDecision denied(AuthorizationDecisionCode code) {
    return new AuthorizationDecision(false, code);
  }
}
