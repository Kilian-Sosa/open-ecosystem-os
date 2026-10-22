package com.openecosystem.os.worker.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openecosystem.os.worker.drive.DriveLifecycleFence;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class DriveSearchLifecycleProcessorTest {
  static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
  JdbcTemplate jdbc;
  SearchDocumentRepository documents;
  ControlledClient remote;
  DriveSearchLifecycleProcessor processor;
  TransactionTemplate transactions;
  SearchDocument document;

  SingleConnectionDataSource database;

  @org.junit.jupiter.api.AfterEach
  void close() {
    database.destroy();
  }

  SingleConnectionDataSource createDatabase() {
    return new SingleConnectionDataSource(
        "jdbc:h2:mem:remote_"
            + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "sa",
        "",
        true);
  }

  @BeforeEach
  void setup() throws Exception {
    var data = createDatabase();
    database = data;
    jdbc = new JdbcTemplate(data);
    jdbc.execute("create table workspaces(workspace_id varchar(128) primary key)");
    jdbc.update("insert into workspaces values ('wrk_test')");
    jdbc.execute(
        "create table drive_files(file_id varchar(64),workspace_id varchar(128),folder_id"
            + " varchar(64),deletion_group_id varchar(64))");
    jdbc.execute(
        "create table drive_folders(folder_id varchar(64),workspace_id"
            + " varchar(128),parent_folder_id varchar(64),deletion_group_id varchar(64))");
    jdbc.execute(
        "create table invoice_extractions(extraction_id varchar(64),workspace_id"
            + " varchar(128),file_id varchar(64),status varchar(32))");
    jdbc.update("insert into drive_files values ('file_test','wrk_test',null,null)");
    for (String sql :
        Files.readString(
                Path.of(
                    System.getProperty("basedir", "."),
                    "../api/src/main/resources/db/migration/V12__drive_search_lifecycle.sql"))
            .split(";")) if (!sql.isBlank()) jdbc.execute(sql);
    documents = mock(SearchDocumentRepository.class);
    document =
        new SearchDocument(
            "srch_test",
            "wrk_test",
            "ocr_result",
            "ocr_test",
            "Synthetic",
            "Synthetic",
            "Synthetic",
            "/app/drive",
            "corr_test",
            SearchDocumentStatus.PENDING,
            0,
            3,
            null,
            null,
            new ObjectMapper().readTree("{\"fileId\":\"file_test\"}"),
            NOW,
            NOW,
            null,
            null);
    when(documents.findSourcesForFile(anyString(), anyString()))
        .thenAnswer(
            ignored ->
                List.of(
                    new SearchDocumentRepository.SourceReference(
                        document.searchDocumentId(),
                        document.workspaceId(),
                        document.sourceType(),
                        document.sourceId(),
                        "file_test")));
    when(documents.findById("srch_test")).thenReturn(Optional.of(document));
    when(documents.findSourceById("srch_test"))
        .thenAnswer(
            ignored ->
                Optional.of(
                    new SearchDocumentRepository.SourceReference(
                        document.searchDocumentId(),
                        document.workspaceId(),
                        document.sourceType(),
                        document.sourceId(),
                        "file_test")));
    remote = new ControlledClient();
    transactions = new TransactionTemplate(new JdbcTransactionManager(data));
    restart();
  }

  void restart() {
    processor =
        new DriveSearchLifecycleProcessor(
            jdbc,
            documents,
            new DriveLifecycleFence(jdbc),
            remote,
            transactions,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  void run() {
    processor.reconcileFile("wrk_test", "file_test");
  }

  void trash() {
    jdbc.update("update drive_files set deletion_group_id='trash_test'");
    jdbc.update("update drive_search_lifecycle set revision=revision+1,desired_state='ABSENT'");
  }

  boolean complete() {
    return jdbc.queryForObject(
        "select reconciled_revision=revision from drive_search_lifecycle", Boolean.class);
  }

  @Test
  void lateRemoteUpsertMustFinishBeforeRemovalAndCleanupProof() {
    run();
    trash();
    run();
    assertThat(remote.removals).isZero();
    assertThat(complete()).isFalse();
    remote.finishAll();
    run();
    run();
    assertThat(remote.removals).isEqualTo(1);
    assertThat(complete()).isFalse();
    remote.finishAll();
    run();
    assertThat(remote.present).isEmpty();
    assertThat(complete()).isTrue();
  }

  @Test
  void staleRemovalAfterRestoreCannotLeaveRestoredProjectionMissing() {
    run();
    remote.finishAll();
    run();
    trash();
    run();
    jdbc.update("update drive_files set deletion_group_id=null");
    jdbc.update("update drive_search_lifecycle set revision=revision+1,desired_state='ACTIVE'");
    remote.finishAll();
    run();
    run();
    assertThat(complete()).isFalse();
    remote.finishAll();
    run();
    assertThat(remote.present).containsExactly("srch_test");
    assertThat(complete()).isTrue();
  }

  @Test
  void restartRecoversLostAcknowledgementWithoutResubmittingUpsert() {
    remote.loseAcknowledgement = true;
    run();
    assertThat(remote.upserts).isEqualTo(1);
    trash();
    restart();
    run();
    assertThat(remote.upserts).isEqualTo(1);
    assertThat(complete()).isFalse();
    remote.finishAll();
    run();
    run();
    remote.finishAll();
    run();
    assertThat(remote.present).isEmpty();
    assertThat(complete()).isTrue();
  }

  @Test
  void undiscoveredAmbiguousWriteNeverAdvertisesCleanup() {
    remote.loseAcknowledgement = true;
    remote.hideTask = true;
    run();
    trash();
    restart();
    run();
    run();
    assertThat(complete()).isFalse();
    assertThat(remote.upserts).isEqualTo(1);
    assertThat(remote.removals).isZero();
  }

  @Test
  void previouslyPersistedInvoiceDraftIsRemovedRatherThanReindexed() throws Exception {
    document =
        new SearchDocument(
            document.searchDocumentId(),
            document.workspaceId(),
            "invoice_extraction",
            "inv_test",
            document.title(),
            document.summary(),
            document.content(),
            document.resourceHref(),
            document.correlationId(),
            document.status(),
            0,
            3,
            null,
            null,
            document.metadata(),
            NOW,
            NOW,
            null,
            null);
    when(documents.findSourcesForFile(anyString(), anyString()))
        .thenAnswer(
            ignored ->
                List.of(
                    new SearchDocumentRepository.SourceReference(
                        document.searchDocumentId(),
                        document.workspaceId(),
                        document.sourceType(),
                        document.sourceId(),
                        "file_test")));
    when(documents.findById("srch_test")).thenReturn(Optional.of(document));
    when(documents.findSourceById("srch_test"))
        .thenAnswer(
            ignored ->
                Optional.of(
                    new SearchDocumentRepository.SourceReference(
                        document.searchDocumentId(),
                        document.workspaceId(),
                        document.sourceType(),
                        document.sourceId(),
                        "file_test")));
    jdbc.update(
        "insert into invoice_extractions values"
            + " ('inv_test','wrk_test','file_test','review_required')");
    remote.present.add("srch_test");
    run();
    remote.finishAll();
    run();
    assertThat(remote.upserts).isZero();
    assertThat(remote.present).isEmpty();
  }

  @Test
  void definitiveRejectionIsRetriedByScheduledRecovery() {
    remote.rejectBeforeAcceptance = true;
    run();
    assertThat(remote.upserts).isZero();
    assertThat(complete()).isFalse();
    processor =
        new DriveSearchLifecycleProcessor(
            jdbc,
            documents,
            new DriveLifecycleFence(jdbc),
            remote,
            transactions,
            Clock.fixed(NOW.plusSeconds(11), ZoneOffset.UTC));
    processor.runDue(25);
    assertThat(remote.upserts).isEqualTo(1);
    remote.finishAll();
    run();
    assertThat(complete()).isTrue();
  }

  @Test
  void failedRemovalTaskWithVerifiedAbsenceCompletesCleanup() {
    trash();
    run();
    remote.failed.add(1L);
    run();
    assertThat(remote.removals).isEqualTo(1);
    assertThat(remote.present).isEmpty();
    assertThat(complete()).isTrue();
  }

  @Test
  void failedRemovalTaskWithExistingRemoteCopyCannotCompleteCleanup() {
    trash();
    remote.present.add("srch_test");
    run();
    remote.failed.add(1L);
    run();
    assertThat(complete()).isFalse();
    assertThat(remote.present).containsExactly("srch_test");
  }

  @Test
  void failedAcceptedTaskPersistsRetryDiagnostic() {
    run();
    remote.failed.add(1L);
    run();
    assertThat(
            jdbc.queryForObject("select last_error_code from drive_search_lifecycle", String.class))
        .isEqualTo("REMOTE_TASK_FAILED");
    assertThat(complete()).isFalse();
    processor =
        new DriveSearchLifecycleProcessor(
            jdbc,
            documents,
            new DriveLifecycleFence(jdbc),
            remote,
            transactions,
            Clock.fixed(NOW.plusSeconds(11), ZoneOffset.UTC));
    processor.runDue(25);
    assertThat(remote.upserts).isEqualTo(2);
    remote.finishAll();
    run();
    assertThat(complete()).isTrue();
  }

  @Test
  void lifecycleConsumerUsesPersistedStateForDuplicateAndStaleEvents() {
    var consumer = new DriveFileLifecycleConsumer(new ObjectMapper(), processor);
    String event =
        "{\"eventId\":\"evt_test\",\"eventType\":\"DriveFileLifecycleChanged\",\"version\":1,\"workspaceId\":\"wrk_test\",\"actorId\":\"usr_test\",\"source\":\"drive\",\"correlationId\":\"corr_test\",\"idempotencyKey\":\"drive:file_test:lifecycle:1:v1\",\"payload\":{\"fileId\":\"file_test\",\"revision\":1,\"desiredState\":\"ACTIVE\"}}";
    assertThat(consumer.consume(event)).isTrue();
    assertThat(consumer.consume(event)).isTrue();
    assertThat(remote.upserts).isEqualTo(1);
    trash();
    assertThat(consumer.consume(event)).isTrue();
    assertThat(complete()).isFalse();
    assertThat(remote.removals).isZero();
    remote.finishAll();
    run();
    run();
    remote.finishAll();
    run();
    assertThat(remote.present).isEmpty();
    assertThat(complete()).isTrue();
  }

  static class ControlledClient implements SearchIndexClient {
    record Pending(String id, boolean present, String operation) {}

    final Map<Long, Pending> pending = new HashMap<>();
    final Set<Long> succeeded = new HashSet<>();
    final Set<Long> failed = new HashSet<>();
    final Set<String> present = new HashSet<>();
    long sequence;
    int upserts, removals;
    boolean loseAcknowledgement, hideTask, rejectBeforeAcceptance;

    public void index(SearchDocument d) {
      throw new AssertionError("Untracked upsert");
    }

    public long submitUpsert(SearchDocument d, String operation) {
      if (rejectBeforeAcceptance) {
        rejectBeforeAcceptance = false;
        throw new SearchSubmissionException(false);
      }
      upserts++;
      long id = ++sequence;
      pending.put(id, new Pending(d.searchDocumentId(), true, operation));
      if (loseAcknowledgement) {
        loseAcknowledgement = false;
        throw new SearchSubmissionException(true);
      }
      return id;
    }

    public long submitRemoval(String id, String operation) {
      removals++;
      long uid = ++sequence;
      pending.put(uid, new Pending(id, false, operation));
      return uid;
    }

    public TaskState taskState(long uid) {
      return failed.contains(uid)
          ? TaskState.FAILED
          : succeeded.contains(uid) ? TaskState.SUCCEEDED : TaskState.PENDING;
    }

    public TaskLookup findTask(String operation, Instant startedAt, Long cursor) {
      return new TaskLookup(
          hideTask
              ? OptionalLong.empty()
              : pending.entrySet().stream()
                  .filter(e -> e.getValue().operation().equals(operation))
                  .mapToLong(Map.Entry::getKey)
                  .findFirst(),
          null);
    }

    public boolean isAbsent(String id) {
      return !present.contains(id);
    }

    void finishAll() {
      pending.forEach(
          (uid, p) -> {
            if (!failed.contains(uid) && succeeded.add(uid)) {
              if (p.present()) present.add(p.id());
              else present.remove(p.id());
            }
          });
    }
  }
}
