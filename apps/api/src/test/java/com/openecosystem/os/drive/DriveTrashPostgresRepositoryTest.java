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

  @org.junit.jupiter.api.Test
  void restoreAndPurgeShareTheWorkspaceFenceAndOnlyOneWins() throws Exception {
    repository.createGroup(
        DriveTrashGroup.trashed(
            "restore-race", WORKSPACE, "file", "legacy", null, "usr_dev_placeholder", NOW));
    repository.attachFile(WORKSPACE, "legacy", "restore-race");
    String schema = jdbc.queryForObject("select current_schema()", String.class);
    String url =
        POSTGRES.getJdbcUrl()
            + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
            + "currentSchema="
            + schema;
    var dataSource =
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            url, POSTGRES.getUsername(), POSTGRES.getPassword());
    var concurrentJdbc = new JdbcTemplate(dataSource);
    var concurrentRepository = new DriveTrashRepository(concurrentJdbc);
    var transaction =
        new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.support.JdbcTransactionManager(dataSource));
    var gate = new java.util.concurrent.CountDownLatch(1);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var restore =
          pool.submit(
              () -> {
                gate.await();
                return transaction.execute(
                    status -> {
                      new DriveFolderRepository(concurrentJdbc).lockWorkspaceForMutation(WORKSPACE);
                      concurrentRepository.lockGroup("restore-race", WORKSPACE).orElseThrow();
                      return concurrentRepository.restoreGroup("restore-race", WORKSPACE, NOW);
                    });
              });
      var purge =
          pool.submit(
              () -> {
                gate.await();
                return concurrentRepository
                    .claimForPurge("restore-race", WORKSPACE, NOW)
                    .isPresent();
              });
      gate.countDown();
      boolean restored = restore.get(10, java.util.concurrent.TimeUnit.SECONDS);
      boolean claimed = purge.get(10, java.util.concurrent.TimeUnit.SECONDS);
      org.assertj.core.api.Assertions.assertThat(restored).isNotEqualTo(claimed);
      org.assertj.core.api.Assertions.assertThat(
              repository.findByIdForWorkspace("restore-race", WORKSPACE).orElseThrow().state())
          .isEqualTo(restored ? DriveTrashState.RESTORED : DriveTrashState.PURGING);
      org.assertj.core.api.Assertions.assertThat(repository.isActiveFile(WORKSPACE, "legacy", NOW))
          .isEqualTo(restored);
    }
  }

  @org.junit.jupiter.api.Test
  void lockedCleanupRenewsPastExpiryBeforeACompetingClaimCanTakeOver() throws Exception {
    repository.createGroup(
        DriveTrashGroup.trashed(
            "locked-renewal", WORKSPACE, "file", "legacy", null, "usr_dev_placeholder", NOW));
    var claim = repository.claimForPurge("locked-renewal", WORKSPACE, NOW).orElseThrow();
    String schema = jdbc.queryForObject("select current_schema()", String.class);
    String url =
        POSTGRES.getJdbcUrl()
            + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?")
            + "currentSchema="
            + schema;
    var dataSource =
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            url, POSTGRES.getUsername(), POSTGRES.getPassword());
    var concurrentJdbc = new JdbcTemplate(dataSource);
    var concurrentRepository = new DriveTrashRepository(concurrentJdbc);
    var transaction =
        new org.springframework.transaction.support.TransactionTemplate(
            new org.springframework.jdbc.support.JdbcTransactionManager(dataSource));
    var locked = new java.util.concurrent.CountDownLatch(1);
    var finishCleanup = new java.util.concurrent.CountDownLatch(1);
    var competitorStarted = new java.util.concurrent.CountDownLatch(1);
    var afterExpiry = NOW.plusSeconds(360);
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var cleanup =
          pool.submit(
              () ->
                  transaction.execute(
                      status -> {
                        new DriveFolderRepository(concurrentJdbc)
                            .lockWorkspaceForMutation(WORKSPACE);
                        concurrentRepository.lockGroup("locked-renewal", WORKSPACE).orElseThrow();
                        org.assertj.core.api.Assertions.assertThat(
                                concurrentRepository.renewClaim(claim, NOW))
                            .isTrue();
                        locked.countDown();
                        try {
                          if (!finishCleanup.await(10, java.util.concurrent.TimeUnit.SECONDS))
                            throw new IllegalStateException("Synthetic cleanup barrier timed out");
                        } catch (InterruptedException exception) {
                          Thread.currentThread().interrupt();
                          throw new IllegalStateException(exception);
                        }
                        return concurrentRepository.renewLockedClaim(claim, afterExpiry);
                      }));
      org.assertj.core.api.Assertions.assertThat(
              locked.await(10, java.util.concurrent.TimeUnit.SECONDS))
          .isTrue();
      var competitor =
          pool.submit(
              () -> {
                competitorStarted.countDown();
                return concurrentRepository.claimForPurge("locked-renewal", WORKSPACE, afterExpiry);
              });
      try {
        org.assertj.core.api.Assertions.assertThat(
                competitorStarted.await(10, java.util.concurrent.TimeUnit.SECONDS))
            .isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> competitor.get(200, java.util.concurrent.TimeUnit.MILLISECONDS))
            .isInstanceOf(java.util.concurrent.TimeoutException.class);
      } finally {
        finishCleanup.countDown();
      }
      org.assertj.core.api.Assertions.assertThat(
              cleanup.get(10, java.util.concurrent.TimeUnit.SECONDS))
          .isTrue();
      org.assertj.core.api.Assertions.assertThat(
              competitor.get(10, java.util.concurrent.TimeUnit.SECONDS))
          .isEmpty();
      var recovered =
          concurrentRepository
              .claimForPurge("locked-renewal", WORKSPACE, afterExpiry.plusSeconds(360))
              .orElseThrow();
      org.assertj.core.api.Assertions.assertThat(recovered.token()).isNotEqualTo(claim.token());
      transaction.executeWithoutResult(
          status -> {
            new DriveFolderRepository(concurrentJdbc).lockWorkspaceForMutation(WORKSPACE);
            concurrentRepository.lockGroup("locked-renewal", WORKSPACE).orElseThrow();
            org.assertj.core.api.Assertions.assertThat(
                    concurrentRepository.renewLockedClaim(claim, afterExpiry.plusSeconds(360)))
                .isFalse();
          });
    } finally {
      finishCleanup.countDown();
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
