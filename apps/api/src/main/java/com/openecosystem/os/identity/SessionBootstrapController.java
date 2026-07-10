package com.openecosystem.os.identity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class SessionBootstrapController {

  private final SessionBootstrapService sessionBootstrapService;

  public SessionBootstrapController(SessionBootstrapService sessionBootstrapService) {
    this.sessionBootstrapService = sessionBootstrapService;
  }

  @GetMapping("/bootstrap")
  public SessionBootstrapResponse bootstrap() {
    return sessionBootstrapService.currentSession();
  }
}
