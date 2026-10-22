package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    classes = {
      com.openecosystem.os.OpenEcosystemApiApplication.class,
      DriveFileControllerTest.DriveFileControllerTestConfiguration.class
    },
    properties =
        "spring.datasource.url=jdbc:h2:mem:drive_trash_http;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DriveTrashControllerTest {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired DriveFileControllerTest.FakeFileObjectStorage storage;
  @Autowired FileViewAuthorizationRepository authorization;

  @org.springframework.test.context.bean.override.mockito.MockitoBean(name = "driveClock")
  java.time.Clock clock;

  @org.junit.jupiter.api.BeforeEach
  void resetClock() {
    org.mockito.Mockito.when(clock.instant())
        .thenReturn(java.time.Instant.parse("2026-10-22T12:00:00Z"));
  }

  final HttpClient client = HttpClient.newHttpClient();
  final JsonMapper json = JsonMapper.builder().build();

  @Test
  void trashFileHidesDownloadAndMetadataButRetainsCiphertext() throws Exception {
    String file = upload(null);
    String key =
        jdbc.queryForObject(
            "select storage_key from drive_files where file_id=?", String.class, file);
    byte[] ciphertext = storage.objectBytes(key).clone();
    var result = post("/files/" + file + "/trash", null, null);
    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(json.readTree(result.body()).get("groupId").asString()).isNotBlank();
    assertThat(get("/files").body()).doesNotContain(file);
    assertThat(get("/folders/contents").body()).doesNotContain(file);
    assertThat(get("/files/" + file + "/download").statusCode()).isEqualTo(404);
    assertThat(get("/files/" + file + "/preview").statusCode()).isEqualTo(404);
    assertThat(storage.objectBytes(key)).isEqualTo(ciphertext);
    assertThat(authorization.findFacts("usr_dev_placeholder", "wrk_dev_placeholder", file))
        .isEmpty();
    assertThat(
            authorization.findAllowedFileIds(
                "usr_dev_placeholder", "wrk_dev_placeholder", java.util.List.of(file)))
        .isEmpty();
    assertThat(
            jdbc.queryForObject(
                "select attributes_json from audit_records where action='drive.file.trashed' and"
                    + " resource_id=?",
                String.class,
                file))
        .doesNotContain("synthetic", key);
  }

  @Test
  void trashFolderHidesWholeSubtreeAndZip() throws Exception {
    String root = folder(null), child = folder(root), file = upload(child);
    assertThat(post("/folders/" + root + "/trash", null, null).statusCode()).isEqualTo(200);
    assertThat(get("/folders/contents").body()).doesNotContain(root, child, file);
    for (String id : new String[] {root, child}) {
      assertThat(get("/folders/" + id).statusCode()).isEqualTo(404);
      assertThat(get("/folders/contents?folderId=" + id).statusCode()).isEqualTo(404);
      assertThat(get("/folders/" + id + "/download").statusCode()).isEqualTo(404);
      assertThat(
              post("/folders", "{\"name\":\"synthetic\",\"parentFolderId\":\"" + id + "\"}", null)
                  .statusCode())
          .isEqualTo(404);
    }
    assertThat(get("/files/" + file + "/download").statusCode()).isEqualTo(404);
    assertThat(
            jdbc.queryForObject(
                "select count(distinct deletion_group_id) from drive_folders where folder_id in"
                    + " (?,?)",
                Integer.class,
                root,
                child))
        .isEqualTo(1);
  }

  @Test
  void ancestorTombstoneAloneFailsClosed() throws Exception {
    String root = folder(null), child = folder(root), file = upload(child);
    post("/folders/" + root + "/trash", null, null);
    jdbc.update("update drive_files set deletion_group_id=null where file_id=?", file);
    jdbc.update("update drive_folders set deletion_group_id=null where folder_id=?", child);
    assertThat(get("/files/" + file + "/download").statusCode()).isEqualTo(404);
    assertThat(get("/folders/" + child).statusCode()).isEqualTo(404);
    assertThat(get("/folders/contents?folderId=" + child).statusCode()).isEqualTo(404);
    assertThat(
            authorization.findAllowedFileIds(
                "usr_dev_placeholder", "wrk_dev_placeholder", java.util.List.of(file)))
        .isEmpty();
  }

  @Test
  void unauthorizedDescendantRejectsWholeTrashWithoutPartialWrites() throws Exception {
    String root = folder(null), child = folder(root), file = upload(child);
    seedViewer();
    jdbc.update("update drive_files set owner_id='usr_trash_viewer' where file_id=?", file);
    assertThat(post("/folders/" + root + "/trash", null, null).statusCode()).isEqualTo(404);
    assertThat(
            jdbc.queryForObject(
                "select deletion_group_id from drive_folders where folder_id=?",
                String.class,
                root))
        .isNull();
    assertThat(get("/folders/" + root).statusCode()).isEqualTo(200);
  }

  @Test
  void viewerCannotTrashOwnedByAnother() throws Exception {
    seedViewer();
    String file = upload(null), folder = folder(null);
    for (String path : new String[] {"/files/" + file + "/trash", "/folders/" + folder + "/trash"})
      assertThat(post(path, null, "usr_trash_viewer").statusCode()).isEqualTo(404);
    assertThat(get("/files/" + file + "/download").statusCode()).isEqualTo(200);
  }

  @Test
  void concurrentTrashIsIdempotent() throws Exception {
    String file = upload(null);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      java.util.concurrent.Callable<HttpResponse<String>> work =
          () -> {
            gate.await();
            return post("/files/" + file + "/trash", null, null);
          };
      var a = pool.submit(work);
      var b = pool.submit(work);
      gate.countDown();
      var first = a.get(10, TimeUnit.SECONDS);
      var second = b.get(10, TimeUnit.SECONDS);
      assertThat(first.statusCode()).isEqualTo(200);
      assertThat(second.statusCode()).isEqualTo(200);
      assertThat(json.readTree(first.body()).get("groupId").asString())
          .isEqualTo(json.readTree(second.body()).get("groupId").asString());
      assertThat(
              jdbc.queryForObject(
                  "select count(*) from drive_trash_groups where root_item_id=?",
                  Integer.class,
                  file))
          .isEqualTo(1);
    }
  }

  @Test
  void oldSearchHitFilteredAfterTrash() throws Exception {
    String file = upload(null);
    String document = "synthetic-search-" + UUID.randomUUID();
    String metadata = "{\"fileId\":\"" + file + "\"}";
    jdbc.update(
        """
        insert into search_documents (search_document_id, workspace_id, source_type, source_id,
          title, summary, content, resource_href, correlation_id, status, attempt_count, max_attempts,
          metadata_json, created_at, updated_at)
        values (?, 'wrk_dev_placeholder', 'ocr_result', ?, 'synthetic-secret-title',
          'synthetic-secret-summary', 'synthetic-secret-term', '/app/drive', 'synthetic-correlation',
          'indexed', 0, 3, ?, current_timestamp, current_timestamp)
        """,
        document,
        document,
        metadata);
    var server =
        com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    String hit =
        "{\"hits\":[{\"id\":\""
            + document
            + "\",\"workspaceId\":\"wrk_dev_placeholder\",\"sourceType\":\"ocr_result\",\"sourceId\":\""
            + document
            + "\",\"title\":\"synthetic-secret-title\",\"summary\":\"synthetic-secret-summary\",\"resourceHref\":\"/app/drive\",\"correlationId\":\"synthetic-correlation\",\"metadata\":"
            + metadata
            + ",\"createdAt\":\"2026-10-22T12:00:00Z\"}]}";
    server.createContext(
        "/",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          byte[] body = hit.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    var sourceAuthorization =
        new com.openecosystem.os.common.security.DefaultResourceAuthorizationService(authorization);
    var search =
        new com.openecosystem.os.search.SearchService(
            () ->
                new com.openecosystem.os.common.security.AuthenticatedPrincipal(
                    "usr_dev_placeholder",
                    "wrk_dev_placeholder",
                    java.util.Set.of("INSTANCE_OWNER"),
                    true),
            new com.openecosystem.os.search.JdbcSearchDocumentRepository(jdbc, mapper),
            new com.openecosystem.os.search.MeilisearchSearchClient(
                new com.openecosystem.os.search.SearchProperties(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "synthetic-key",
                    "synthetic-index",
                    3),
                mapper),
            sourceAuthorization,
            mapper);
    try {
      assertThat(search.search("synthetic-secret-term").results())
          .extracting(com.openecosystem.os.search.SearchResultResponse::id)
          .contains(document);
      assertThat(post("/files/" + file + "/trash", null, null).statusCode()).isEqualTo(200);
      assertThat(search.search("synthetic-secret-term").results()).isEmpty();
    } finally {
      server.stop(0);
    }
    assertThat(search.search("synthetic-secret-term").results()).isEmpty();
  }

  @Test
  void folderTrashPreservesIndependentChildGroupAndDeadline() throws Exception {
    String root = folder(null), child = folder(root), file = upload(child);
    var earlier = post("/files/" + file + "/trash", null, null);
    String group = json.readTree(earlier.body()).get("groupId").asString();
    jdbc.update(
        "update drive_trash_groups set trashed_at = trashed_at - interval '120' hour, purge_after ="
            + " purge_after - interval '120' hour where group_id=?",
        group);
    var deadline =
        jdbc.queryForObject(
            "select purge_after from drive_trash_groups where group_id=?",
            java.sql.Timestamp.class,
            group);
    var parent = post("/folders/" + root + "/trash", null, null);
    assertThat(parent.statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select deletion_group_id from drive_files where file_id=?", String.class, file))
        .isEqualTo(group);
    assertThat(
            jdbc.queryForObject(
                "select purge_after from drive_trash_groups where group_id=?",
                java.sql.Timestamp.class,
                group))
        .isEqualTo(deadline);
    assertThat(json.readTree(parent.body()).get("groupId").asString()).isNotEqualTo(group);
    assertThat(post("/folders/" + child + "/trash", null, null).statusCode()).isEqualTo(404);
  }

  @Test
  void restoreBeforeDeadlinePreservesBytesAndIds() throws Exception {
    String parent = folder(null), root = folder(parent), child = folder(root), file = upload(child);
    byte[] bytes =
        client
            .send(
                request("/files/" + file + "/download").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray())
            .body();
    String group = group(post("/folders/" + root + "/trash", null, null));
    var listing = get("/trash");
    assertThat(listing.statusCode()).isEqualTo(200);
    assertThat(listing.body()).contains(group, root, "2026-11-21T12:00:00Z");
    assertThat(post("/trash/" + group + "/restore", null, null).statusCode()).isEqualTo(200);
    assertThat(get("/folders/" + root).statusCode()).isEqualTo(200);
    assertThat(get("/folders/contents?folderId=" + child).body()).contains(file);
    assertThat(
            client
                .send(
                    request("/files/" + file + "/download").GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray())
                .body())
        .isEqualTo(bytes);
    assertThat(
            jdbc.queryForObject(
                "select parent_folder_id from drive_folders where folder_id=?", String.class, root))
        .isEqualTo(parent);
    assertThat(post("/trash/" + group + "/restore", null, null).statusCode()).isEqualTo(200);
    assertThat(get("/trash").body()).doesNotContain(group);
  }

  @Test
  void restoreRejectsAtExactDeadline() throws Exception {
    String file = upload(null);
    String group = group(post("/files/" + file + "/trash", null, null));
    org.mockito.Mockito.when(clock.instant())
        .thenReturn(java.time.Instant.parse("2026-11-21T12:00:00Z"));
    assertThat(post("/trash/" + group + "/restore", null, null).statusCode()).isEqualTo(409);
    assertThat(get("/files/" + file + "/download").statusCode()).isEqualTo(404);
    assertThat(get("/trash").body()).contains("\"canRestore\":false");
  }

  @Test
  void restoreMissingParentRequiresExplicitDestination() throws Exception {
    String parent = folder(null), file = upload(parent);
    String group = group(post("/files/" + file + "/trash", null, null));
    post("/folders/" + parent + "/trash", null, null);
    assertThat(post("/trash/" + group + "/restore", null, null).statusCode()).isEqualTo(409);
    assertThat(post("/trash/" + group + "/restore", "{}", null).statusCode()).isEqualTo(409);
    assertThat(
            post("/trash/" + group + "/restore", "{\"destinationFolderId\":null}", null)
                .statusCode())
        .isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select folder_id from drive_files where file_id=?", String.class, file))
        .isNull();
    assertThat(get("/files/" + file + "/download").statusCode()).isEqualTo(200);
  }

  @Test
  void restoreFolderIntoChosenDestinationAndPreserveIndependentChild() throws Exception {
    String root = folder(null), child = folder(root), file = upload(child), target = folder(null);
    String independent = group(post("/files/" + file + "/trash", null, null));
    String group = group(post("/folders/" + root + "/trash", null, null));
    assertThat(
            post(
                    "/trash/" + group + "/restore",
                    "{\"destinationFolderId\":\"" + target + "\"}",
                    null)
                .statusCode())
        .isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select parent_folder_id from drive_folders where folder_id=?", String.class, root))
        .isEqualTo(target);
    assertThat(get("/folders/" + child).statusCode()).isEqualTo(200);
    assertThat(get("/files/" + file + "/download").statusCode()).isEqualTo(404);
    assertThat(get("/trash").body()).contains(independent).doesNotContain(group);
  }

  @Test
  void restoreDuplicateSiblingConflictsNotOverwritten() throws Exception {
    String file = upload(null);
    String group = group(post("/files/" + file + "/trash", null, null));
    String replacement = upload(null);
    jdbc.update(
        "update drive_files set encrypted_name=(select encrypted_name from drive_files where"
            + " file_id=?), name_iv=(select name_iv from drive_files where file_id=?) where"
            + " file_id=?",
        file,
        file,
        replacement);
    assertThat(post("/trash/" + group + "/restore", null, null).statusCode()).isEqualTo(409);
    assertThat(get("/files/" + replacement + "/download").statusCode()).isEqualTo(200);
    assertThat(get("/files/" + file + "/download").statusCode()).isEqualTo(404);
  }

  @Test
  void unauthorizedTrashGroupNotEnumeratedOrRestored() throws Exception {
    seedViewer();
    String file = upload(null);
    String group = group(post("/files/" + file + "/trash", null, null));
    var listing =
        client.send(
            request("/trash").header("X-Actor-Id", "usr_trash_viewer").GET().build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(listing.statusCode()).isEqualTo(200);
    assertThat(listing.body()).doesNotContain(group, file, "synthetic");
    assertThat(post("/trash/" + group + "/restore", null, "usr_trash_viewer").statusCode())
        .isEqualTo(404);
    assertThat(post("/trash/nonexistent/restore", null, null).statusCode()).isEqualTo(404);
  }

  @Test
  void restoreOneSecondBeforeDeadlineStillWorks() throws Exception {
    String file = upload(null);
    String group = group(post("/files/" + file + "/trash", null, null));
    org.mockito.Mockito.when(clock.instant())
        .thenReturn(java.time.Instant.parse("2026-11-21T11:59:59Z"));
    assertThat(post("/trash/" + group + "/restore", "{}", null).statusCode()).isEqualTo(200);
    assertThat(get("/files/" + file + "/download").statusCode()).isEqualTo(200);
  }

  @Test
  void invalidRestoreDestinationNeverMutatesGroup() throws Exception {
    String file = upload(null);
    String group = group(post("/files/" + file + "/trash", null, null));
    for (String destination : new String[] {"true", "123", "\"\"", "[]", "{}"})
      assertThat(
              post(
                      "/trash/" + group + "/restore",
                      "{\"destinationFolderId\":" + destination + "}",
                      null)
                  .statusCode())
          .isEqualTo(400);
    assertThat(
            jdbc.queryForObject(
                "select state from drive_trash_groups where group_id=?", String.class, group))
        .isEqualTo("TRASHED");
    assertThat(get("/files/" + file + "/download").statusCode()).isEqualTo(404);
  }

  @Autowired com.openecosystem.os.media.FileUploadedEventParser uploadedParser;
  @Autowired com.openecosystem.os.media.FileUploadedOcrJobService uploadedConsumer;

  @Test
  void restoreAutomaticallyCreatesJobAfterUploadWasConsumedInTrash() throws Exception {
    String file = uploadPdf();
    String group = group(post("/files/" + file + "/trash", null, null));
    String envelope =
        jdbc.queryForObject(
            "select envelope_json from event_outbox where idempotency_key=?",
            String.class,
            "drive:" + file + ":uploaded:v1");
    uploadedConsumer.queueOcrJobIfEligible(uploadedParser.parse(envelope));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ocr_jobs where file_id=?", Integer.class, file))
        .isZero();
    assertThat(post("/trash/" + group + "/restore", null, null).statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ocr_jobs where file_id=?", Integer.class, file))
        .isEqualTo(1);
    assertThat(post("/trash/" + group + "/restore", null, null).statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from ocr_jobs where file_id=?", Integer.class, file))
        .isEqualTo(1);
  }

  @Test
  void restoreDurablySchedulesQueuedOcrWithoutManualRedelivery() throws Exception {
    String file = uploadPdf();
    String envelope =
        jdbc.queryForObject(
            "select envelope_json from event_outbox where idempotency_key=?",
            String.class,
            "drive:" + file + ":uploaded:v1");
    uploadedConsumer.queueOcrJobIfEligible(uploadedParser.parse(envelope));
    jdbc.update(
        "update event_outbox set published_at=current_timestamp where event_type='OcrRequested'");
    String group = group(post("/files/" + file + "/trash", null, null));
    assertThat(post("/trash/" + group + "/restore", null, null).statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from event_outbox where event_type='OcrRequested' and published_at"
                    + " is null and idempotency_key like ?",
                Integer.class,
                "%" + group + "%"))
        .isEqualTo(1);
    assertThat(post("/trash/" + group + "/restore", null, null).statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from event_outbox where event_type='OcrRequested' and published_at"
                    + " is null and idempotency_key like ?",
                Integer.class,
                "%" + group + "%"))
        .isEqualTo(1);
  }

  @Test
  void restoreRequeuesInterruptedFinalOcrAttemptExactlyOnce() throws Exception {
    String file = uploadPdf();
    String envelope =
        jdbc.queryForObject(
            "select envelope_json from event_outbox where idempotency_key=?",
            String.class,
            "drive:" + file + ":uploaded:v1");
    uploadedConsumer.queueOcrJobIfEligible(uploadedParser.parse(envelope));
    jdbc.update(
        "update ocr_jobs set status='processing', attempt_count=max_attempts,"
            + " processing_started_at=? where file_id=?",
        java.sql.Timestamp.from(java.time.Instant.parse("2026-10-22T10:00:00Z")),
        file);
    int originalBudget =
        jdbc.queryForObject(
            "select max_attempts from ocr_jobs where file_id=?", Integer.class, file);
    String group = group(post("/files/" + file + "/trash", null, null));
    assertThat(post("/trash/" + group + "/restore", null, null).statusCode()).isEqualTo(200);
    var job = jdbc.queryForMap("select * from ocr_jobs where file_id=?", file);
    assertThat(job.get("status")).isEqualTo("queued");
    assertThat(job.get("processing_started_at")).isNull();
    assertThat(((Number) job.get("attempt_count")).intValue())
        .isLessThan(((Number) job.get("max_attempts")).intValue());
    assertThat(post("/trash/" + group + "/restore", null, null).statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select max_attempts from ocr_jobs where file_id=?", Integer.class, file))
        .isEqualTo(originalBudget + 1);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from event_outbox where event_type='OcrRequested'"
                    + " and idempotency_key like ?",
                Integer.class,
                "%" + group + "%"))
        .isEqualTo(1);
    String recovery =
        jdbc.queryForObject(
            "select payload_json from event_outbox"
                + " where event_type='OcrRequested' and idempotency_key like ?",
            String.class,
            "%" + group + "%");
    assertThat(json.readTree(recovery).get("maxAttempts").asInt()).isEqualTo(originalBudget + 1);
  }

  @Autowired DriveSearchLifecycle searchLifecycle;

  @Test
  void lifecycleRevisionsAndCleanupProofFollowTrashRestoreTransitions() throws Exception {
    String file = upload(null);
    String firstGroup = group(post("/files/" + file + "/trash", null, null));
    assertThat(
            jdbc.queryForObject(
                "select revision from drive_search_lifecycle where file_id=?", Long.class, file))
        .isEqualTo(1L);
    assertThat(searchLifecycle.cleanupComplete("wrk_dev_placeholder", file, 1)).isFalse();
    assertThat(post("/trash/" + firstGroup + "/restore", null, null).statusCode()).isEqualTo(200);
    assertThat(post("/trash/" + firstGroup + "/restore", null, null).statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select revision from drive_search_lifecycle where file_id=?", Long.class, file))
        .isEqualTo(2L);
    jdbc.update(
        "update drive_search_lifecycle set reconciled_revision=revision where file_id=?", file);
    assertThat(searchLifecycle.cleanupComplete("wrk_dev_placeholder", file, 2)).isFalse();
    post("/files/" + file + "/trash", null, null);
    assertThat(searchLifecycle.cleanupComplete("wrk_dev_placeholder", file, 3)).isFalse();
    jdbc.update(
        "update drive_search_lifecycle set reconciled_revision=revision where file_id=?", file);
    assertThat(searchLifecycle.cleanupComplete("wrk_dev_placeholder", file, 3)).isTrue();
    assertThat(searchLifecycle.cleanupComplete("wrk_dev_placeholder", file, 1)).isFalse();
    assertThat(
            jdbc.queryForList(
                "select payload_json from event_outbox where event_type='DriveFileLifecycleChanged'"
                    + " and idempotency_key like ?",
                String.class,
                "drive:" + file + ":lifecycle:%"))
        .hasSize(3)
        .allSatisfy(
            payload ->
                assertThat(payload)
                    .doesNotContain("synthetic-", "encryptedName", "storageKey", "ocrText"));
  }

  @Autowired DriveTrashPurgeService purge;

  @Test
  void permanentDeleteRejectsOtherActorsAndReportsRealCleanupState() throws Exception {
    seedViewer();
    String file = upload(null);
    String group = group(post("/files/" + file + "/trash", null, null));
    assertThat(post("/trash/" + group + "/purge", null, "usr_trash_viewer").statusCode())
        .isEqualTo(404);
    var pending = post("/trash/" + group + "/purge", null, null);
    assertThat(pending.statusCode()).isEqualTo(200);
    assertThat(json.readTree(pending.body()).get("state").asString()).isEqualTo("PURGING");
    assertThat(post("/trash/" + group + "/restore", null, null).statusCode()).isEqualTo(409);
    jdbc.update(
        "update drive_search_lifecycle set reconciled_revision=revision where file_id=?", file);
    org.mockito.Mockito.when(clock.instant())
        .thenReturn(java.time.Instant.parse("2026-10-22T13:00:00Z"));
    assertThat(purge.runCleanup(clock.instant(), 25)).isPositive();
    var completed = post("/trash/" + group + "/purge", null, null);
    assertThat(json.readTree(completed.body()).get("state").asString()).isEqualTo("PURGED");
    assertThat(get("/trash").body()).doesNotContain(group);
    assertThat(get("/files/" + file + "/download").statusCode()).isEqualTo(404);
  }

  @Test
  void purgingFolderPreservesIndependentTrashAndRequiresExplicitRootRestore() throws Exception {
    String parent = folder(null),
        child = folder(parent),
        independent = upload(child),
        member = upload(child);
    String independentGroup = group(post("/files/" + independent + "/trash", null, null));
    String parentGroup = group(post("/folders/" + parent + "/trash", null, null));
    jdbc.update(
        "update drive_search_lifecycle set reconciled_revision=revision where file_id=?", member);
    var result = post("/trash/" + parentGroup + "/purge", null, null);
    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(json.readTree(result.body()).get("state").asString()).isEqualTo("PURGED");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from drive_files where file_id=?", Integer.class, independent))
        .isEqualTo(1);
    assertThat(post("/trash/" + independentGroup + "/restore", null, null).statusCode())
        .isEqualTo(409);
    assertThat(
            post("/trash/" + independentGroup + "/restore", "{\"destinationFolderId\":null}", null)
                .statusCode())
        .isEqualTo(200);
    assertThat(get("/files/" + independent + "/download").statusCode()).isEqualTo(200);
    assertThat(get("/files/" + member + "/download").statusCode()).isEqualTo(404);
  }

  String uploadPdf() throws Exception {
    var body =
        HttpRequest.BodyPublishers.concat(
            HttpRequest.BodyPublishers.ofString(
                "--synthetic-boundary\r\n"
                    + "Content-Disposition: form-data; name=\"file\";"
                    + " filename=\"synthetic-recovery-"
                    + UUID.randomUUID()
                    + ".pdf\"\r\nContent-Type: application/pdf\r\n\r\n"),
            HttpRequest.BodyPublishers.ofByteArray(SyntheticDriveFiles.pdf()),
            HttpRequest.BodyPublishers.ofString("\r\n--synthetic-boundary--\r\n"));
    var response =
        client.send(
            request("/files")
                .header("Content-Type", "multipart/form-data; boundary=synthetic-boundary")
                .POST(body)
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(201);
    return json.readTree(response.body()).get("fileId").asString();
  }

  String group(HttpResponse<String> response) {
    assertThat(response.statusCode()).isEqualTo(200);
    return json.readTree(response.body()).get("groupId").asString();
  }

  String folder(String parent) throws Exception {
    var response =
        post(
            "/folders",
            json.writeValueAsString(
                java.util.Map.of(
                    "name",
                    "synthetic-" + UUID.randomUUID(),
                    "parentFolderId",
                    parent == null ? "" : parent)),
            null);
    assertThat(response.statusCode()).isEqualTo(201);
    return json.readTree(response.body()).get("folderId").asString();
  }

  String upload(String folder) throws Exception {
    String multipart =
        "--synthetic-boundary\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"synthetic-"
            + UUID.randomUUID()
            + ".txt\"\r\nContent-Type: text/plain\r\n\r\nsynthetic text\r\n"
            + (folder == null
                ? ""
                : "--synthetic-boundary\r\n"
                    + "Content-Disposition: form-data; name=\"folderId\"\r\n\r\n"
                    + folder
                    + "\r\n")
            + "--synthetic-boundary--\r\n";
    var response =
        client.send(
            request("/files")
                .header("Content-Type", "multipart/form-data; boundary=synthetic-boundary")
                .POST(HttpRequest.BodyPublishers.ofString(multipart, StandardCharsets.UTF_8))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(201);
    return json.readTree(response.body()).get("fileId").asString();
  }

  HttpResponse<String> post(String path, String body, String actor) throws Exception {
    var request =
        request(path)
            .header("Content-Type", "application/json")
            .POST(
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
    if (actor != null) request.header("X-Actor-Id", actor);
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> get(String path) throws Exception {
    return client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/drive" + path));
  }

  void seedViewer() {
    if (jdbc.queryForObject(
            "select count(*) from identity_users where user_id='usr_trash_viewer'", Integer.class)
        > 0) return;
    jdbc.update(
        "insert into identity_users"
            + " (user_id,display_name,email,avatar_initials,status,is_seeded,created_at,updated_at)"
            + " values"
            + " ('usr_trash_viewer','Synthetic','trash@example.test','ST','active',false,current_timestamp,current_timestamp)");
    jdbc.update(
        "insert into workspace_memberships"
            + " (workspace_id,user_id,role,is_default,created_at,updated_at) values"
            + " ('wrk_dev_placeholder','usr_trash_viewer','VIEWER',false,current_timestamp,current_timestamp)");
  }
}
