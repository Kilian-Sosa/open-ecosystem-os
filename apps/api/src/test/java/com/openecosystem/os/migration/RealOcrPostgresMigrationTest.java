package com.openecosystem.os.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RealOcrPostgresMigrationTest {

  private static final Instant NOW = Instant.parse("2026-07-29T12:00:00Z");

  @Container
  private static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine");

  @Test
  void upgradesRepresentativeV6RowsThroughAllOcrMigrationsWithoutDataLoss() {
    String schema = schemaName();
    migrate(schema, MigrationVersion.fromVersion("6"));
    JdbcTemplate jdbcTemplate = jdbcTemplate(schema);
    insertDriveFile(jdbcTemplate, "file_existing", "wrk_existing");
    insertOcrJob(jdbcTemplate, "ocr_existing", "file_existing", "wrk_existing");

    migrate(schema, null);

    assertThat(jdbcTemplate.queryForObject("select count(*) from drive_files", Integer.class))
        .isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject("select count(*) from ocr_jobs", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select status from ocr_jobs where job_id = 'ocr_existing'", String.class))
        .isEqualTo("completed");
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from workflow_versions where version_id = ?",
                Integer.class,
                "wfv_invoice_automation_v3"))
        .isEqualTo(1);
  }

  @Test
  void rejectsCompositeWorkspaceAndResultLineageMismatches() {
    String schema = schemaName();
    migrate(schema, null);
    JdbcTemplate jdbcTemplate = jdbcTemplate(schema);
    insertDriveFile(jdbcTemplate, "file_one", "wrk_one");
    insertDriveFile(jdbcTemplate, "file_two", "wrk_one");
    insertOcrJob(jdbcTemplate, "ocr_one", "file_one", "wrk_one");
    insertOcrJob(jdbcTemplate, "ocr_two", "file_two", "wrk_one");

    assertThatThrownBy(
            () -> insertOcrJob(jdbcTemplate, "ocr_wrong_workspace", "file_one", "wrk_two"))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(
            () ->
                insertResult(
                    jdbcTemplate, "ocrr_wrong_workspace", "ocr_one", "file_one", "wrk_two"))
        .isInstanceOf(DataIntegrityViolationException.class);

    insertResult(jdbcTemplate, "ocrr_one", "ocr_one", "file_one", "wrk_one");
    insertWorkflowExecution(jdbcTemplate, "wfe_one", "wrk_one");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    """
                    insert into invoice_extractions (
                      extraction_id, workflow_execution_id, ocr_result_id, job_id, file_id,
                      workspace_id, extractor_name, extractor_version, status, warnings_json,
                      aggregate_confidence, field_count, warning_count, created_at, updated_at
                    ) values ('invx_wrong_lineage', 'wfe_one', 'ocrr_one', 'ocr_two', 'file_one',
                      'wrk_one', 'heuristic', 'v1', 'completed', '[]', null, 0, 0, ?, ?)
                    """,
                    Timestamp.from(NOW),
                    Timestamp.from(NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void migratesDriveFilesToPrivateVisibilityAndEnforcesUserGrantIntegrity() {
    String schema = schemaName();
    migrate(schema, MigrationVersion.fromVersion("8"));
    JdbcTemplate jdbcTemplate = jdbcTemplate(schema);
    insertActiveWorkspaceMember(jdbcTemplate, "wrk_legacy", "usr_owner");
    insertActiveWorkspaceMember(jdbcTemplate, "wrk_legacy", "usr_grantee");
    insertActiveWorkspaceMember(jdbcTemplate, "wrk_other", "usr_other");
    insertDriveFile(jdbcTemplate, "file_legacy", "wrk_legacy");

    migrate(schema, null);

    assertThat(
            jdbcTemplate.queryForObject(
                "select visibility from drive_files where file_id = ?",
                String.class,
                "file_legacy"))
        .isEqualTo("private");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "update drive_files set visibility = 'public' where file_id = 'file_legacy'"))
        .isInstanceOf(DataIntegrityViolationException.class);

    insertViewGrant(jdbcTemplate, "grant_valid", "wrk_legacy", "file_legacy", "usr_grantee");

    assertThatThrownBy(
            () ->
                insertGrant(
                    jdbcTemplate,
                    "grant_invalid_action",
                    "wrk_legacy",
                    "file_legacy",
                    "usr_grantee",
                    "file:edit",
                    null,
                    null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                insertGrant(
                    jdbcTemplate,
                    "grant_invalid_revocation",
                    "wrk_legacy",
                    "file_legacy",
                    "usr_other",
                    "file:view",
                    null,
                    NOW))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                insertGrant(
                    jdbcTemplate,
                    "grant_invalid_revocation_actor",
                    "wrk_legacy",
                    "file_legacy",
                    "usr_other",
                    "file:view",
                    "usr_owner",
                    null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                insertGrant(
                    jdbcTemplate,
                    "grant_foreign_lineage",
                    "wrk_other",
                    "file_legacy",
                    "usr_other",
                    "file:view",
                    null,
                    null))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                insertViewGrant(
                    jdbcTemplate, "grant_duplicate", "wrk_legacy", "file_legacy", "usr_grantee"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(
            jdbcTemplate.queryForList(
                """
                select indexname
                from pg_indexes
                where schemaname = current_schema()
                  and indexname in (
                    'drive_files_workspace_owner_created_at_idx',
                    'drive_files_workspace_visibility_created_at_idx',
                    'drive_file_user_grants_grantee_lookup_idx',
                    'drive_file_user_grants_file_lookup_idx'
                  )
                """,
                String.class))
        .containsExactlyInAnyOrder(
            "drive_files_workspace_owner_created_at_idx",
            "drive_files_workspace_visibility_created_at_idx",
            "drive_file_user_grants_grantee_lookup_idx",
            "drive_file_user_grants_file_lookup_idx");
  }

  private void migrate(String schema, MigrationVersion target) {
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .schemas(schema)
        .defaultSchema(schema)
        .createSchemas(true)
        .locations("classpath:db/migration")
        .target(target)
        .load()
        .migrate();
  }

  private JdbcTemplate jdbcTemplate(String schema) {
    JdbcTemplate jdbcTemplate =
        new JdbcTemplate(
            new DriverManagerDataSource(
                POSTGRES.getJdbcUrl() + "&currentSchema=" + schema,
                POSTGRES.getUsername(),
                POSTGRES.getPassword()));
    return jdbcTemplate;
  }

  private void insertDriveFile(JdbcTemplate jdbcTemplate, String fileId, String workspaceId) {
    jdbcTemplate.update(
        """
        insert into drive_files (
          file_id, workspace_id, owner_id, encrypted_name, content_type, size_bytes,
          checksum_sha256, storage_key, encryption_algorithm, encryption_key_id,
          content_iv, name_iv, created_at, updated_at
        ) values (?, ?, 'usr_test', 'encrypted-name', 'application/pdf', 10,
          'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', ?,
          'AES-256-GCM', 'test-key', 'content-iv', 'name-iv', ?, ?)
        """,
        fileId,
        workspaceId,
        "workspaces/" + workspaceId + "/drive/" + fileId + "/original",
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }

  private void insertActiveWorkspaceMember(
      JdbcTemplate jdbcTemplate, String workspaceId, String userId) {
    jdbcTemplate.update(
        """
        insert into identity_users (
          user_id, display_name, email, avatar_initials, status, is_seeded, created_at, updated_at
        ) values (?, ?, ?, 'TU', 'active', false, ?, ?)
        """,
        userId,
        userId,
        userId + "@example.test",
        Timestamp.from(NOW),
        Timestamp.from(NOW));
    jdbcTemplate.update(
        """
        insert into workspaces (
          workspace_id, name, slug, status, is_seeded, created_at, updated_at
        ) values (?, ?, ?, 'active', false, ?, ?)
        on conflict (workspace_id) do nothing
        """,
        workspaceId,
        workspaceId,
        workspaceId,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
    jdbcTemplate.update(
        """
        insert into workspace_memberships (
          workspace_id, user_id, role, is_default, created_at, updated_at
        ) values (?, ?, 'VIEWER', true, ?, ?)
        """,
        workspaceId,
        userId,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }

  private void insertViewGrant(
      JdbcTemplate jdbcTemplate,
      String grantId,
      String workspaceId,
      String fileId,
      String granteeUserId) {
    insertGrant(jdbcTemplate, grantId, workspaceId, fileId, granteeUserId, "file:view", null, null);
  }

  private void insertGrant(
      JdbcTemplate jdbcTemplate,
      String grantId,
      String workspaceId,
      String fileId,
      String granteeUserId,
      String action,
      String revokedByUserId,
      Instant revokedAt) {
    jdbcTemplate.update(
        """
        insert into drive_file_user_grants (
          grant_id, workspace_id, file_id, grantee_user_id, action, granted_by_user_id,
          granted_at, revoked_by_user_id, revoked_at, created_at, updated_at
        ) values (?, ?, ?, ?, ?, 'usr_owner', ?, ?, ?, ?, ?)
        """,
        grantId,
        workspaceId,
        fileId,
        granteeUserId,
        action,
        Timestamp.from(NOW),
        revokedByUserId,
        revokedAt == null ? null : Timestamp.from(revokedAt),
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }

  private void insertOcrJob(
      JdbcTemplate jdbcTemplate, String jobId, String fileId, String workspaceId) {
    jdbcTemplate.update(
        """
        insert into ocr_jobs (
          job_id, file_id, workspace_id, actor_id, source_event_id, correlation_id,
          content_type, storage_key, status, provider, attempt_count, max_attempts,
          extracted_text, extracted_text_length, queued_at, processing_started_at, completed_at,
          created_at, updated_at
        ) values (?, ?, ?, 'usr_test', 'evt_test', 'corr_test', 'application/pdf', ?,
          'completed', 'tesseract', 1, 3, 'test text', 9, ?, ?, ?, ?, ?)
        """,
        jobId,
        fileId,
        workspaceId,
        "workspaces/" + workspaceId + "/drive/" + fileId + "/original",
        Timestamp.from(NOW),
        Timestamp.from(NOW),
        Timestamp.from(NOW),
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }

  private void insertResult(
      JdbcTemplate jdbcTemplate, String resultId, String jobId, String fileId, String workspaceId) {
    jdbcTemplate.update(
        """
        insert into ocr_results (
          ocr_result_id, job_id, file_id, workspace_id, provider, provider_version,
          document_text, page_count, word_count, created_at, updated_at
        ) values (?, ?, ?, ?, 'tesseract', '5.5.1', 'test text', 1, 0, ?, ?)
        """,
        resultId,
        jobId,
        fileId,
        workspaceId,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }

  private void insertWorkflowExecution(
      JdbcTemplate jdbcTemplate, String executionId, String workspaceId) {
    jdbcTemplate.update(
        """
        insert into workflows (
          workflow_id, workspace_id, name, description, status, current_version_id,
          current_version_number, created_by, updated_by, created_at, updated_at
        ) values ('flow_one', ?, 'Workflow', 'test', 'active', null, 1, 'usr_test', 'usr_test', ?, ?)
        """,
        workspaceId,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
    jdbcTemplate.update(
        """
        insert into workflow_versions (
          version_id, workflow_id, workspace_id, version_number, definition_json,
          created_by, created_at, published_at
        ) values ('wfv_one', 'flow_one', ?, 1, '{"trigger":{"type":"event"},"steps":[]}',
          'usr_test', ?, ?)
        """,
        workspaceId,
        Timestamp.from(NOW),
        Timestamp.from(NOW));
    jdbcTemplate.update(
        "update workflows set current_version_id = 'wfv_one' where workflow_id = 'flow_one'");
    jdbcTemplate.update(
        """
        insert into workflow_executions (
          execution_id, workflow_id, workflow_version_id, workflow_version_number, workspace_id,
          actor_id, correlation_id, trigger_type, source_event_id, source_event_type,
          trigger_idempotency_key, status, retry_count, started_at, completed_at, created_at, updated_at
        ) values (?, 'flow_one', 'wfv_one', 1, ?, 'usr_test', 'corr_test', 'event', 'evt_test',
          'OcrCompleted', 'workflow:test', 'completed', 0, ?, ?, ?, ?)
        """,
        executionId,
        workspaceId,
        Timestamp.from(NOW),
        Timestamp.from(NOW),
        Timestamp.from(NOW),
        Timestamp.from(NOW));
  }

  private String schemaName() {
    return "ocr_verify_" + UUID.randomUUID().toString().replace("-", "");
  }
}
