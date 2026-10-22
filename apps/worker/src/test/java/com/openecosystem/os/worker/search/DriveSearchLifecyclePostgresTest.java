package com.openecosystem.os.worker.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DriveSearchLifecyclePostgresTest extends DriveSearchLifecycleProcessorTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @Override
  SingleConnectionDataSource createDatabase() {
    var source =
        new SingleConnectionDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), true);
    var root = new JdbcTemplate(source);
    String schema = "remote_" + UUID.randomUUID().toString().replace("-", "");
    root.execute("create schema " + schema);
    root.execute("set search_path to " + schema);
    return source;
  }

  @Test
  void concurrentReconcilersSubmitOnlyOneRemoteOperation() throws Exception {
    String schema = jdbc.queryForObject("select current_schema()", String.class);
    var source =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl()
                + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
                + "currentSchema="
                + schema,
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    var concurrentJdbc = new JdbcTemplate(source);
    var concurrent =
        new DriveSearchLifecycleProcessor(
            concurrentJdbc,
            documents,
            new com.openecosystem.os.worker.drive.DriveLifecycleFence(concurrentJdbc),
            remote,
            new TransactionTemplate(new JdbcTransactionManager(source)),
            Clock.fixed(NOW, ZoneOffset.UTC));
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<Void> work =
          () -> {
            start.await();
            concurrent.reconcileFile("wrk_test", "file_test");
            return null;
          };
      var first = pool.submit(work);
      var second = pool.submit(work);
      start.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    }
    assertThat(remote.upserts).isEqualTo(1);
    assertThat(complete()).isFalse();
  }
}
