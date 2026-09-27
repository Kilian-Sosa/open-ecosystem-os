package com.openecosystem.os.drive;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DriveFileUserGrantRepository {

  private static final String FILE_VIEW_ACTION = "file:view";
  private static final RowMapper<DriveFileUserGrant> ROW_MAPPER =
      DriveFileUserGrantRepository::mapRow;

  private final JdbcTemplate jdbcTemplate;

  public DriveFileUserGrantRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<DriveFileUserGrant> find(
      String workspaceId, String fileId, String granteeUserId, String action) {
    return jdbcTemplate
        .query(
            """
            select grant_id, workspace_id, file_id, grantee_user_id, action, granted_by_user_id,
                   granted_at, revoked_by_user_id, revoked_at, created_at, updated_at
            from drive_file_user_grants
            where workspace_id = ? and file_id = ? and grantee_user_id = ? and action = ?
            """,
            ROW_MAPPER,
            workspaceId,
            fileId,
            granteeUserId,
            action)
        .stream()
        .findFirst();
  }

  @Transactional
  public DriveFileUserGrant grantOrReactivateView(
      String grantId,
      String workspaceId,
      String fileId,
      String granteeUserId,
      String grantedByUserId,
      Instant grantedAt) {
    if (!hasActiveGrantLineage(workspaceId, fileId, granteeUserId, grantedByUserId)) {
      throw new IllegalStateException("Drive file grant requires active same-workspace members");
    }

    Timestamp timestamp = Timestamp.from(grantedAt);
    List<DriveFileUserGrant> grants =
        jdbcTemplate.query(
            """
            insert into drive_file_user_grants (
              grant_id, workspace_id, file_id, grantee_user_id, action, granted_by_user_id,
              granted_at, revoked_by_user_id, revoked_at, created_at, updated_at
            ) values (?, ?, ?, ?, 'file:view', ?, ?, null, null, ?, ?)
            on conflict on constraint drive_file_user_grants_target_unique
            do update set
              granted_by_user_id = excluded.granted_by_user_id,
              granted_at = excluded.granted_at,
              revoked_by_user_id = null,
              revoked_at = null,
              updated_at = excluded.updated_at
            returning grant_id, workspace_id, file_id, grantee_user_id, action, granted_by_user_id,
                      granted_at, revoked_by_user_id, revoked_at, created_at, updated_at
            """,
            ROW_MAPPER,
            grantId,
            workspaceId,
            fileId,
            granteeUserId,
            grantedByUserId,
            timestamp,
            timestamp,
            timestamp);
    return grants.getFirst();
  }

  @Transactional
  public Optional<DriveFileUserGrant> revokeView(
      String workspaceId,
      String fileId,
      String granteeUserId,
      String revokedByUserId,
      Instant revokedAt) {
    if (!hasActiveFileMember(workspaceId, fileId, revokedByUserId)) {
      return Optional.empty();
    }

    Timestamp timestamp = Timestamp.from(revokedAt);
    jdbcTemplate.update(
        """
        update drive_file_user_grants
        set revoked_by_user_id = ?, revoked_at = ?, updated_at = ?
        where workspace_id = ?
          and file_id = ?
          and grantee_user_id = ?
          and action = 'file:view'
          and revoked_at is null
        """,
        revokedByUserId,
        timestamp,
        timestamp,
        workspaceId,
        fileId,
        granteeUserId);
    return find(workspaceId, fileId, granteeUserId, FILE_VIEW_ACTION);
  }

  private boolean hasActiveGrantLineage(
      String workspaceId, String fileId, String granteeUserId, String grantedByUserId) {
    Boolean valid =
        jdbcTemplate.queryForObject(
            """
            select exists (
              select 1
              from drive_files f
              join workspaces w on w.workspace_id = f.workspace_id and w.status = 'active'
              where f.file_id = ?
                and f.workspace_id = ?
                and exists (
                  select 1
                  from identity_users grantee
                  where grantee.user_id = ?
                    and grantee.status = 'active'
                    and exists (
                      select 1
                      from workspace_memberships membership
                      where membership.workspace_id = f.workspace_id
                        and membership.user_id = grantee.user_id
                    )
                )
                and exists (
                  select 1
                  from identity_users grantor
                  where grantor.user_id = ?
                    and grantor.status = 'active'
                    and exists (
                      select 1
                      from workspace_memberships membership
                      where membership.workspace_id = f.workspace_id
                        and membership.user_id = grantor.user_id
                    )
                )
            )
            """,
            Boolean.class,
            fileId,
            workspaceId,
            granteeUserId,
            grantedByUserId);
    return Boolean.TRUE.equals(valid);
  }

  private boolean hasActiveFileMember(String workspaceId, String fileId, String userId) {
    Boolean activeMember =
        jdbcTemplate.queryForObject(
            """
            select exists (
              select 1
              from drive_files f
              join workspaces w on w.workspace_id = f.workspace_id and w.status = 'active'
              join identity_users u on u.user_id = ? and u.status = 'active'
              where f.file_id = ?
                and f.workspace_id = ?
                and exists (
                  select 1
                  from workspace_memberships membership
                  where membership.workspace_id = f.workspace_id and membership.user_id = u.user_id
                )
            )
            """,
            Boolean.class,
            userId,
            fileId,
            workspaceId);
    return Boolean.TRUE.equals(activeMember);
  }

  private static DriveFileUserGrant mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
    return new DriveFileUserGrant(
        resultSet.getString("grant_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("file_id"),
        resultSet.getString("grantee_user_id"),
        resultSet.getString("action"),
        resultSet.getString("granted_by_user_id"),
        resultSet.getTimestamp("granted_at").toInstant(),
        resultSet.getString("revoked_by_user_id"),
        toInstant(resultSet.getTimestamp("revoked_at")),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant());
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
