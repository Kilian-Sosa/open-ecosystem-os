package com.openecosystem.os.worker.search;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
public class DriveSearchLifecycleScheduling {
  @Bean
  @ConditionalOnMissingBean(Clock.class)
  Clock lifecycleClock() {
    return Clock.systemUTC();
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "openecosystem.search.lifecycle",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  Poller driveSearchLifecyclePoller(DriveSearchLifecycleProcessor processor) {
    return new Poller(processor);
  }

  static class Poller {
    private final DriveSearchLifecycleProcessor processor;

    Poller(DriveSearchLifecycleProcessor processor) {
      this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${openecosystem.search.lifecycle.poll-ms:2000}")
    public void poll() {
      processor.runDue(25);
    }
  }
}
