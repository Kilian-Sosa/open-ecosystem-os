package com.openecosystem.os.drive;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DriveTrashConfiguration {
  @Bean
  @ConditionalOnMissingBean(Clock.class)
  Clock driveClock() {
    return Clock.systemUTC();
  }
}
