package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.OpenEcosystemApiApplication;
import com.openecosystem.os.common.security.AuthenticatedPrincipal;
import com.openecosystem.os.common.security.DefaultResourceAuthorizationService;
import com.openecosystem.os.common.security.ResourceAction;
import com.openecosystem.os.common.security.ResourceAuthorizationService;
import com.openecosystem.os.common.security.ResourceType;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(classes = OpenEcosystemApiApplication.class)
class FileViewAuthorizationRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");
  private static final String WORKSPACE_ID = "wrk_auth";
  private static final String OTHER_WORKSPACE_ID = "wrk_auth_other";
  private static final String OWNER_ID = "usr_auth_owner";

  @Autowired private FileViewAuthorizationRepository repository;
  @Autowired private JdbcTemplate jdbcTemplate;

  private ResourceAuthorizationService service;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("delete from drive_file_user_grants where workspace_id like 'wrk_auth%'");
    jdbcTemplate.update("delete from drive_files where workspace_id like 'wrk_auth%'");
    jdbcTemplate.update("delete from workspace_memberships where workspace_id like 'wrk_auth%'");
    jdbcTemplate.update("delete from identity_users where user_id like 'usr_auth%'");
    jdbcTemplate.update("delete from workspaces where workspace_id like 'wrk_auth%'");
    service = new DefaultResourceAuthorizationService(repository);
  }

  @Test
  void preservesSingularAndBatchParityForEveryRoleVisibilityAndGrantState() {
    insertWorkspace(WORKSPACE_ID, "active");
    insertActiveMember(WORKSPACE_ID, OWNER_ID, "VIEWER");
    insertFile("file_auth_private", WORKSPACE_ID, OWNER_ID, "private");
    insertFile("file_auth_workspace", WORKSPACE_ID, OWNER_ID, "workspace");

    assertParity(OWNER_ID, "file_auth_private", true);
    assertParity(OWNER_ID, "file_auth_workspace", true);

    List<String> approvedRoles =
        List.of("INSTANCE_OWNER", "WORKSPACE_ADMIN", "DEVELOPER", "EDITOR", "VIEWER");
    List<String> deniedRoles = List.of("GUEST", "AUDITOR", "DEVELOPMENT_PLACEHOLDER");
    for (String role : approvedRoles) {
      insertActiveMember(WORKSPACE_ID, actorFor(role), role);
      assertParity(actorFor(role), "file_auth_private", false);
      assertParity(actorFor(role), "file_auth_workspace", true);
    }
    for (String role : deniedRoles) {
      insertActiveMember(WORKSPACE_ID, actorFor(role), role);
      assertParity(actorFor(role), "file_auth_private", false);
      assertParity(actorFor(role), "file_auth_workspace", false);
    }

    insertActiveMember(WORKSPACE_ID, "usr_auth_granted", "GUEST");
    insertGrant("grant_active", "file_auth_private", "usr_auth_granted", null);
    assertParity("usr_auth_granted", "file_auth_private", true);

    insertActiveMember(WORKSPACE_ID, "usr_auth_revoked", "AUDITOR");
    insertGrant("grant_revoked", "file_auth_private", "usr_auth_revoked", NOW.plusSeconds(1));
    assertParity("usr_auth_revoked", "file_auth_private", false);

    assertThat(
            service.allowedResourceIds(
                principal("usr_auth_granted"),
                WORKSPACE_ID,
                ResourceType.FILE,
                List.of("file_auth_private", "file_auth_workspace"),
                ResourceAction.VIEW))
        .containsExactly("file_auth_private");
  }

  @Test
  void returnsInactiveFactsForExistingFilesButNoFactsForMissingOrForeignFiles() {
    insertWorkspace(WORKSPACE_ID, "active");
    insertWorkspace(OTHER_WORKSPACE_ID, "active");
    insertActiveMember(WORKSPACE_ID, OWNER_ID, "VIEWER");
    insertActiveMember(WORKSPACE_ID, "usr_auth_disabled", "VIEWER");
    insertActiveMember(WORKSPACE_ID, "usr_auth_removed", "VIEWER");
    insertActiveMember(WORKSPACE_ID, "usr_auth_workspace_disabled", "VIEWER");
    insertFile("file_existing", WORKSPACE_ID, OWNER_ID, "workspace");
    insertFile("file_foreign", OTHER_WORKSPACE_ID, OWNER_ID, "workspace");
    insertGrant("grant_disabled", "file_existing", "usr_auth_disabled", null);
    insertGrant("grant_removed", "file_existing", "usr_auth_removed", null);
    insertGrant("grant_workspace_disabled", "file_existing", "usr_auth_workspace_disabled", null);

    jdbcTemplate.update(
        "update identity_users set status = 'disabled' where user_id = 'usr_auth_disabled'");
    jdbcTemplate.update(
        "delete from workspace_memberships where workspace_id = ? and user_id = ?",
        WORKSPACE_ID,
        "usr_auth_removed");
    jdbcTemplate.update(
        "update workspaces set status = 'disabled' where workspace_id = ?", WORKSPACE_ID);

    assertInactiveFacts("usr_auth_disabled", "file_existing");
    assertInactiveFacts("usr_auth_removed", "file_existing");
    assertInactiveFacts("usr_auth_workspace_disabled", "file_existing");
    assertThat(repository.findFacts("usr_auth_disabled", WORKSPACE_ID, "missing_file")).isEmpty();
    assertThat(repository.findFacts("usr_auth_disabled", WORKSPACE_ID, "file_foreign")).isEmpty();

    assertThat(
            service
                .decide(
                    principal("usr_auth_disabled"),
                    WORKSPACE_ID,
                    ResourceType.FILE,
                    "file_existing",
                    ResourceAction.VIEW)
                .code())
        .isEqualTo(
            com.openecosystem.os.common.security.AuthorizationDecisionCode
                .DENY_INACTIVE_MEMBERSHIP);
  }

  @Test
  void restrictsPolicyFactsToIdentifiersVisibilityAndPolicyBooleans() {
    assertThat(
            Arrays.stream(FileViewAuthorizationFacts.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
        .containsExactly(
            "fileId",
            "workspaceId",
            "ownerId",
            "visibility",
            "activeMember",
            "workspaceVisibilityRole",
            "activeUserGrant")
        .doesNotContain(
            "encryptedName",
            "storageKey",
            "checksumSha256",
            "contentType",
            "ocrText",
            "email",
            "displayName");
  }

  private void assertParity(String actorId, String fileId, boolean expectedAllowed) {
    AuthenticatedPrincipal principal = principal(actorId);
    boolean singularAllowed =
        service
            .decide(principal, WORKSPACE_ID, ResourceType.FILE, fileId, ResourceAction.VIEW)
            .allowed();
    Set<String> batchAllowed =
        service.allowedResourceIds(
            principal, WORKSPACE_ID, ResourceType.FILE, List.of(fileId), ResourceAction.VIEW);

    assertThat(singularAllowed).isEqualTo(expectedAllowed);
    assertThat(batchAllowed.contains(fileId)).isEqualTo(singularAllowed);
  }

  private void assertInactiveFacts(String actorId, String fileId) {
    assertThat(repository.findFacts(actorId, WORKSPACE_ID, fileId))
        .get()
        .satisfies(
            facts -> {
              assertThat(facts.activeMember()).isFalse();
              assertThat(facts.workspaceVisibilityRole()).isFalse();
              assertThat(facts.activeUserGrant()).isFalse();
            });
  }

  private AuthenticatedPrincipal principal(String actorId) {
    return new AuthenticatedPrincipal(actorId, WORKSPACE_ID, Set.of("VIEWER"), true);
  }

  private String actorFor(String role) {
    return "usr_auth_" + role.toLowerCase();
  }

  private void insertWorkspace(String workspaceId, String status) {
    jdbcTemplate.update(
        """
        insert into workspaces (
          workspace_id, name, slug, status, is_seeded, created_at, updated_at
        ) values (?, ?, ?, ?, false, ?, ?)
        """,
        workspaceId,
        workspaceId,
        workspaceId,
        status,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }

  private void insertActiveMember(String workspaceId, String userId, String role) {
    jdbcTemplate.update(
        """
        insert into identity_users (
          user_id, display_name, email, avatar_initials, status, is_seeded, created_at, updated_at
        ) values (?, ?, ?, 'AU', 'active', false, ?, ?)
        """,
        userId,
        userId,
        userId + "@example.test",
        Timestamp.from(NOW),
        Timestamp.from(NOW));
    jdbcTemplate.update(
        """
        insert into workspace_memberships (
          workspace_id, user_id, role, is_default, created_at, updated_at
        ) values (?, ?, ?, false, ?, ?)
        """,
        workspaceId,
        userId,
        role,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }

  private void insertFile(String fileId, String workspaceId, String ownerId, String visibility) {
    jdbcTemplate.update(
        """
        insert into drive_files (
          file_id, workspace_id, owner_id, visibility, encrypted_name, content_type, size_bytes,
          checksum_sha256, storage_key, encryption_algorithm, encryption_key_id,
          content_iv, name_iv, created_at, updated_at
        ) values (?, ?, ?, ?, 'encrypted-name', 'application/pdf', 10,
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', ?,
          'AES-256-GCM', 'test-key', 'content-iv', 'name-iv', ?, ?)
        """,
        fileId,
        workspaceId,
        ownerId,
        visibility,
        "workspaces/" + workspaceId + "/drive/" + fileId + "/original",
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }

  private void insertGrant(String grantId, String fileId, String granteeUserId, Instant revokedAt) {
    jdbcTemplate.update(
        """
        insert into drive_file_user_grants (
          grant_id, workspace_id, file_id, grantee_user_id, action, granted_by_user_id,
          granted_at, revoked_by_user_id, revoked_at, created_at, updated_at
        ) values (?, ?, ?, ?, 'file:view', ?, ?, ?, ?, ?, ?)
        """,
        grantId,
        WORKSPACE_ID,
        fileId,
        granteeUserId,
        OWNER_ID,
        Timestamp.from(NOW),
        revokedAt == null ? null : OWNER_ID,
        revokedAt == null ? null : Timestamp.from(revokedAt),
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }
}
