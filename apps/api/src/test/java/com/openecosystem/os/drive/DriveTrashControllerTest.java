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
        values (?, 'wrk_dev_placeholder', 'invoice_extraction', ?, 'synthetic-secret-title',
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
            + "\",\"workspaceId\":\"wrk_dev_placeholder\",\"sourceType\":\"invoice_extraction\",\"sourceId\":\""
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
