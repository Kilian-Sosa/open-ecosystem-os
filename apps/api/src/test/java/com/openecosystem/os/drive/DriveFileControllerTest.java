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

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from event_outbox");
    jdbcTemplate.update("delete from audit_records");
    jdbcTemplate.update("delete from drive_files");
    objectStorage.clear();
  }

  @Test
  void uploadStoresEncryptedObjectMetadataAuditAndFileUploadedOutboxEvent() throws Exception {
    byte[] plaintext = "%PDF-1.7 fake invoice".getBytes(StandardCharsets.UTF_8);

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
    assertThat(response.body()).contains("\"sizeBytes\":21");
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
            uploadRequest(
                    "workspace-a.pdf",
                    "application/pdf",
                    "%PDF-1.7 workspace A".getBytes(StandardCharsets.UTF_8))
                .header(PlaceholderAuthenticationContext.ACTOR_HEADER, "usr_workspace_a")
                .header(PlaceholderAuthenticationContext.WORKSPACE_HEADER, "wrk_workspace_a")
                .build(),
            BodyHandlers.ofString());
    HttpResponse<String> workspaceBUpload =
        httpClient.send(
            uploadRequest(
                    "workspace-b.pdf",
                    "application/pdf",
                    "%PDF-1.7 workspace B".getBytes(StandardCharsets.UTF_8))
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
            uploadRequest(
                    "private.pdf",
                    "application/pdf",
                    "%PDF-1.7 private".getBytes(StandardCharsets.UTF_8))
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
            uploadRequest(filename, "application/pdf", ("%PDF-1.7 " + filename).getBytes())
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
