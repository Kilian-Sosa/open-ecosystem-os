package com.openecosystem.os.worker.metrics;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsController {

  private static final String PROMETHEUS_TEXT_CONTENT_TYPE =
      "text/plain; version=0.0.4; charset=utf-8";

  private final PrometheusMeterRegistry prometheusMeterRegistry;

  public MetricsController(PrometheusMeterRegistry prometheusMeterRegistry) {
    this.prometheusMeterRegistry = prometheusMeterRegistry;
  }

  @GetMapping(value = "/metrics", produces = PROMETHEUS_TEXT_CONTENT_TYPE)
  public String metrics() {
    return prometheusMeterRegistry.scrape();
  }
}
