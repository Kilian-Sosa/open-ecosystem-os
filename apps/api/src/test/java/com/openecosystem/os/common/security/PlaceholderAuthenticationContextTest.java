package com.openecosystem.os.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class PlaceholderAuthenticationContextTest {

  private final PlaceholderAuthenticationContext authenticationContext =
      new PlaceholderAuthenticationContext();

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
  }

  @Test
  void readsPlaceholderIdsFromHeaders() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(PlaceholderAuthenticationContext.ACTOR_HEADER, "usr_123");
    request.addHeader(PlaceholderAuthenticationContext.WORKSPACE_HEADER, "wrk_123");
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();

    assertThat(principal.actorId()).isEqualTo("usr_123");
    assertThat(principal.workspaceId()).isEqualTo("wrk_123");
    assertThat(principal.authenticated()).isTrue();
  }
}
