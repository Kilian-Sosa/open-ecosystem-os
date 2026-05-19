package com.openecosystem.os.admin.health;

import com.openecosystem.os.common.security.CorrelationContext;
import java.time.Instant;
import java.util.Map;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  private static final String SERVICE_NAME = "open-ecosystem-api";

  private final ApplicationAvailability applicationAvailability;

  public HealthController(ApplicationAvailability applicationAvailability) {
    this.applicationAvailability = applicationAvailability;
  }

  @GetMapping("/health")
  public HealthResponse health() {
    return new HealthResponse(
        "UP", SERVICE_NAME, Instant.now(), CorrelationContext.currentOrCreate());
  }

  @GetMapping("/ready")
  public ResponseEntity<ReadinessResponse> ready() {
    ReadinessState readinessState = applicationAvailability.getReadinessState();
    boolean ready = readinessState == ReadinessState.ACCEPTING_TRAFFIC;
    ReadinessResponse response =
        new ReadinessResponse(
            ready ? "READY" : "NOT_READY",
            readinessState.name(),
            Map.of("application", readinessState.name()),
            Instant.now(),
            CorrelationContext.currentOrCreate());

    return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
        .body(response);
  }
}
