package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.OpenEcosystemApiApplication;
import com.openecosystem.os.common.security.PlaceholderAuthenticationContext;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    classes = {
      OpenEcosystemApiApplication.class,
      DriveFileControllerTest.DriveFileControllerTestConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DriveFolderControllerTest {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired DriveFileControllerTest.FakeFileObjectStorage storage;
  @Autowired com.openecosystem.os.drive.crypto.FileEncryptionService encryption;
  final HttpClient client = HttpClient.newHttpClient();
  final JsonMapper json = JsonMapper.builder().build();

  @BeforeEach
  void clean() {
    jdbc.update("delete from event_outbox");
    jdbc.update("delete from audit_records");
    jdbc.update("delete from drive_files");
    jdbc.update("delete from drive_folders");
    storage.clear();
  }

  @Test
  void createsProjectsInvoices2026AndListsImmediateChildren() throws Exception {
    String projects = create("Projects", null),
        invoices = create("Invoices", projects),
        year = create("2026", invoices);
    assertThat(get("/contents", null).body()).contains("Projects").doesNotContain("Invoices");
    assertThat(get("/contents?folderId=" + projects, null).body())
        .contains("Invoices")
        .doesNotContain("\"name\":\"2026\"");
    assertThat(get("/" + year, null).statusCode()).isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select encrypted_name from drive_folders where folder_id=?", String.class, year))
        .doesNotContain("2026");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_records where action='drive.folder.created'",
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForList("select attributes_json from audit_records", String.class).toString())
        .doesNotContain("Projects", "Invoices", "2026");
  }

  @Test
  void uploadIntoChildNotRoot() throws Exception {
    String folder = create("Projects", null);
    var response = upload("synthetic.txt", "text/plain", "synthetic text".getBytes(), folder);
    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.body()).contains("\"folderId\":\"" + folder + "\"");
    assertThat(get("/contents", null).body()).doesNotContain("synthetic.txt");
    assertThat(get("/contents?folderId=" + folder, null).body()).contains("synthetic.txt");
  }

  @Test
  void conflictingFileAndFolderSiblingNamesReject() throws Exception {
    create("CAFÉ", null);
    assertThat(post("cafe\u0301", null).statusCode()).isEqualTo(409);
    assertThat(upload("café", "text/plain", "synthetic".getBytes(), null).statusCode())
        .isEqualTo(409);
    assertThat(storage.objectCount()).isZero();
    assertThat(upload("REPORT", "text/plain", "synthetic".getBytes(), null).statusCode())
        .isEqualTo(201);
    assertThat(post("report", null).statusCode()).isEqualTo(409);
    String child = create("child", null);
    assertThat(upload("REPORT", "text/plain", "synthetic".getBytes(), child).statusCode())
        .isEqualTo(201);
  }

  @Test
  void twoConcurrentCreatesCannotDuplicateName() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var a =
          executor.submit(
              () -> {
                gate.await();
                return post("Concurrent", null).statusCode();
              });
      var b =
          executor.submit(
              () -> {
                gate.await();
                return post("CONCURRENT", null).statusCode();
              });
      gate.countDown();
      assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
  }

  @Test
  void concurrentUploadAndFolderCreationCannotDuplicateName() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var a =
          executor.submit(
              () -> {
                gate.await();
                return post("Synthetic", null).statusCode();
              });
      var b =
          executor.submit(
              () -> {
                gate.await();
                return upload("SYNTHETIC", "text/plain", "synthetic".getBytes(), null).statusCode();
              });
      gate.countDown();
      assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
  }

  @Test
  void foreignFolderReadForbiddenWithoutNameLeak() throws Exception {
    String folder = create("Secret synthetic folder", null);
    String actor = "usr_task2_viewer";
    jdbc.update(
        "insert into identity_users"
            + " (user_id,display_name,email,avatar_initials,status,is_seeded,created_at,updated_at)"
            + " values (?,?,?,'TS','active',false,current_timestamp,current_timestamp)",
        actor,
        actor,
        actor + "@example.test");
    jdbc.update(
        "insert into workspace_memberships"
            + " (workspace_id,user_id,role,is_default,created_at,updated_at) values"
            + " (?,?,'VIEWER',false,current_timestamp,current_timestamp)",
        PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID,
        actor);
    for (String path : new String[] {"/" + folder, "/contents?folderId=" + folder, "/missing"}) {
      var r = get(path, actor);
      assertThat(r.statusCode()).isEqualTo(404);
      assertThat(r.body()).doesNotContain("Secret synthetic folder");
    }
    var body = "{\"name\":\"child\",\"parentFolderId\":\"" + folder + "\"}";
    var denied =
        client.send(
            request("")
                .header("X-Actor-Id", actor)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(denied.statusCode()).isEqualTo(404);
    assertThat(
            upload("denied.txt", "text/plain", "synthetic".getBytes(), folder, actor).statusCode())
        .isEqualTo(404);
    assertThat(get("/contents", actor).body()).doesNotContain("Secret synthetic folder");
    jdbc.update(
        "insert into workspaces (workspace_id,name,slug,status,is_seeded,created_at,updated_at)"
            + " values ('wrk_task2_foreign','Synthetic"
            + " workspace','task2-foreign','active',false,current_timestamp,current_timestamp)");
    jdbc.update(
        "insert into workspace_memberships"
            + " (workspace_id,user_id,role,is_default,created_at,updated_at) values"
            + " ('wrk_task2_foreign',?,'WORKSPACE_ADMIN',false,current_timestamp,current_timestamp)",
        actor);
    var foreign =
        client.send(
            request("/" + folder)
                .header("X-Actor-Id", actor)
                .header("X-Workspace-Id", "wrk_task2_foreign")
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(foreign.statusCode()).isEqualTo(404);
    assertThat(foreign.body()).doesNotContain("Secret synthetic folder");
    assertThat(upload("no.txt", "text/plain", "synthetic".getBytes(), "missing").statusCode())
        .isEqualTo(404);
    assertThat(storage.objectCount()).isZero();
  }

  @Test
  void folderListChecksFileViewBeforeDecryptingNames() throws Exception {
    String folder = create("Synthetic parent", null);
    assertThat(upload("allowed.txt", "text/plain", "synthetic".getBytes(), folder).statusCode())
        .isEqualTo(201);
    assertThat(upload("private.txt", "text/plain", "synthetic".getBytes(), folder).statusCode())
        .isEqualTo(201);
    String denied =
        jdbc.queryForObject(
            "select file_id from drive_files order by created_at desc limit 1", String.class);
    jdbc.update(
        "update drive_files set owner_id='usr_other_synthetic', encrypted_name='invalid-ciphertext'"
            + " where file_id=?",
        denied);
    var response = get("/contents?folderId=" + folder, null);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body())
        .contains("allowed.txt")
        .doesNotContain("private.txt", "invalid-ciphertext");
  }

  @Test
  void concurrentUploadsPersistOnlyOneObjectAndEvent() throws Exception {
    try (var executor = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var first =
          executor.submit(
              () -> {
                gate.await();
                return upload("Synthetic.txt", "text/plain", "synthetic".getBytes(), null)
                    .statusCode();
              });
      var second =
          executor.submit(
              () -> {
                gate.await();
                return upload("SYNTHETIC.txt", "text/plain", "synthetic".getBytes(), null)
                    .statusCode();
              });
      gate.countDown();
      assertThat(
              java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    }
    assertThat(storage.objectCount()).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from drive_files", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from audit_records", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("select count(*) from event_outbox", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void legacyLongNamesDoNotBlockUnrelatedDirectoryCreation() throws Exception {
    assertThat(upload("legacy.txt", "text/plain", "synthetic".getBytes(), null).statusCode())
        .isEqualTo(201);
    var encrypted = encryption.encryptText("synthetic-legacy-" + "x".repeat(300));
    jdbc.update(
        "update drive_files set encrypted_name=?, name_iv=?",
        encrypted.ciphertextBase64(),
        encrypted.ivBase64());
    assertThat(post("New folder", null).statusCode()).isEqualTo(201);
    assertThat(get("/contents", null).body()).contains("synthetic-legacy-", "New folder");
  }

  @Test
  void rejectsCorruptAllowedFormatsBeforeStorage() throws Exception {
    for (String mime : SyntheticDriveFiles.TYPES) {
      assertThat(upload("synthetic", "" + mime, new byte[] {1, 2, 3}, null).statusCode())
          .as(mime)
          .isEqualTo(400);
    }
    assertThat(
            upload("synthetic", "application/pdf", "synthetic text".getBytes(), null).statusCode())
        .isEqualTo(400);
    assertThat(storage.objectCount()).isZero();
    assertThat(jdbc.queryForObject("select count(*) from drive_files", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("select count(*) from audit_records", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("select count(*) from event_outbox", Integer.class)).isZero();
  }

  @Test
  void validSupportedFormatsStillUpload() throws Exception {
    int i = 0;
    for (String mime : SyntheticDriveFiles.TYPES)
      assertThat(
              upload("synthetic-" + i++, mime, SyntheticDriveFiles.bytes(mime), null).statusCode())
          .as(mime)
          .isEqualTo(201);
  }

  @Test
  void renamePreservesObjectKeyFileIdAndOcrLink() throws Exception {
    String file = uploadedFile("synthetic-original.txt", null);
    var before = jdbc.queryForMap("select * from drive_files where file_id=?", file);
    jdbc.update(
        "insert into ocr_jobs (job_id,file_id,workspace_id,actor_id,source_event_id,correlation_id,"
            + "content_type,storage_key,status,attempt_count,max_attempts,queued_at,created_at,updated_at)"
            + " values"
            + " ('job_task4_synthetic',?,?,?,'evt_task4','cor_task4','text/plain',?,'queued',0,3,"
            + "current_timestamp,current_timestamp,current_timestamp)",
        file,
        PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID,
        PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
        before.get("storage_key"));
    var job = jdbc.queryForMap("select * from ocr_jobs where file_id=?", file);
    int events = jdbc.queryForObject("select count(*) from event_outbox", Integer.class);
    assertThat(patch("files", file, "name", "name", "Synthetic-renamed.txt", null).statusCode())
        .isEqualTo(200);
    String destination = create("Destination", null);
    assertThat(
            patch("files", file, "location", "destinationFolderId", destination, null).statusCode())
        .isEqualTo(200);
    var after = jdbc.queryForMap("select * from drive_files where file_id=?", file);
    for (String column : before.keySet())
      if (!java.util.Set.of("encrypted_name", "name_iv", "updated_at", "folder_id")
          .contains(column)) assertThat(after.get(column)).as(column).isEqualTo(before.get(column));
    assertThat(after.get("encrypted_name")).isNotEqualTo(before.get("encrypted_name"));
    assertThat(after.get("folder_id")).isEqualTo(destination);
    assertThat(jdbc.queryForMap("select * from ocr_jobs where file_id=?", file)).isEqualTo(job);
    assertThat(jdbc.queryForObject("select count(*) from event_outbox", Integer.class))
        .isEqualTo(events);
    assertThat(storage.objectCount()).isEqualTo(1);
    var download =
        client.send(
            HttpRequest.newBuilder(
                    URI.create(
                        "http://localhost:" + port + "/api/drive/files/" + file + "/download"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(download.statusCode()).isEqualTo(200);
    assertThat(download.body()).isEqualTo("synthetic text");
    assertThat(patch("files", file, "location", "destinationFolderId", null, null).statusCode())
        .isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select folder_id from drive_files where file_id=?", String.class, file))
        .isNull();
    assertThat(
            jdbc.queryForList("select attributes_json from audit_records", String.class).toString())
        .doesNotContain(
            "synthetic-original",
            "Synthetic-renamed",
            "Destination",
            (String) before.get("storage_key"));
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_records where action in"
                    + " ('drive.file.renamed','drive.file.moved')",
                Integer.class))
        .isEqualTo(3);
    jdbc.update("delete from ocr_jobs where job_id='job_task4_synthetic'");
  }

  @Test
  void moveFolderPreservesSubtreeReferences() throws Exception {
    String source = create("Source", null),
        child = create("Child", source),
        leaf = create("Leaf", child);
    String file = uploadedFile("synthetic.txt", leaf), target = create("Target", null);
    var childBefore = jdbc.queryForMap("select * from drive_folders where folder_id=?", child);
    var leafBefore = jdbc.queryForMap("select * from drive_folders where folder_id=?", leaf);
    var fileBefore = jdbc.queryForMap("select * from drive_files where file_id=?", file);
    assertThat(patch("folders", source, "name", "name", "Renamed", null).statusCode())
        .isEqualTo(200);
    assertThat(
            patch("folders", source, "location", "destinationFolderId", target, null).statusCode())
        .isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select parent_folder_id from drive_folders where folder_id=?",
                String.class,
                source))
        .isEqualTo(target);
    assertThat(jdbc.queryForMap("select * from drive_folders where folder_id=?", child))
        .isEqualTo(childBefore);
    assertThat(jdbc.queryForMap("select * from drive_folders where folder_id=?", leaf))
        .isEqualTo(leafBefore);
    assertThat(jdbc.queryForMap("select * from drive_files where file_id=?", file))
        .isEqualTo(fileBefore);
    assertThat(patch("folders", source, "location", "destinationFolderId", null, null).statusCode())
        .isEqualTo(200);
    assertThat(get("/" + source, null).body()).contains("\"parentFolderId\":null", "Renamed");
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_records where action in"
                    + " ('drive.folder.renamed','drive.folder.moved')",
                Integer.class))
        .isEqualTo(3);
  }

  @Test
  void rejectCycleSelfOrDescendant() throws Exception {
    String source = create("Source", null),
        child = create("Child", source),
        leaf = create("Leaf", child);
    for (String destination : new String[] {source, child, leaf})
      assertThat(
              patch("folders", source, "location", "destinationFolderId", destination, null)
                  .statusCode())
          .isEqualTo(409);
    assertThat(get("/" + source, null).body()).contains("\"parentFolderId\":null");
    assertThat(get("/" + child, null).body()).contains(source);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_records where action='drive.folder.moved'",
                Integer.class))
        .isZero();
  }

  @Test
  void normalizedCrossKindConflictsRejectAndSelfIsExcluded() throws Exception {
    String folder = create("CAFÉ", null), file = uploadedFile("synthetic.txt", null);
    assertThat(patch("files", file, "name", "name", "cafe\u0301", null).statusCode())
        .isEqualTo(409);
    assertThat(patch("folders", folder, "name", "name", "SYNTHETIC.TXT", null).statusCode())
        .isEqualTo(409);
    assertThat(patch("files", file, "name", "name", "SYNTHETIC.TXT", null).statusCode())
        .isEqualTo(200);
    assertThat(patch("folders", folder, "name", "name", "cafe\u0301", null).statusCode())
        .isEqualTo(200);
    assertThat(patch("files", file, "location", "destinationFolderId", null, null).statusCode())
        .isEqualTo(200);
    assertThat(patch("folders", folder, "location", "destinationFolderId", null, null).statusCode())
        .isEqualTo(200);
    for (String invalid : new String[] {"", "..", "../secret", "bad\\name", "x".repeat(256)}) {
      assertThat(patch("files", file, "name", "name", invalid, null).statusCode()).isEqualTo(400);
      assertThat(patch("folders", folder, "name", "name", invalid, null).statusCode())
          .isEqualTo(400);
    }
    String target = create("Target", null);
    create("synthetic.txt", target);
    assertThat(patch("files", file, "location", "destinationFolderId", target, null).statusCode())
        .isEqualTo(409);
    uploadedFile("CAFÉ", target);
    assertThat(
            patch("folders", folder, "location", "destinationFolderId", target, null).statusCode())
        .isEqualTo(409);
  }

  @Test
  void rejectConcurrentCollision() throws Exception {
    String folder = create("Folder", null), file = uploadedFile("synthetic.txt", null);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var a =
          executor.submit(
              () -> {
                gate.await();
                return patch("files", file, "name", "name", "Shared", null).statusCode();
              });
      var b =
          executor.submit(
              () -> {
                gate.await();
                return patch("folders", folder, "name", "name", "SHARED", null).statusCode();
              });
      gate.countDown();
      assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
  }

  @Test
  void concurrentMovesCannotCreateCycle() throws Exception {
    String a = create("A", null), b = create("B", null);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var first =
          executor.submit(
              () -> {
                gate.await();
                return patch("folders", a, "location", "destinationFolderId", b, null).statusCode();
              });
      var second =
          executor.submit(
              () -> {
                gate.await();
                return patch("folders", b, "location", "destinationFolderId", a, null).statusCode();
              });
      gate.countDown();
      assertThat(
              java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
  }

  @Test
  void viewOnlyUserCannotRenameOrMoveOrUseForeignDestination() throws Exception {
    String file = uploadedFile("Synthetic secret.txt", null),
        folder = create("Synthetic secret folder", null);
    String actor = "usr_task4_viewer";
    jdbc.update(
        "insert into identity_users"
            + " (user_id,display_name,email,avatar_initials,status,is_seeded,created_at,updated_at)"
            + " values (?,?,?,'TS','active',false,current_timestamp,current_timestamp)",
        actor,
        actor,
        actor + "@example.test");
    jdbc.update(
        "insert into workspace_memberships"
            + " (workspace_id,user_id,role,is_default,created_at,updated_at) values"
            + " (?,?,'VIEWER',false,current_timestamp,current_timestamp)",
        PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID,
        actor);
    jdbc.update("update drive_files set visibility='workspace' where file_id=?", file);
    for (String kind : new String[] {"files", "folders"}) {
      String id = kind.equals("files") ? file : folder;
      for (String operation : new String[] {"name", "location"}) {
        var response =
            patch(
                kind,
                id,
                operation,
                operation.equals("name") ? "name" : "destinationFolderId",
                "New",
                actor);
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("Synthetic secret");
      }
      assertThat(patch(kind, "missing", "name", "name", "New", null).statusCode()).isEqualTo(404);
      assertThat(patch(kind, id, "location", "destinationFolderId", "missing", null).statusCode())
          .isEqualTo(404);
    }
    jdbc.update("update drive_folders set owner_id=? where folder_id=?", actor, folder);
    assertThat(patch("files", file, "location", "destinationFolderId", folder, null).statusCode())
        .isEqualTo(404);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from audit_records where action like '%.renamed' or action like"
                    + " '%.moved'",
                Integer.class))
        .isZero();
  }

  String uploadedFile(String name, String parent) throws Exception {
    var response =
        upload(name, "text/plain", "synthetic text".getBytes(StandardCharsets.UTF_8), parent);
    assertThat(response.statusCode()).isEqualTo(201);
    return json.readTree(response.body()).get("fileId").asString();
  }

  @Test
  void mutationPreflightAllowsConfiguredFrontendOriginOnly() throws Exception {
    for (String kind : new String[] {"files", "folders"}) {
      for (String action : new String[] {"name", "location"}) {
        var uri =
            URI.create("http://localhost:" + port + "/api/drive/" + kind + "/synthetic/" + action);
        var response =
            client.send(
                HttpRequest.newBuilder(uri)
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "PATCH")
                    .header(
                        "Access-Control-Request-Headers", "content-type,x-actor-id,x-workspace-id")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
            .contains("http://localhost:3000");
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods").orElseThrow())
            .contains("PATCH");
        var denied =
            client.send(
                HttpRequest.newBuilder(uri)
                    .header("Origin", "https://synthetic-untrusted.example.test")
                    .header("Access-Control-Request-Method", "PATCH")
                    .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                    .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(denied.statusCode()).isEqualTo(403);
      }
    }
  }

  @Test
  void legacyLongNamesCanMoveWithoutBeingRenamed() throws Exception {
    String file = uploadedFile("synthetic.txt", null), folder = create("Synthetic", null);
    var legacyName = encryption.encryptText("synthetic-legacy-" + "x".repeat(300));
    jdbc.update(
        "update drive_files set encrypted_name=?, name_iv=? where file_id=?",
        legacyName.ciphertextBase64(),
        legacyName.ivBase64(),
        file);
    jdbc.update(
        "update drive_folders set encrypted_name=?, name_iv=? where folder_id=?",
        legacyName.ciphertextBase64(),
        legacyName.ivBase64(),
        folder);
    String destination = create("Destination", null);
    assertThat(
            patch("files", file, "location", "destinationFolderId", destination, null).statusCode())
        .isEqualTo(200);
    assertThat(
            patch("folders", folder, "location", "destinationFolderId", destination, null)
                .statusCode())
        .isEqualTo(409);
    assertThat(patch("files", file, "location", "destinationFolderId", null, null).statusCode())
        .isEqualTo(409);
    String other = create("Other", null);
    assertThat(
            patch("folders", folder, "location", "destinationFolderId", other, null).statusCode())
        .isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "select encrypted_name from drive_files where file_id=?", String.class, file))
        .isEqualTo(legacyName.ciphertextBase64());
  }

  @Test
  void inactiveOwnerCannotRenameOrMoveFilesOrFolders() throws Exception {
    String file = uploadedFile("synthetic.txt", null), folder = create("Synthetic", null);
    String actor = PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID;
    jdbc.update("update identity_users set status='disabled' where user_id=?", actor);
    try {
      for (String kind : new String[] {"files", "folders"}) {
        String id = kind.equals("files") ? file : folder;
        assertThat(patch(kind, id, "name", "name", "New", null).statusCode()).isEqualTo(404);
        assertThat(patch(kind, id, "location", "destinationFolderId", null, null).statusCode())
            .isEqualTo(404);
      }
    } finally {
      jdbc.update("update identity_users set status='active' where user_id=?", actor);
    }
  }

  @Test
  void concurrentFileAndFolderMovesCannotCollide() throws Exception {
    String source = create("Source", null), destination = create("Destination", null);
    String file = uploadedFile("CAFÉ", source), folder = create("cafe\u0301", null);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var gate = new CountDownLatch(1);
      var a =
          executor.submit(
              () -> {
                gate.await();
                return patch("files", file, "location", "destinationFolderId", destination, null)
                    .statusCode();
              });
      var b =
          executor.submit(
              () -> {
                gate.await();
                return patch(
                        "folders", folder, "location", "destinationFolderId", destination, null)
                    .statusCode();
              });
      gate.countDown();
      assertThat(java.util.List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
  }

  @Test
  void foreignWorkspaceSourcesAndDestinationsAreNotFound() throws Exception {
    String file = uploadedFile("synthetic.txt", null), folder = create("Synthetic", null);
    String foreignFile = uploadedFile("Foreign synthetic secret.txt", null),
        foreignFolder = create("Foreign synthetic secret", null);
    jdbc.update(
        "insert into workspaces (workspace_id,name,slug,status,is_seeded,created_at,updated_at)"
            + " values"
            + " ('wrk_task4_foreign','Synthetic','task4-foreign','active',false,current_timestamp,current_timestamp)");
    jdbc.update(
        "update drive_files set workspace_id='wrk_task4_foreign' where file_id=?", foreignFile);
    jdbc.update(
        "update drive_folders set workspace_id='wrk_task4_foreign' where folder_id=?",
        foreignFolder);
    for (String kind : new String[] {"files", "folders"}) {
      String id = kind.equals("files") ? file : folder;
      String foreign = kind.equals("files") ? foreignFile : foreignFolder;
      assertThat(patch(kind, foreign, "name", "name", "Renamed", null).statusCode()).isEqualTo(404);
      var response = patch(kind, id, "location", "destinationFolderId", foreignFolder, null);
      assertThat(response.statusCode()).isEqualTo(404);
      assertThat(response.body()).doesNotContain("Foreign synthetic secret");
    }
  }

  HttpResponse<String> patch(
      String kind, String id, String action, String field, String value, String actor)
      throws Exception {
    var body = new java.util.HashMap<String, String>();
    body.put(field, value);
    var builder =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:" + port + "/api/drive/" + kind + "/" + id + "/" + action))
            .header("Content-Type", "application/json")
            .method("PATCH", HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    if (actor != null) builder.header("X-Actor-Id", actor);
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  String create(String name, String parent) throws Exception {
    var r = post(name, parent);
    assertThat(r.statusCode()).isEqualTo(201);
    return json.readTree(r.body()).get("folderId").asString();
  }

  HttpResponse<String> post(String name, String parent) throws Exception {
    String body =
        json.writeValueAsString(
            java.util.Map.of("name", name, "parentFolderId", parent == null ? "" : parent));
    return client.send(
        request("")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> get(String path, String actor) throws Exception {
    var r = request(path);
    if (actor != null) r.header("X-Actor-Id", actor);
    return client.send(r.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(
        URI.create("http://localhost:" + port + "/api/drive/folders" + path));
  }

  HttpResponse<String> upload(String name, String mime, byte[] bytes, String folder)
      throws Exception {
    return upload(name, mime, bytes, folder, null);
  }

  HttpResponse<String> upload(String name, String mime, byte[] bytes, String folder, String actor)
      throws Exception {
    var out = new ByteArrayOutputStream();
    out.write(
        ("--synthetic-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + name
                + "\"\r\nContent-Type: "
                + mime
                + "\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    out.write(bytes);
    out.write("\r\n".getBytes());
    if (folder != null)
      out.write(
          ("--synthetic-boundary\r\nContent-Disposition: form-data; name=\"folderId\"\r\n\r\n"
                  + folder
                  + "\r\n")
              .getBytes());
    out.write("--synthetic-boundary--\r\n".getBytes());
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/drive/files"))
            .header("Content-Type", "multipart/form-data; boundary=synthetic-boundary")
            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()));
    if (actor != null) builder.header("X-Actor-Id", actor);
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
