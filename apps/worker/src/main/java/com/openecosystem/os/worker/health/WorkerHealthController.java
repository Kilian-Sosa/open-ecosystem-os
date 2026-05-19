package com.openecosystem.os.worker.health;

import java.time.Instant;
import java.util.Map;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkerHealthController {

  private static final String SERVICE_NAME = "open-ecosystem-worker";

  private final ApplicationAvailability applicationAvailability;

  public WorkerHealthController(ApplicationAvailability applicationAvailability) {
    this.applicationAvailability = applicationAvailability;
  }

  @GetMapping("/health")
  public WorkerHealthResponse health() {
    return new WorkerHealthResponse("UP", SERVICE_NAME, Instant.now());
  }

  @GetMapping("/ready")
  public ResponseEntity<WorkerReadinessResponse> ready() {
    ReadinessState readinessState = applicationAvailability.getReadinessState();
    boolean ready = readinessState == ReadinessState.ACCEPTING_TRAFFIC;
    WorkerReadinessResponse response =
        new WorkerReadinessResponse(
            ready ? "READY" : "NOT_READY",
            readinessState.name(),
            Map.of("application", readinessState.name()),
            Instant.now());

    return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
        .body(response);
  }
}
