package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.OpenEcosystemApiApplication;
import com.openecosystem.os.common.security.CorrelationIds;
import com.openecosystem.os.common.security.PlaceholderAuthenticationContext;
import com.openecosystem.os.drive.storage.FileObjectStorage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    classes = {
      OpenEcosystemApiApplication.class,
      DriveFileControllerTest.DriveFileControllerTestConfiguration.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DriveFileControllerTest {

  private static final String BOUNDARY = "----open-ecosystem-test-boundary";

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @LocalServerPort private int port;

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private FakeFileObjectStorage objectStorage;
  @Autowired private com.openecosystem.os.drive.crypto.FileEncryptionService encryption;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from event_outbox");
    jdbcTemplate.update("delete from audit_records");
    jdbcTemplate.update("delete from drive_files");
    objectStorage.clear();
  }

  @Test
  void uploadStoresEncryptedObjectMetadataAuditAndFileUploadedOutboxEvent() throws Exception {
    byte[] plaintext = SyntheticDriveFiles.pdf();

    HttpResponse<String> response =
        httpClient.send(
            uploadRequest("invoice.pdf", "application/pdf", plaintext)
                .header(
                    PlaceholderAuthenticationContext.ACTOR_HEADER,
                    PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID)
                .header(
                    PlaceholderAuthenticationContext.WORKSPACE_HEADER,
                    PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID)
                .header(CorrelationIds.HEADER_NAME, "corr_drive_upload")
                .build(),
            BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(response.body()).contains("\"name\":\"invoice.pdf\"");
    assertThat(response.body()).contains("\"contentType\":\"application/pdf\"");
    assertThat(response.body()).contains("\"sizeBytes\":" + plaintext.length);
    assertThat(response.body()).contains("\"encrypted\":true");
    assertThat(response.body()).contains("\"visibility\":\"private\"");

    Map<String, Object> metadata =
        jdbcTemplate.queryForMap(
            "select * from drive_files where workspace_id = ?",
            PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID);
    String fileId = (String) metadata.get("file_id");
    assertThat(metadata.get("encrypted_name")).asString().doesNotContain("invoice.pdf");
    assertThat(metadata.get("storage_key"))
        .isEqualTo(
            "workspaces/"
                + PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID
                + "/drive/"
                + fileId
                + "/original");

    byte[] storedContent = objectStorage.objectBytes((String) metadata.get("storage_key"));
    assertThat(storedContent).isNotEmpty();
    assertThat(Arrays.equals(storedContent, plaintext)).isFalse();

    Map<String, Object> audit =
        jdbcTemplate.queryForMap("select * from audit_records where resource_id = ?", fileId);
    assertThat(audit.get("action")).isEqualTo("drive.file.uploaded");
    assertThat(audit.get("actor_id")).isEqualTo(PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID);
    assertThat(audit.get("correlation_id")).isEqualTo("corr_drive_upload");
    assertThat(audit.get("attributes_json")).asString().doesNotContain("invoice.pdf");

    Map<String, Object> event =
        jdbcTemplate.queryForMap(
            "select * from event_outbox where workspace_id = ?",
            PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID);
    assertThat(event.get("event_type")).isEqualTo("FileUploaded");
    assertThat(event.get("source")).isEqualTo("drive");
    assertThat(event.get("actor_id")).isEqualTo(PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID);
    assertThat(event.get("correlation_id")).isEqualTo("corr_drive_upload");
    assertThat(event.get("idempotency_key")).isEqualTo("drive:" + fileId + ":uploaded:v1");
    assertThat(event.get("payload_json")).asString().contains("\"encryptionAlgorithm\"");
    assertThat(event.get("envelope_json")).asString().doesNotContain("invoice.pdf");
  }

  @Test
  void listsOnlyFilesFromRequestedWorkspaceAfterUploads() throws Exception {
    seedWorkspaceMembership("usr_workspace_a", "wrk_workspace_a");
    seedWorkspaceMembership("usr_workspace_b", "wrk_workspace_b");

    HttpResponse<String> workspaceAUpload =
        httpClient.send(
            uploadRequest("workspace-a.pdf", "application/pdf", SyntheticDriveFiles.pdf())
                .header(PlaceholderAuthenticationContext.ACTOR_HEADER, "usr_workspace_a")
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, "wrk_workspace_a")
                .build(),
            BodyHandlers.ofString());
    HttpResponse<String> workspaceBUpload =
        httpClient.send(
            uploadRequest("workspace-b.pdf", "application/pdf", SyntheticDriveFiles.pdf())
                .header(PlaceholderAuthenticationContext.ACTOR_HEADER, "usr_workspace_b")
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, "wrk_workspace_b")
                .build(),
            BodyHandlers.ofString());

    assertThat(workspaceAUpload.statusCode()).isEqualTo(201);
    assertThat(workspaceBUpload.statusCode()).isEqualTo(201);

    HttpResponse<String> listResponse =
        httpClient.send(
            HttpRequest.newBuilder(uri("/api/drive/files"))
                .header(PlaceholderAuthenticationContext.ACTOR_HEADER, "usr_workspace_a")
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, "wrk_workspace_a")
                .GET()
                .build(),
            BodyHandlers.ofString());

    assertThat(listResponse.statusCode()).isEqualTo(200);
    assertThat(listResponse.body())
        .contains("\"name\":\"workspace-a.pdf\"")
        .doesNotContain("\"name\":\"workspace-b.pdf\"");
  }

  @Test
  void hidesPrivateFilesFromAnUnsharedWorkspaceMember() throws Exception {
    String workspaceId = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    seedExistingWorkspaceMembership("usr_unshared", workspaceId, "VIEWER");

    HttpResponse<String> uploadResponse =
        httpClient.send(
            uploadRequest("private.pdf", "application/pdf", SyntheticDriveFiles.pdf())
                .header(
                    PlaceholderAuthenticationContext.ACTOR_HEADER,
                    PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID)
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, workspaceId)
                .build(),
            BodyHandlers.ofString());
    String fileId =
        jdbcTemplate.queryForObject(
            "select file_id from drive_files where workspace_id = ?", String.class, workspaceId);

    HttpResponse<String> listResponse =
        httpClient.send(
            HttpRequest.newBuilder(uri("/api/drive/files"))
                .header(PlaceholderAuthenticationContext.ACTOR_HEADER, "usr_unshared")
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, workspaceId)
                .GET()
                .build(),
            BodyHandlers.ofString());
    HttpResponse<String> detailResponse =
        httpClient.send(
            HttpRequest.newBuilder(uri("/api/drive/files/" + fileId))
                .header(PlaceholderAuthenticationContext.ACTOR_HEADER, "usr_unshared")
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, workspaceId)
                .GET()
                .build(),
            BodyHandlers.ofString());

    assertThat(uploadResponse.statusCode()).isEqualTo(201);
    assertThat(listResponse.statusCode()).isEqualTo(200);
    assertThat(listResponse.body()).doesNotContain("private.pdf");
    assertThat(detailResponse.statusCode()).isEqualTo(404);
    assertThat(detailResponse.body()).doesNotContain("private.pdf");
  }

  @Test
  void activeOwnerCanViewItsPrivateFile() throws Exception {
    String fileId =
        uploadAndGetFileId(
            PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
            PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID,
            "owner-private.pdf");

    HttpResponse<String> response =
        httpClient.send(
            requestForFile(
                fileId,
                PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
                PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID),
            BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("owner-private.pdf");
    assertThat(response.body()).contains("\"visibility\":\"private\"");
  }

  @Test
  void privateFilesHaveNoRoleBypassButWorkspaceFilesUseApprovedRoles() throws Exception {
    String workspaceId = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    String privateFileId =
        uploadAndGetFileId(
            PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID, workspaceId, "private-policy.pdf");
    String workspaceFileId =
        uploadAndGetFileId(
            PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID, workspaceId, "workspace-policy.pdf");
    jdbcTemplate.update(
        "update drive_files set visibility = 'workspace' where file_id = ?", workspaceFileId);

    Map<String, String> roles =
        Map.of(
            "usr_policy_instance_owner", "INSTANCE_OWNER",
            "usr_policy_admin", "WORKSPACE_ADMIN",
            "usr_policy_developer", "DEVELOPER",
            "usr_policy_editor", "EDITOR",
            "usr_policy_viewer", "VIEWER",
            "usr_policy_guest", "GUEST",
            "usr_policy_auditor", "AUDITOR");
    roles.forEach((actorId, role) -> seedExistingWorkspaceMembership(actorId, workspaceId, role));

    for (String actorId : roles.keySet()) {
      HttpResponse<String> privateResponse =
          httpClient.send(
              requestForFile(privateFileId, actorId, workspaceId), BodyHandlers.ofString());
      assertThat(privateResponse.statusCode()).as(actorId).isEqualTo(404);
    }

    for (String actorId :
        new String[] {
          "usr_policy_instance_owner",
          "usr_policy_admin",
          "usr_policy_developer",
          "usr_policy_editor",
          "usr_policy_viewer"
        }) {
      HttpResponse<String> workspaceResponse =
          httpClient.send(
              requestForFile(workspaceFileId, actorId, workspaceId), BodyHandlers.ofString());
      assertThat(workspaceResponse.statusCode()).as(actorId).isEqualTo(200);
      assertThat(workspaceResponse.body()).contains("workspace-policy.pdf");
    }

    for (String actorId : new String[] {"usr_policy_guest", "usr_policy_auditor"}) {
      HttpResponse<String> workspaceResponse =
          httpClient.send(
              requestForFile(workspaceFileId, actorId, workspaceId), BodyHandlers.ofString());
      assertThat(workspaceResponse.statusCode()).as(actorId).isEqualTo(404);
    }
  }

  @Test
  void activeExplicitUserGrantsWorkForPrivateGuestAndAuditorFilesAndRevocationRemovesAccess()
      throws Exception {
    String workspaceId = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    String guestId = "usr_grant_guest";
    String auditorId = "usr_grant_auditor";
    seedExistingWorkspaceMembership(guestId, workspaceId, "GUEST");
    seedExistingWorkspaceMembership(auditorId, workspaceId, "AUDITOR");

    String privateFileId =
        uploadAndGetFileId(
            PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID, workspaceId, "granted-private.pdf");
    seedGrant(privateFileId, guestId, "grant_guest_private");
    seedGrant(privateFileId, auditorId, "grant_auditor_private");

    assertThat(
            httpClient
                .send(requestForFile(privateFileId, guestId, workspaceId), BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(200);
    assertThat(
            httpClient
                .send(
                    requestForFile(privateFileId, auditorId, workspaceId), BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(200);

    jdbcTemplate.update(
        "update drive_file_user_grants set revoked_by_user_id = ?, revoked_at = current_timestamp "
            + "where file_id = ? and grantee_user_id = ?",
        PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
        privateFileId,
        guestId);
    assertThat(
            httpClient
                .send(requestForFile(privateFileId, guestId, workspaceId), BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(404);
  }

  @Test
  void listOmitsDeniedFilesAndDetailDenialsMatchMissingAndForeignFiles() throws Exception {
    String workspaceId = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    String viewerId = "usr_list_viewer";
    seedExistingWorkspaceMembership(viewerId, workspaceId, "VIEWER");
    seedWorkspaceMembership("usr_foreign_drive_owner", "wrk_drive_foreign");
    String allowedFileId =
        uploadAndGetFileId(
            PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID, workspaceId, "allowed-list.pdf");
    jdbcTemplate.update(
        "update drive_files set visibility = 'workspace' where file_id = ?", allowedFileId);
    String deniedFileId =
        uploadAndGetFileId(
            PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
            workspaceId,
            "denied-list-storage-key.pdf");
    String foreignFileId =
        uploadAndGetFileId("usr_foreign_drive_owner", "wrk_drive_foreign", "foreign-list.pdf");

    HttpResponse<String> listResponse =
        httpClient.send(
            HttpRequest.newBuilder(uri("/api/drive/files"))
                .header(PlaceholderAuthenticationContext.ACTOR_HEADER, viewerId)
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, workspaceId)
                .GET()
                .build(),
            BodyHandlers.ofString());
    assertThat(listResponse.statusCode()).isEqualTo(200);
    assertThat(listResponse.body())
        .contains("allowed-list.pdf")
        .doesNotContain("denied-list-storage-key.pdf")
        .doesNotContain("workspaces/" + workspaceId + "/drive/");

    HttpResponse<String> deniedResponse =
        httpClient.send(
            requestForFile(deniedFileId, viewerId, workspaceId), BodyHandlers.ofString());
    HttpResponse<String> missingResponse =
        httpClient.send(
            requestForFile("file_missing", viewerId, workspaceId), BodyHandlers.ofString());
    HttpResponse<String> foreignResponse =
        httpClient.send(
            requestForFile(foreignFileId, viewerId, workspaceId), BodyHandlers.ofString());

    assertThat(deniedResponse.statusCode()).isEqualTo(404);
    assertThat(missingResponse.statusCode()).isEqualTo(404);
    assertThat(foreignResponse.statusCode()).isEqualTo(404);
    assertThat(deniedResponse.body())
        .contains("\"error\":\"NOT_FOUND\"")
        .contains("\"message\":\"Drive file was not found\"");
    assertThat(missingResponse.body())
        .contains("\"error\":\"NOT_FOUND\"")
        .contains("\"message\":\"Drive file was not found\"");
    assertThat(foreignResponse.body())
        .contains("\"error\":\"NOT_FOUND\"")
        .contains("\"message\":\"Drive file was not found\"");
  }

  @Test
  void disabledOrRemovedMembersCannotUsePersistedFileAccess() throws Exception {
    String workspaceId = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    String disabledId = "usr_disabled_drive_member";
    String removedId = "usr_removed_drive_member";
    seedExistingWorkspaceMembership(disabledId, workspaceId, "VIEWER");
    seedExistingWorkspaceMembership(removedId, workspaceId, "VIEWER");
    String fileId =
        uploadAndGetFileId(
            PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID, workspaceId, "inactive-member.pdf");
    seedGrant(fileId, disabledId, "grant_disabled_member");
    seedGrant(fileId, removedId, "grant_removed_member");

    jdbcTemplate.update(
        "update identity_users set status = 'disabled' where user_id = ?", disabledId);
    jdbcTemplate.update(
        "delete from workspace_memberships where workspace_id = ? and user_id = ?",
        workspaceId,
        removedId);

    assertThat(
            httpClient
                .send(requestForFile(fileId, disabledId, workspaceId), BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(403);
    assertThat(
            httpClient
                .send(requestForFile(fileId, removedId, workspaceId), BodyHandlers.ofString())
                .statusCode())
        .isEqualTo(403);
  }

  private void seedWorkspaceMembership(String actorId, String workspaceId) {
    jdbcTemplate.update(
        """
        insert into identity_users (
          user_id, display_name, email, avatar_initials, status, is_seeded, created_at, updated_at
        ) values (?, ?, ?, ?, 'active', false, current_timestamp, current_timestamp)
        """,
        actorId,
        actorId,
        actorId + "@example.test",
        "TS");
    jdbcTemplate.update(
        """
        insert into workspaces (
          workspace_id, name, slug, status, is_seeded, created_at, updated_at
        ) values (?, ?, ?, 'active', false, current_timestamp, current_timestamp)
        """,
        workspaceId,
        workspaceId,
        workspaceId);
    jdbcTemplate.update(
        """
        insert into workspace_memberships (
          workspace_id, user_id, role, is_default, created_at, updated_at
        ) values (?, ?, 'WORKSPACE_ADMIN', false, current_timestamp, current_timestamp)
        """,
        workspaceId,
        actorId);
  }

  private void seedExistingWorkspaceMembership(String actorId, String workspaceId, String role) {
    jdbcTemplate.update(
        """
        insert into identity_users (
          user_id, display_name, email, avatar_initials, status, is_seeded, created_at, updated_at
        ) values (?, ?, ?, ?, 'active', false, current_timestamp, current_timestamp)
        """,
        actorId,
        actorId,
        actorId + "@example.test",
        "TS");
    jdbcTemplate.update(
        """
        insert into workspace_memberships (
          workspace_id, user_id, role, is_default, created_at, updated_at
        ) values (?, ?, ?, false, current_timestamp, current_timestamp)
        """,
        workspaceId,
        actorId,
        role);
  }

  private String uploadAndGetFileId(String actorId, String workspaceId, String filename)
      throws Exception {
    HttpResponse<String> response =
        httpClient.send(
            uploadRequest(filename, "application/pdf", SyntheticDriveFiles.pdf())
                .header(PlaceholderAuthenticationContext.ACTOR_HEADER, actorId)
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, workspaceId)
                .build(),
            BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(201);
    return jdbcTemplate.queryForObject(
        "select file_id from drive_files where workspace_id = ? and owner_id = ? order by"
            + " created_at desc limit 1",
        String.class,
        workspaceId,
        actorId);
  }

  private HttpRequest requestForFile(String fileId, String actorId, String workspaceId) {
    return HttpRequest.newBuilder(uri("/api/drive/files/" + fileId))
        .header(PlaceholderAuthenticationContext.ACTOR_HEADER, actorId)
        .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, workspaceId)
        .GET()
        .build();
  }

  private void seedGrant(String fileId, String granteeUserId, String grantId) {
    String workspaceId = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    jdbcTemplate.update(
        """
        insert into drive_file_user_grants (
          grant_id, workspace_id, file_id, grantee_user_id, action, granted_by_user_id,
          granted_at, revoked_by_user_id, revoked_at, created_at, updated_at
        ) values (?, ?, ?, ?, 'file:view', ?, current_timestamp, null, null, current_timestamp, current_timestamp)
        """,
        grantId,
        workspaceId,
        fileId,
        granteeUserId,
        PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID);
  }

  @Test
  void rejectsEmptyUpload() throws Exception {
    HttpResponse<String> response =
        httpClient.send(
            uploadRequest("empty.pdf", "application/pdf", new byte[0]).build(),
            BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("\"error\":\"BAD_REQUEST\"");
    assertThat(objectStorage.objectCount()).isZero();
  }

  @Test
  void rejectsBlankAndPathUploadNamesBeforeStorage() throws Exception {
    for (String name : new String[] {" ", "../synthetic.txt", "a/b.txt", "..", "x".repeat(256)}) {
      var response =
          httpClient.send(
              uploadRequest(name, "text/plain", "synthetic".getBytes()).build(),
              BodyHandlers.ofString());
      assertThat(response.statusCode()).as(name).isEqualTo(400);
    }
    assertThat(objectStorage.objectCount()).isZero();
    assertThat(jdbcTemplate.queryForObject("select count(*) from drive_files", Integer.class))
        .isZero();
  }

  @Test
  void rejectsUnsupportedContentType() throws Exception {
    HttpResponse<String> response =
        httpClient.send(
            uploadRequest("script.sh", "application/x-sh", "echo hi".getBytes()).build(),
            BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("\"contentType\":\"application/x-sh\"");
    assertThat(objectStorage.objectCount()).isZero();
  }

  private HttpRequest.Builder uploadRequest(String filename, String contentType, byte[] fileContent)
      throws Exception {
    return HttpRequest.newBuilder(uri("/api/drive/files"))
        .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
        .POST(
            HttpRequest.BodyPublishers.ofByteArray(
                multipartBody(filename, contentType, fileContent)));
  }

  private byte[] multipartBody(String filename, String contentType, byte[] fileContent)
      throws Exception {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    output.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
    output.write(
        ("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n")
            .getBytes(StandardCharsets.UTF_8));
    output.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    output.write(fileContent);
    output.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return output.toByteArray();
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  @Test
  void originalDownloadBytesMatchUpload() throws Exception {
    for (String type :
        new String[] {
          "application/pdf", "image/png", "image/jpeg", "text/plain", SyntheticDriveFiles.TYPES[3]
        }) {
      byte[] original =
          type.equals("text/plain")
              ? "synthetic text\n".getBytes(StandardCharsets.UTF_8)
              : SyntheticDriveFiles.bytes(type);
      String id = uploadContent("synthetic-" + type.hashCode(), type, original);
      var response = contentRequest(id, "download", null, null);
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo(original);
      assertThat(response.headers().firstValue("Content-Disposition").orElseThrow())
          .startsWith("attachment;");
      assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith(type);
      assertSafeContentHeaders(response);
    }
  }

  @Test
  void previewsPdfImagesAndText() throws Exception {
    for (String type : new String[] {"application/pdf", "image/png", "image/jpeg", "text/plain"}) {
      byte[] original =
          type.equals("text/plain")
              ? "synthetic text".getBytes(StandardCharsets.UTF_8)
              : SyntheticDriveFiles.bytes(type);
      String id = uploadContent("synthetic-preview-" + type.hashCode(), type, original);
      var response = contentRequest(id, "preview", null, null);
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("Content-Disposition").orElseThrow())
          .startsWith("inline;");
      assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith(type);
      if (!type.equals("application/pdf")) assertThat(response.body()).isEqualTo(original);
      assertSafeContentHeaders(response);
    }
  }

  @Test
  void previewDeniesUnsupportedOfficeType() throws Exception {
    String id =
        uploadContent(
            "synthetic.docx",
            SyntheticDriveFiles.TYPES[3],
            SyntheticDriveFiles.bytes(SyntheticDriveFiles.TYPES[3]));
    var response = contentRequest(id, "preview", null, null);
    assertThat(response.statusCode()).isEqualTo(415);
    assertThat(new String(response.body(), StandardCharsets.UTF_8))
        .doesNotContain("synthetic.docx", "word/document.xml");
  }

  @Test
  void legacyMislabeledOrCorruptContentCannotPreview() throws Exception {
    String id =
        uploadContent(
            "synthetic-legacy.txt",
            "text/plain",
            "<script>synthetic()</script>".getBytes(StandardCharsets.UTF_8));
    jdbcTemplate.update(
        "update drive_files set content_type = 'application/pdf' where file_id = ?", id);
    assertThat(contentRequest(id, "preview", null, null).statusCode()).isEqualTo(422);
    assertThat(contentRequest(id, "download", null, null).statusCode()).isEqualTo(200);
    String key =
        jdbcTemplate.queryForObject(
            "select storage_key from drive_files where file_id = ?", String.class, id);
    objectStorage.objectBytes(key)[0] ^= 1;
    var corrupt = contentRequest(id, "preview", null, null);
    assertThat(corrupt.statusCode()).isEqualTo(500);
    assertThat(new String(corrupt.body(), StandardCharsets.UTF_8))
        .doesNotContain("synthetic-legacy", key, "AEAD", "tag");
  }

  @Test
  void authenticatedLegacyCorruptFormatsCannotPreviewButDownloadUnchanged() throws Exception {
    for (String type : new String[] {"application/pdf", "image/png", "image/jpeg"}) {
      String id =
          uploadContent(
              "synthetic-corrupt-" + type.hashCode(), type, SyntheticDriveFiles.bytes(type));
      byte[] corrupt = java.util.Arrays.copyOf(SyntheticDriveFiles.bytes(type), 12);
      var encrypted = encryption.encryptBytes(corrupt);
      String key =
          jdbcTemplate.queryForObject(
              "select storage_key from drive_files where file_id = ?", String.class, id);
      objectStorage.putEncryptedObject(key, encrypted.ciphertext(), type, encrypted.ivBase64());
      jdbcTemplate.update(
          "update drive_files set size_bytes = ?, checksum_sha256 = ?, content_iv = ? where file_id"
              + " = ?",
          corrupt.length,
          java.util.HexFormat.of()
              .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(corrupt)),
          encrypted.ivBase64(),
          id);
      var preview = contentRequest(id, "preview", null, null);
      assertThat(preview.statusCode()).isEqualTo(422);
      assertThat(new String(preview.body(), StandardCharsets.UTF_8))
          .doesNotContain(key, "synthetic-corrupt", "EOF", "PDFBox");
      assertThat(contentRequest(id, "download", null, null).body()).isEqualTo(corrupt);
    }
  }

  @Test
  void pdfPreviewPreservesPageAppearanceAndRejectsExcessiveRendering() throws Exception {
    byte[] original;
    try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument()) {
      for (int i = 0; i < 2; i++) {
        var page =
            new org.apache.pdfbox.pdmodel.PDPage(
                new org.apache.pdfbox.pdmodel.common.PDRectangle(50, 30));
        if (i == 1) page.setRotation(90);
        pdf.addPage(page);
        try (var stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf, page)) {
          stream.setNonStrokingColor(java.awt.Color.RED);
          stream.addRect(0, 0, 50, 30);
          stream.fill();
        }
      }
      var out = new ByteArrayOutputStream();
      pdf.save(out);
      original = out.toByteArray();
    }
    String id = uploadContent("synthetic-visual.pdf", "application/pdf", original);
    try (var pdf =
        org.apache.pdfbox.Loader.loadPDF(contentRequest(id, "preview", null, null).body())) {
      assertThat(pdf.getNumberOfPages()).isEqualTo(2);
      var renderer = new org.apache.pdfbox.rendering.PDFRenderer(pdf);
      for (int i = 0; i < 2; i++) {
        var image = renderer.renderImageWithDPI(i, 72);
        assertThat(image.getWidth()).isEqualTo(i == 0 ? 50 : 30);
        assertThat(image.getHeight()).isEqualTo(i == 0 ? 30 : 50);
        var pixel = new java.awt.Color(image.getRGB(10, 10));
        assertThat(pixel.getRed()).isGreaterThan(240);
        assertThat(pixel.getGreen()).isLessThan(10);
        assertThat(pixel.getBlue()).isLessThan(10);
      }
    }
    for (int count : new int[] {1, 41}) {
      try (var pdf = new org.apache.pdfbox.pdmodel.PDDocument()) {
        for (int i = 0; i < count; i++)
          pdf.addPage(
              new org.apache.pdfbox.pdmodel.PDPage(
                  new org.apache.pdfbox.pdmodel.common.PDRectangle(
                      count == 1 ? 3000 : 10, count == 1 ? 3000 : 10)));
        var out = new ByteArrayOutputStream();
        pdf.save(out);
        String large =
            uploadContent(
                "synthetic-render-limit-" + count + ".pdf", "application/pdf", out.toByteArray());
        assertThat(contentRequest(large, "preview", null, null).statusCode()).isEqualTo(422);
        assertThat(contentRequest(large, "download", null, null).body())
            .isEqualTo(out.toByteArray());
      }
    }
  }

  @Test
  void previewCannotExecuteActiveContent() throws Exception {
    byte[] activePdf;
    try (var document = org.apache.pdfbox.Loader.loadPDF(SyntheticDriveFiles.pdf())) {
      var script =
          new org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript(
              "app.alert('synthetic-active')");
      document.getDocumentCatalog().setOpenAction(script);
      var link = new org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink();
      link.setAction(script);
      document.getPage(0).getAnnotations().add(link);
      var out = new ByteArrayOutputStream();
      document.save(out);
      activePdf = out.toByteArray();
    }
    String id = uploadContent("synthetic-active.pdf", "application/pdf", activePdf);
    var response = contentRequest(id, "preview", null, null);
    assertThat(response.statusCode()).isEqualTo(200);
    try (var preview = org.apache.pdfbox.Loader.loadPDF(response.body())) {
      assertThat(preview.getDocumentCatalog().getOpenAction()).isNull();
      assertThat(preview.getDocumentCatalog().getNames()).isNull();
      assertThat(preview.getDocumentCatalog().getAcroForm()).isNull();
      assertThat(preview.getNumberOfPages()).isEqualTo(1);
      assertThat(preview.getPage(0).getAnnotations()).isEmpty();
      assertThat(preview.getPage(0).getCOSObject().containsKey(org.apache.pdfbox.cos.COSName.AA))
          .isFalse();
      assertThat(preview.getPage(0).getResources().getXObjectNames()).isNotEmpty();
    }
    assertThat(contentRequest(id, "download", null, null).body()).isEqualTo(activePdf);
    String textId =
        uploadContent(
            "synthetic-html.txt",
            "text/plain",
            "<script>alert('synthetic')</script>".getBytes(StandardCharsets.UTF_8));
    var text = contentRequest(textId, "preview", null, null);
    assertThat(text.headers().firstValue("Content-Type").orElseThrow())
        .isEqualTo("text/plain;charset=UTF-8");
    assertThat(text.headers().firstValue("Content-Security-Policy").orElseThrow())
        .contains("sandbox", "default-src 'none'");
    assertSafeContentHeaders(text);
  }

  @Test
  void viewerWithoutFileViewCannotFetchBytes() throws Exception {
    String workspace = PlaceholderAuthenticationContext.DEFAULT_WORKSPACE_ID;
    seedExistingWorkspaceMembership("usr_content_viewer", workspace, "VIEWER");
    seedWorkspaceMembership("usr_content_foreign", "wrk_content_foreign");
    String id =
        uploadContent(
            "synthetic-private.txt",
            "text/plain",
            "synthetic private".getBytes(StandardCharsets.UTF_8));
    for (String route : new String[] {"download", "preview"}) {
      for (String[] identity :
          new String[][] {
            {"usr_content_viewer", workspace}, {"usr_content_foreign", "wrk_content_foreign"}
          }) {
        var denied = contentRequest(id, route, identity[0], identity[1]);
        assertThat(denied.statusCode()).isEqualTo(404);
        assertThat(new String(denied.body(), StandardCharsets.UTF_8))
            .doesNotContain("synthetic-private", "synthetic private");
      }
    }
    seedGrant(id, "usr_content_viewer", "grant_content_viewer");
    assertThat(contentRequest(id, "download", "usr_content_viewer", workspace).statusCode())
        .isEqualTo(200);
    jdbcTemplate.update(
        "update drive_file_user_grants set revoked_at = current_timestamp, revoked_by_user_id = ?"
            + " where file_id = ?",
        PlaceholderAuthenticationContext.DEFAULT_ACTOR_ID,
        id);
    assertThat(contentRequest(id, "download", "usr_content_viewer", workspace).statusCode())
        .isEqualTo(404);
  }

  @Test
  void legacyFilenameCannotInjectResponseHeaders() throws Exception {
    String id =
        uploadContent(
            "synthetic-safe.txt", "text/plain", "synthetic".getBytes(StandardCharsets.UTF_8));
    var name = encryption.encryptText("../synthetic\r\nX-Injected: true\"\\.txt");
    jdbcTemplate.update(
        "update drive_files set encrypted_name = ?, name_iv = ? where file_id = ?",
        name.ciphertextBase64(),
        name.ivBase64(),
        id);
    var response = contentRequest(id, "download", null, null);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("X-Injected")).isEmpty();
    assertThat(response.headers().firstValue("Content-Disposition").orElseThrow())
        .doesNotContain("\r", "\n", "../", "X-Injected");
  }

  private String uploadContent(String name, String type, byte[] bytes) throws Exception {
    var uploaded =
        httpClient.send(uploadRequest(name, type, bytes).build(), BodyHandlers.ofString());
    assertThat(uploaded.statusCode()).isEqualTo(201);
    return jdbcTemplate.queryForObject(
        "select file_id from drive_files order by created_at desc limit 1", String.class);
  }

  private HttpResponse<byte[]> contentRequest(
      String id, String route, String actor, String workspace) throws Exception {
    var request = HttpRequest.newBuilder(uri("/api/drive/files/" + id + "/" + route));
    if (actor != null) request.header(PlaceholderAuthenticationContext.ACTOR_HEADER, actor);
    if (workspace != null)
      request.header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, workspace);
    return httpClient.send(request.GET().build(), BodyHandlers.ofByteArray());
  }

  private void assertSafeContentHeaders(HttpResponse<byte[]> response) {
    assertThat(response.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
    assertThat(response.headers().firstValue("X-Content-Type-Options").orElseThrow())
        .isEqualTo("nosniff");
    assertThat(response.headers().firstValue("Content-Security-Policy").orElseThrow())
        .contains("sandbox", "default-src 'none'");
  }

  @TestConfiguration
  static class DriveFileControllerTestConfiguration {

    @Bean
    @Primary
    FakeFileObjectStorage fakeFileObjectStorage() {
      return new FakeFileObjectStorage();
    }
  }

  static class FakeFileObjectStorage implements FileObjectStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public byte[] getEncryptedObject(String storageKey, long maxCiphertextBytes) {
      byte[] bytes = objects.get(storageKey);
      if (bytes == null || bytes.length > maxCiphertextBytes)
        throw new IllegalStateException("Synthetic object unavailable");
      return bytes.clone();
    }

    @Override
    public void putEncryptedObject(
        String storageKey, byte[] encryptedContent, String originalContentType, String contentIv) {
      objects.put(storageKey, encryptedContent);
    }

    @Override
    public void deleteObjectIfExists(String storageKey) {
      objects.remove(storageKey);
    }

    byte[] objectBytes(String storageKey) {
      return objects.get(storageKey);
    }

    int objectCount() {
      return objects.size();
    }

    void clear() {
      objects.clear();
    }
  }
}
