package com.openecosystem.os.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.identity.SeededSession;
import com.openecosystem.os.identity.SeededSessionRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class PlaceholderAuthenticationContextTest {

  private final InMemorySeededSessionRepository sessionRepository =
      new InMemorySeededSessionRepository();
  private final PlaceholderAuthenticationContext authenticationContext =
      new PlaceholderAuthenticationContext(sessionRepository);

  @AfterEach
  void clearRequestContext() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void returnsDevelopmentPlaceholderWhenNoRequestExists() {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();

    assertThat(principal.actorId()).isEqualTo(PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID);
    assertThat(principal.workspaceId())
        .isEqualTo(PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID);
    assertThat(principal.roles()).contains("WORKSPACE_ADMIN", "DEVELOPMENT_PLACEHOLDER");
  }

  @Test
  void readsSeededMembershipFromHeaders() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(
        PlaceholderAuthenticationContext.ACTOR_HEADER,
        PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID);
    request.addHeader(
        PlaceholderAuthenticationContext.WORKSPACE_HEADER,
        PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();

    assertThat(principal.actorId()).isEqualTo(PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID);
    assertThat(principal.workspaceId())
        .isEqualTo(PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID);
    assertThat(principal.roles()).contains("WORKSPACE_ADMIN");
    assertThat(principal.authenticated()).isTrue();
  }

  @Test
  void rejectsHeaderIdsWithoutSeededMembership() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(PlaceholderAuthenticationContext.ACTOR_HEADER, "usr_unknown");
    request.addHeader(
        PlaceholderAuthenticationContext.WORKSPACE_HEADER,
        PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    assertThatThrownBy(authenticationContext::currentPrincipal)
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("Actor is not a member of the workspace");
  }

  private static final class InMemorySeededSessionRepository implements SeededSessionRepository {

    @Override
    public Optional<SeededSession> findActiveSession(String actorId, String workspaceId) {
      if (!PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID.equals(actorId)
          || !PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID.equals(workspaceId)) {
        return Optional.empty();
      }
      return Optional.of(
          new SeededSession(
              actorId,
              "Demo Admin",
              "demo.admin@example.test",
              "DA",
              workspaceId,
              "Open Ecosystem Demo Workspace",
              "demo",
              Set.of("WORKSPACE_ADMIN", "DEVELOPMENT_PLACEHOLDER"),
              "seeded_dev"));
    }
  }
}
