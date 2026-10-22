package com.openecosystem.os.drive;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "openecosystem.drive.trash.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class DriveTrashPurgeScheduler {
  private final DriveTrashPurgeService service;
  private final Clock clock;
  private final int batchSize;

  public DriveTrashPurgeScheduler(
      DriveTrashPurgeService service,
      Clock clock,
      @Value("${openecosystem.drive.trash.batch-size:25}") int batchSize) {
    this.service = service;
    this.clock = clock;
    this.batchSize = batchSize;
  }

  @Scheduled(
      fixedDelayString = "${openecosystem.drive.trash.purge-poll-ms:3600000}",
      initialDelayString = "${openecosystem.drive.trash.purge-poll-ms:3600000}")
  public void runPurge() {
    service.runCleanup(clock.instant(), batchSize);
  }
}
