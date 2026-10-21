package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.OpenEcosystemApiApplication;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;
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
    properties =
        "spring.datasource.url=jdbc:h2:mem:drivecorejourney;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DriveCoreJourneyTest {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired DriveFileControllerTest.FakeFileObjectStorage storage;
  final HttpClient client = HttpClient.newHttpClient();
  final JsonMapper json = JsonMapper.builder().build();
  final byte[] source =
      "Synthetic integration fixture.\nOriginal bytes: café.\n".getBytes(StandardCharsets.UTF_8);
  String workspace;
  String owner;

  @BeforeEach
  void seedPrivateWorkspace() {
    String suffix = UUID.randomUUID().toString();
    workspace = "wrk_" + suffix;
    owner = "usr_" + suffix;
    jdbc.update(
        "insert into workspaces (workspace_id,name,slug,status,is_seeded,created_at,updated_at)"
            + " values (?, 'Synthetic journey', ?,"
            + " 'active',false,current_timestamp,current_timestamp)",
        workspace,
        workspace);
    member(owner, workspace);
  }

  @Test
  void nestedUploadRenameMoveDownloadZipPreservesBytesAndLineage() throws Exception {
    String projects = folder("Projects", null);
    String invoices = folder("Invoices", projects);
    String year = folder("2026", invoices);
    folder("Empty", invoices);
    String destination = folder("Archive", null);
    String file = upload("synthetic-original.txt", year);
    Map<String, Object> original = metadata(file);
    String key = (String) original.get("storage_key");
    byte[] ciphertext = storage.objectBytes(key).clone();
    assertThat(ciphertext).isNotEqualTo(source);
    seedLineage(file, key);
    var lineage = lineage(file);

    assertThat(text(get("/folders/contents", owner)))
        .contains("Projects")
        .doesNotContain("synthetic-original.txt");
    assertThat(text(get("/folders/contents?folderId=" + year, owner)))
        .contains("synthetic-original.txt");
    patch("files/" + file + "/name", "name", "synthetic-renamed.txt");
    patch("folders/" + invoices + "/name", "name", "Receipts");
    patch("folders/" + invoices + "/location", "destinationFolderId", destination);
    patch("files/" + file + "/location", "destinationFolderId", null);
    assertThat(text(get("/folders/contents", owner))).contains("synthetic-renamed.txt");
    assertThat(text(get("/folders/contents?folderId=" + year, owner)))
        .doesNotContain("synthetic-renamed.txt");
    patch("files/" + file + "/location", "destinationFolderId", year);

    var content = get("/files/" + file + "/download", owner);
    assertProtected(content, "attachment");
    assertThat(content.body()).isEqualTo(source);
    assertThat(content.headers().firstValue("Content-Disposition").orElseThrow())
        .contains("synthetic-renamed.txt");
    var preview = get("/files/" + file + "/preview", owner);
    assertProtected(preview, "inline");
    assertThat(preview.body()).isEqualTo(source);
    var zip = get("/folders/" + destination + "/download", owner);
    assertProtected(zip, "attachment");
    assertThat(zip.headers().firstValue("Content-Type")).hasValue("application/zip");
    var entries = unzip(zip.body());
    assertThat(entries.keySet())
        .containsExactlyInAnyOrder(
            "Receipts/",
            "Receipts/2026/",
            "Receipts/Empty/",
            "Receipts/2026/synthetic-renamed.txt");
    assertThat(entries.get("Receipts/2026/synthetic-renamed.txt")).isEqualTo(source);
    assertThat(entries.get("Receipts/Empty/")).isEmpty();
    assertThat(text(get("/folders/contents?folderId=" + projects, owner)))
        .doesNotContain("Receipts", "Invoices");
    assertThat(text(get("/folders/contents?folderId=" + destination, owner))).contains("Receipts");
    Map<String, Object> after = metadata(file);
    for (String field :
        new String[] {
          "file_id", "storage_key", "content_iv", "checksum_sha256", "size_bytes", "created_at"
        }) {
      assertThat(after.get(field)).as(field).isEqualTo(original.get(field));
    }
    assertThat(after.get("folder_id")).isEqualTo(year);
    assertThat(storage.objectBytes(key)).isEqualTo(ciphertext);
    assertThat(lineage(file)).isEqualTo(lineage);
    assertThat(
            jdbc.queryForObject(
                "select count(*) from event_outbox where workspace_id=?", Integer.class, workspace))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForList(
                    "select attributes_json from audit_records where workspace_id=?",
                    String.class,
                    workspace)
                .toString())
        .doesNotContain("synthetic-original", "synthetic-renamed", "Receipts", key);
  }

  @Test
  void foreignUserCannotInspectOrMutateSubtree() throws Exception {
    String root = folder("Synthetic private projects", null);
    String child = folder("Synthetic confidential invoices", root);
    String file = upload("synthetic-secret.txt", child);
    var before = metadata(file);
    String sameWorkspaceUser = "usr_" + UUID.randomUUID();
    member(sameWorkspaceUser, workspace);
    String foreignWorkspace = "wrk_" + UUID.randomUUID();
    jdbc.update(
        "insert into workspaces (workspace_id,name,slug,status,is_seeded,created_at,updated_at)"
            + " values (?, 'Synthetic foreign', ?,"
            + " 'active',false,current_timestamp,current_timestamp)",
        foreignWorkspace,
        foreignWorkspace);
    String foreignUser = "usr_" + UUID.randomUUID();
    member(foreignUser, foreignWorkspace);
    for (String actor : new String[] {sameWorkspaceUser, foreignUser}) {
      String actorWorkspace = actor.equals(foreignUser) ? foreignWorkspace : workspace;
      for (String path :
          new String[] {
            "/folders/" + root,
            "/folders/contents?folderId=" + child,
            "/folders/" + root + "/download",
            "/files/" + file,
            "/files/" + file + "/preview",
            "/files/" + file + "/download"
          }) {
        assertDenied(send("GET", path, null, actor, actorWorkspace));
      }
      for (String resource : new String[] {"files/" + file, "folders/" + child}) {
        assertDenied(
            send(
                "PATCH",
                "/" + resource + "/name",
                Map.of("name", "Unauthorized"),
                actor,
                actorWorkspace));
        assertDenied(
            send(
                "PATCH",
                "/" + resource + "/location",
                nullable("destinationFolderId", null),
                actor,
                actorWorkspace));
      }
      var list = send("GET", "/folders/contents", null, actor, actorWorkspace);
      assertThat(list.statusCode()).isEqualTo(200);
      assertNoLabels(list);
    }
    assertThat(metadata(file)).isEqualTo(before);
    assertThat(text(get("/folders/contents?folderId=" + root, owner)))
        .contains("Synthetic confidential invoices");
    assertThat(get("/files/" + file + "/download", owner).body()).isEqualTo(source);
  }

  void member(String actor, String targetWorkspace) {
    jdbc.update(
        "insert into identity_users"
            + " (user_id,display_name,email,avatar_initials,status,is_seeded,created_at,updated_at)"
            + " values (?,'Synthetic',?,'SY','active',false,current_timestamp,current_timestamp)",
        actor,
        actor + "@example.test");
    jdbc.update(
        "insert into workspace_memberships"
            + " (workspace_id,user_id,role,is_default,created_at,updated_at) values"
            + " (?,?,'WORKSPACE_ADMIN',false,current_timestamp,current_timestamp)",
        targetWorkspace,
        actor);
  }

  void seedLineage(String file, String key) {
    jdbc.update(
        "insert into ocr_jobs"
            + " (job_id,file_id,workspace_id,actor_id,source_event_id,correlation_id,content_type,storage_key,status,attempt_count,max_attempts,queued_at,created_at,updated_at)"
            + " values"
            + " (?,?,?,?,'synthetic-event','synthetic-correlation','text/plain',?,'completed',1,3,current_timestamp,current_timestamp,current_timestamp)",
        "job_" + file,
        file,
        workspace,
        owner,
        key);
    jdbc.update(
        "insert into ocr_results"
            + " (ocr_result_id,job_id,file_id,workspace_id,provider,provider_version,document_text,page_count,word_count,created_at,updated_at)"
            + " values (?,?,?,?,'synthetic','test','Synthetic OCR"
            + " fixture',1,0,current_timestamp,current_timestamp)",
        "ocr_" + file,
        "job_" + file,
        file,
        workspace);
    jdbc.update(
        "insert into workflow_executions"
            + " (execution_id,workflow_id,workflow_version_id,workflow_version_number,workspace_id,actor_id,correlation_id,trigger_type,trigger_idempotency_key,status,retry_count,started_at,created_at,updated_at)"
            + " select"
            + " ?,workflow_id,version_id,version_number,?,?,'synthetic-correlation','manual',?,'completed',0,current_timestamp,current_timestamp,current_timestamp"
            + " from workflow_versions order by version_id limit 1",
        "exec_" + file,
        workspace,
        owner,
        "synthetic_" + file);
    jdbc.update(
        "insert into invoice_extractions"
            + " (extraction_id,workflow_execution_id,ocr_result_id,job_id,file_id,workspace_id,extractor_name,extractor_version,status,warnings_json,field_count,warning_count,created_at,updated_at)"
            + " values"
            + " (?,?,?,?,?,?,'synthetic','test','review_required','[]',0,0,current_timestamp,current_timestamp)",
        "inv_" + file,
        "exec_" + file,
        "ocr_" + file,
        "job_" + file,
        file,
        workspace);
  }

  Object lineage(String file) {
    return java.util.List.of(
        jdbc.queryForMap("select * from ocr_jobs where file_id=?", file),
        jdbc.queryForMap("select * from ocr_results where file_id=?", file),
        jdbc.queryForMap("select * from invoice_extractions where file_id=?", file));
  }

  Map<String, Object> metadata(String file) {
    return jdbc.queryForMap("select * from drive_files where file_id=?", file);
  }

  String folder(String name, String parent) throws Exception {
    var body = nullable("parentFolderId", parent);
    body.put("name", name);
    var response = send("POST", "/folders", body, owner, workspace);
    assertThat(response.statusCode()).isEqualTo(201);
    return json.readTree(response.body()).get("folderId").asString();
  }

  String upload(String name, String folder) throws Exception {
    var body = new ByteArrayOutputStream();
    body.write(
        ("--synthetic-journey\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + name
                + "\"\r\nContent-Type: text/plain\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    body.write(source);
    body.write(
        ("\r\n--synthetic-journey\r\nContent-Disposition: form-data; name=\"folderId\"\r\n\r\n"
                + folder
                + "\r\n--synthetic-journey--\r\n")
            .getBytes(StandardCharsets.UTF_8));
    var response =
        client.send(
            request("/files", owner, workspace)
                .header("Content-Type", "multipart/form-data; boundary=synthetic-journey")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build(),
            HttpResponse.BodyHandlers.ofByteArray());
    assertThat(response.statusCode()).isEqualTo(201);
    return json.readTree(response.body()).get("fileId").asString();
  }

  void patch(String path, String field, String value) throws Exception {
    assertThat(send("PATCH", "/" + path, nullable(field, value), owner, workspace).statusCode())
        .isEqualTo(200);
  }

  HttpResponse<byte[]> get(String path, String actor) throws Exception {
    return send("GET", path, null, actor, workspace);
  }

  HttpResponse<byte[]> send(
      String method, String path, Map<String, String> body, String actor, String targetWorkspace)
      throws Exception {
    var builder = request(path, actor, targetWorkspace);
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    if (body != null) builder.header("Content-Type", "application/json");
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
  }

  HttpRequest.Builder request(String path, String actor, String targetWorkspace) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/drive" + path))
        .header("X-Actor-Id", actor)
        .header("X-Workspace-Id", targetWorkspace);
  }

  Map<String, String> nullable(String field, String value) {
    var body = new HashMap<String, String>();
    body.put(field, value);
    return body;
  }

  void assertProtected(HttpResponse<byte[]> response, String disposition) {
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
    assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
    assertThat(response.headers().firstValue("Content-Disposition").orElseThrow())
        .contains(disposition);
  }

  void assertDenied(HttpResponse<byte[]> response) {
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(response.headers().firstValue("Content-Disposition")).isEmpty();
    assertNoLabels(response);
  }

  void assertNoLabels(HttpResponse<byte[]> response) {
    assertThat(text(response))
        .doesNotContain(
            "Synthetic private",
            "Synthetic confidential",
            "synthetic-secret",
            "Original bytes",
            "storage_key",
            "encrypted_name");
  }

  String text(HttpResponse<byte[]> response) {
    return new String(response.body(), StandardCharsets.UTF_8);
  }

  Map<String, byte[]> unzip(byte[] bytes) throws Exception {
    var entries = new LinkedHashMap<String, byte[]>();
    try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        assertThat(entry.getName()).doesNotContain("..").doesNotStartWith("/");
        assertThat(entries.put(entry.getName(), zip.readAllBytes())).isNull();
      }
    }
    return entries;
  }
}
