package com.openecosystem.os.identity;

import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.AuthenticationContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SessionBootstrapService {

  private final AuthenticationContext authenticationContext;
  private final SeededSessionRepository sessionRepository;

  public SessionBootstrapService(
      AuthenticationContext authenticationContext, SeededSessionRepository sessionRepository) {
    this.authenticationContext = authenticationContext;
    this.sessionRepository = sessionRepository;
  }

  public SessionBootstrapResponse currentSession() {
    AuthenticatedPrincipal principal = authenticationContext.currentPrincipal();
    SeededSession session = seededSessionFor(principal);
    return SessionBootstrapResponse.from(session, principal.authenticated());
  }

  private SeededSession seededSessionFor(AuthenticatedPrincipal principal) {
    return sessionRepository
        .findActiveSession(principal.actorId(), principal.workspaceId())
        .orElseGet(
            () -> {
              if (SeededSessionDefaults.isDefaultMembership(
                  principal.actorId(), principal.workspaceId())) {
                return SeededSessionDefaults.defaultSession();
              }
              throw new ApiException(
                  HttpStatus.FORBIDDEN,
                  ApiErrorCode.FORBIDDEN,
                  "Actor is not a member of the workspace");
            });
  }
}
