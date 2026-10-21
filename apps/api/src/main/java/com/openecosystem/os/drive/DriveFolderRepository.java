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

@Repository
public class DriveFolderRepository {

  private static final String METADATA_COLUMNS =
      """
      folder_id, workspace_id, owner_id, parent_folder_id, encrypted_name, name_iv,
      created_at, updated_at
      """;
  private static final RowMapper<DriveFolderMetadata> ROW_MAPPER = DriveFolderRepository::mapRow;
  private final JdbcTemplate jdbcTemplate;

  public DriveFolderRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void save(DriveFolderMetadata metadata) {
    jdbcTemplate.update(
        """
        insert into drive_folders (
          folder_id, workspace_id, owner_id, parent_folder_id, encrypted_name, name_iv,
          created_at, updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        metadata.folderId(),
        metadata.workspaceId(),
        metadata.ownerId(),
        metadata.parentFolderId(),
        metadata.encryptedName(),
        metadata.nameIv(),
        Timestamp.from(metadata.createdAt()),
        Timestamp.from(metadata.updatedAt()));
  }

  public Optional<DriveFolderMetadata> findByIdForWorkspace(String folderId, String workspaceId) {
    return jdbcTemplate
        .query(
            "select "
                + METADATA_COLUMNS
                + " from drive_folders where folder_id = ? and workspace_id = ?",
            ROW_MAPPER,
            folderId,
            workspaceId)
        .stream()
        .findFirst();
  }

  public List<DriveFolderMetadata> listChildren(String workspaceId, String parentId) {
    return listChildren(workspaceId, parentId, Integer.MAX_VALUE);
  }

  public List<DriveFolderMetadata> listChildren(String workspaceId, String parentId, int limit) {
    String query =
        "select "
            + METADATA_COLUMNS
            + " from drive_folders where workspace_id = ? and parent_folder_id "
            + (parentId == null ? "is null" : "= ?")
            + " order by created_at, folder_id limit ?";
    return parentId == null
        ? jdbcTemplate.query(query, ROW_MAPPER, workspaceId, limit)
        : jdbcTemplate.query(query, ROW_MAPPER, workspaceId, parentId, limit);
  }

  // The caller must retain the surrounding transaction through the directory mutation.
  public void lockWorkspaceForMutation(String workspaceId) {
    jdbcTemplate.queryForObject(
        "select workspace_id from workspaces where workspace_id = ? for update",
        String.class,
        workspaceId);
  }

  public boolean isActiveMember(String workspaceId, String actorId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            """
            select exists (
              select 1 from workspace_memberships membership
              join identity_users actor on actor.user_id = membership.user_id
              join workspaces workspace on workspace.workspace_id = membership.workspace_id
              where membership.workspace_id = ? and membership.user_id = ?
                and actor.status = 'active' and workspace.status = 'active'
            )
            """,
            Boolean.class,
            workspaceId,
            actorId));
  }

  public boolean updateName(
      String workspaceId, String folderId, String encryptedName, String nameIv, Instant when) {
    return jdbcTemplate.update(
            """
            update drive_folders set encrypted_name = ?, name_iv = ?, updated_at = ?
            where workspace_id = ? and folder_id = ?
            """,
            encryptedName,
            nameIv,
            Timestamp.from(when),
            workspaceId,
            folderId)
        > 0;
  }

  public boolean updateParent(String workspaceId, String folderId, String parentId, Instant when) {
    return jdbcTemplate.update(
            """
            update drive_folders set parent_folder_id = ?, updated_at = ?
            where workspace_id = ? and folder_id = ?
            """,
            parentId,
            Timestamp.from(when),
            workspaceId,
            folderId)
        > 0;
  }

  private static DriveFolderMetadata mapRow(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new DriveFolderMetadata(
        resultSet.getString("folder_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("owner_id"),
        resultSet.getString("parent_folder_id"),
        resultSet.getString("encrypted_name"),
        resultSet.getString("name_iv"),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant());
  }
}
