package com.openecosystem.os.common.security;

public record AuthorizationDecision(boolean allowed, AuthorizationDecisionCode code) {

  public static AuthorizationDecision allow(AuthorizationDecisionCode code) {
    return new AuthorizationDecision(true, code);
  }

  public static AuthorizationDecision deny(AuthorizationDecisionCode code) {
    return new AuthorizationDecision(false, code);
  }
}
