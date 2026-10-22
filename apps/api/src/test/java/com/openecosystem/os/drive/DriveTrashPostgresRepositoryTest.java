package com.openecosystem.os.drive;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DriveTrashPostgresRepositoryTest extends DriveTrashRepositoryTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  @org.junit.jupiter.api.Test
  void concurrentCleanupClaimsHaveOneOwner() throws Exception {
    repository.createGroup(
        DriveTrashGroup.trashed(
            "concurrent",
            WORKSPACE,
            "file",
            "legacy",
            null,
            "usr_dev_placeholder",
            NOW.minus(java.time.Duration.ofDays(30))));
    String schema = jdbc.queryForObject("select current_schema()", String.class);
    String url =
        POSTGRES.getJdbcUrl()
            + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
            + "currentSchema="
            + schema;
    var separateConnections =
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            url, POSTGRES.getUsername(), POSTGRES.getPassword());
    var claims = new DriveTrashRepository(new JdbcTemplate(separateConnections));
    var start = new java.util.concurrent.CountDownLatch(1);
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      java.util.concurrent.Callable<java.util.List<DriveTrashCleanupClaim>> work =
          () -> {
            start.await();
            return claims.claimCleanupWork(NOW, 1);
          };
      var first = executor.submit(work);
      var second = executor.submit(work);
      start.countDown();
      org.assertj.core.api.Assertions.assertThat(
              first.get(10, java.util.concurrent.TimeUnit.SECONDS).size()
                  + second.get(10, java.util.concurrent.TimeUnit.SECONDS).size())
          .isEqualTo(1);
    }
  }

  @Override
  SingleConnectionDataSource createDatabase() {
    var dataSource =
        new SingleConnectionDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), true);
    var connection = new JdbcTemplate(dataSource);
    String schema = "trash_" + UUID.randomUUID().toString().replace("-", "");
    connection.execute("create schema " + schema);
    connection.execute("set search_path to " + schema);
    connection.execute("set time zone 'Atlantic/Canary'");
    return dataSource;
  }
}
