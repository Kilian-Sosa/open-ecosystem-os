package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    classes = {
      com.openecosystem.os.OpenEcosystemApiApplication.class,
      DriveFileControllerTest.DriveFileControllerTestConfiguration.class
    },
    properties =
        "spring.datasource.url=jdbc:h2:mem:drive_trash_journey;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DriveTrashJourneyTest {
  private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");
  private static final String WORKSPACE = "wrk_dev_placeholder";
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired DriveTrashPurgeService purge;
  @Autowired DriveFileControllerTest.FakeFileObjectStorage storage;

  @MockitoBean(name = "driveClock")
  Clock clock;

  final HttpClient client = HttpClient.newHttpClient();
  final JsonMapper json = JsonMapper.builder().build();

  @BeforeEach
  void time() {
    when(clock.instant()).thenReturn(NOW);
  }

  @Test
  void nestedFolderTrashRestoreExpiryAndManualPurgeJourney() throws Exception {
    String parent = folder(null), nested = folder(parent), file = upload(nested);
    String key =
        jdbc.queryForObject(
            "select storage_key from drive_files where file_id=?", String.class, file);
    byte[] ciphertext = storage.objectBytes(key).clone();
    seedDerived(file, key);
    String first = post("/folders/" + parent + "/trash", Map.of(), 200).get("groupId").asString();
    assertThat(get("/folders/contents", 200)).doesNotContain(parent);
    get("/files/" + file + "/download", 404);
    get("/folders/" + parent + "/download", 404);
    assertThat(storage.objectBytes(key)).isEqualTo(ciphertext);
    assertThat(count("ocr_results", "file_id", file)).isEqualTo(1);
    post("/trash/" + first + "/restore", Map.of(), 200);
    assertThat(get("/files/" + file + "/download", 200)).isEqualTo("Synthetic Trash journey bytes");
    assertThat(storage.objectBytes(key)).isEqualTo(ciphertext);
    assertThat(
            jdbc.queryForObject(
                "select status from invoice_extractions where file_id=?", String.class, file))
        .isEqualTo("review_required");
    assertThat(
            jdbc.queryForObject(
                "select folder_id from drive_files where file_id=?", String.class, file))
        .isEqualTo(nested);
    String second = post("/folders/" + parent + "/trash", Map.of(), 200).get("groupId").asString();
    assertThat(post("/trash/" + second + "/purge", Map.of(), 200).get("state").asString())
        .isEqualTo("PURGING");
    post("/trash/" + second + "/restore", Map.of(), 409);
    assertThat(storage.objectBytes(key)).isNull();
    assertThat(count("ocr_results", "file_id", file)).isEqualTo(1);
    reconcile(file);
    when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
    purge.runCleanup(clock.instant(), 25);
    assertThat(post("/trash/" + second + "/purge", Map.of(), 200).get("state").asString())
        .isEqualTo("PURGED");
    for (String table :
        new String[] {"drive_files", "ocr_jobs", "ocr_results", "invoice_extractions"})
      assertThat(count(table, "file_id", file)).as(table).isZero();
    assertThat(count("drive_folders", "folder_id", parent)).isZero();
    assertThat(count("drive_folders", "folder_id", nested)).isZero();
    assertThat(
            jdbc.queryForList(
                "select attributes_json from audit_records where resource_id=?",
                String.class,
                parent))
        .allSatisfy(
            attributes ->
                assertThat(attributes).doesNotContain("Synthetic", "storage_key", "document_text"));
  }

  @Test
  void expiryDeniesRestoreWithSchedulerPausedAndCleanupEventuallyCompletes() throws Exception {
    String file = upload(folder(null));
    String group = post("/files/" + file + "/trash", Map.of(), 200).get("groupId").asString();
    when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(30)));
    post("/trash/" + group + "/restore", Map.of(), 409);
    assertThat(count("drive_files", "file_id", file)).isEqualTo(1);
    reconcile(file);
    purge.runCleanup(clock.instant(), 25);
    assertThat(post("/trash/" + group + "/purge", Map.of(), 200).get("state").asString())
        .isEqualTo("PURGED");
    assertThat(count("drive_files", "file_id", file)).isZero();
  }

  @Test
  void reconciliationRevisionAloneCannotCompletePurgeWithOutstandingRemoteOperation()
      throws Exception {
    String file = upload(folder(null));
    String group = post("/files/" + file + "/trash", Map.of(), 200).get("groupId").asString();
    reconcile(file);
    String document = "synthetic-" + UUID.randomUUID();
    jdbc.update(
        "insert into"
            + " drive_search_remote_documents(workspace_id,file_id,document_id,desired_revision,desired_present,completed_revision,completed_present,operation_id,operation_state)"
            + " values (?,?,?,1,false,1,false,?,'UNCERTAIN')",
        WORKSPACE,
        file,
        document,
        UUID.randomUUID().toString());
    assertThat(post("/trash/" + group + "/purge", Map.of(), 200).get("state").asString())
        .isEqualTo("PURGING");
    assertThat(count("drive_files", "file_id", file)).isEqualTo(1);
    jdbc.update(
        "update drive_search_remote_documents set operation_id=null,operation_state=null where"
            + " document_id=?",
        document);
    when(clock.instant()).thenReturn(NOW.plusSeconds(3600));
    purge.runCleanup(clock.instant(), 25);
    assertThat(post("/trash/" + group + "/purge", Map.of(), 200).get("state").asString())
        .isEqualTo("PURGED");
  }

  void reconcile(String file) {
    jdbc.update(
        "update drive_search_lifecycle set reconciled_revision=revision where file_id=?", file);
  }

  int count(String table, String column, String id) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where " + column + "=?", Integer.class, id);
  }

  void seedDerived(String file, String key) {
    String suffix = UUID.randomUUID().toString();
    String job = "job_" + suffix, result = "ocr_" + suffix, execution = "exec_" + suffix;
    jdbc.update(
        "insert into"
            + " ocr_jobs(job_id,file_id,workspace_id,actor_id,source_event_id,correlation_id,content_type,storage_key,status,attempt_count,max_attempts,queued_at,created_at,updated_at)"
            + " values"
            + " (?,?,?,'usr_dev_placeholder',?,'synthetic','text/plain',?,'completed',1,3,current_timestamp,current_timestamp,current_timestamp)",
        job,
        file,
        WORKSPACE,
        suffix,
        key);
    jdbc.update(
        "insert into"
            + " ocr_results(ocr_result_id,job_id,file_id,workspace_id,provider,provider_version,document_text,page_count,word_count,created_at,updated_at)"
            + " values (?,?,?,?,'synthetic','test','Synthetic retained"
            + " OCR',1,0,current_timestamp,current_timestamp)",
        result,
        job,
        file,
        WORKSPACE);
    jdbc.update(
        "insert into"
            + " workflow_executions(execution_id,workflow_id,workflow_version_id,workflow_version_number,workspace_id,actor_id,correlation_id,trigger_type,trigger_idempotency_key,status,retry_count,started_at,created_at,updated_at)"
            + " select"
            + " ?,workflow_id,version_id,version_number,?,'usr_dev_placeholder','synthetic','manual',?,'completed',0,current_timestamp,current_timestamp,current_timestamp"
            + " from workflow_versions order by version_id limit 1",
        execution,
        WORKSPACE,
        suffix);
    jdbc.update(
        "insert into"
            + " invoice_extractions(extraction_id,workflow_execution_id,ocr_result_id,job_id,file_id,workspace_id,extractor_name,extractor_version,status,warnings_json,field_count,warning_count,created_at,updated_at)"
            + " values"
            + " (?,?,?,?,?,?,'synthetic','test','review_required','[]',0,0,current_timestamp,current_timestamp)",
        "inv_" + suffix,
        execution,
        result,
        job,
        file,
        WORKSPACE);
  }

  String folder(String parent) throws Exception {
    return post(
            "/folders",
            Map.of(
                "name",
                "Synthetic " + UUID.randomUUID(),
                "parentFolderId",
                parent == null ? "" : parent),
            201)
        .get("folderId")
        .asString();
  }

  String upload(String folder) throws Exception {
    String body =
        "--synthetic-trash\r\n"
            + "Content-Disposition: form-data; name=\"file\";"
            + " filename=\"synthetic-journey.txt\"\r\n"
            + "Content-Type: text/plain\r\n\r\n"
            + "Synthetic Trash journey bytes\r\n"
            + "--synthetic-trash\r\n"
            + "Content-Disposition: form-data; name=\"folderId\"\r\n\r\n"
            + folder
            + "\r\n--synthetic-trash--\r\n";
    var response =
        client.send(
            request("/files")
                .header("Content-Type", "multipart/form-data; boundary=synthetic-trash")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(201);
    return json.readTree(response.body()).get("fileId").asString();
  }

  tools.jackson.databind.JsonNode post(String path, Map<String, ?> body, int expected)
      throws Exception {
    var response =
        client.send(
            request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(path).isEqualTo(expected);
    return json.readTree(response.body());
  }

  String get(String path, int expected) throws Exception {
    var response = client.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(path).isEqualTo(expected);
    return response.body();
  }

  HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/drive" + path));
  }
}
