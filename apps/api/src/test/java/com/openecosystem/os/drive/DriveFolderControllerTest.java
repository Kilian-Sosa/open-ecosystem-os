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
