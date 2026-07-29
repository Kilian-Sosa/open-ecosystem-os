package com.openecosystem.os.drive;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DriveFileRepository {

  private static final String METADATA_COLUMNS =
      """
      file_id, workspace_id, owner_id, visibility, encrypted_name, content_type, size_bytes,
      checksum_sha256, storage_key, encryption_algorithm, encryption_key_id, content_iv, name_iv,
      created_at, updated_at
      """;
  private static final RowMapper<DriveFileMetadata> ROW_MAPPER = DriveFileRepository::mapRow;

  private final JdbcTemplate jdbcTemplate;

  public DriveFileRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void save(DriveFileMetadata metadata) {
    jdbcTemplate.update(
        """
        insert into drive_files (
          file_id,
          workspace_id,
          owner_id,
          visibility,
          encrypted_name,
          content_type,
          size_bytes,
          checksum_sha256,
          storage_key,
          encryption_algorithm,
          encryption_key_id,
          content_iv,
          name_iv,
          created_at,
          updated_at
        ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        metadata.fileId(),
        metadata.workspaceId(),
        metadata.ownerId(),
        metadata.visibility().value(),
        metadata.encryptedName(),
        metadata.contentType(),
        metadata.sizeBytes(),
        metadata.checksumSha256(),
        metadata.storageKey(),
        metadata.encryptionAlgorithm(),
        metadata.encryptionKeyId(),
        metadata.contentIv(),
        metadata.nameIv(),
        Timestamp.from(metadata.createdAt()),
        Timestamp.from(metadata.updatedAt()));
  }

  public List<DriveFileMetadata> listByWorkspace(String workspaceId) {
    return jdbcTemplate.query(
        "select "
            + METADATA_COLUMNS
            + " from drive_files where workspace_id = ? order by created_at desc",
        ROW_MAPPER,
        workspaceId);
  }

  public List<String> listIdsByWorkspace(String workspaceId) {
    return jdbcTemplate.queryForList(
        "select file_id from drive_files where workspace_id = ? order by created_at desc",
        String.class,
        workspaceId);
  }

  public List<DriveFileMetadata> listByIdsForWorkspace(
      String workspaceId, Collection<String> fileIds) {
    if (fileIds.isEmpty()) {
      return List.of();
    }
    String placeholders = String.join(", ", Collections.nCopies(fileIds.size(), "?"));
    Object[] arguments = new Object[fileIds.size() + 1];
    arguments[0] = workspaceId;
    int index = 1;
    for (String fileId : fileIds) {
      arguments[index++] = fileId;
    }
    return jdbcTemplate.query(
        "select "
            + METADATA_COLUMNS
            + " from drive_files where workspace_id = ? and file_id in ("
            + placeholders
            + ") order by created_at desc",
        ROW_MAPPER,
        arguments);
  }

  public boolean updateVisibility(
      String fileId, String workspaceId, DriveFileVisibility visibility, Instant updatedAt) {
    return jdbcTemplate.update(
            """
            update drive_files
            set visibility = ?, updated_at = ?
            where file_id = ? and workspace_id = ?
            """,
            visibility.value(),
            Timestamp.from(updatedAt),
            fileId,
            workspaceId)
        > 0;
  }

  public Optional<DriveFileMetadata> findByIdForWorkspace(String fileId, String workspaceId) {
    List<DriveFileMetadata> results =
        jdbcTemplate.query(
            "select "
                + METADATA_COLUMNS
                + " from drive_files where file_id = ? and workspace_id = ?",
            ROW_MAPPER,
            fileId,
            workspaceId);
    return results.stream().findFirst();
  }

  private static DriveFileMetadata mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
    return new DriveFileMetadata(
        resultSet.getString("file_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("owner_id"),
        DriveFileVisibility.fromValue(resultSet.getString("visibility")),
        resultSet.getString("encrypted_name"),
        resultSet.getString("content_type"),
        resultSet.getLong("size_bytes"),
        resultSet.getString("checksum_sha256"),
        resultSet.getString("storage_key"),
        resultSet.getString("encryption_algorithm"),
        resultSet.getString("encryption_key_id"),
        resultSet.getString("content_iv"),
        resultSet.getString("name_iv"),
        resultSet.getTimestamp("created_at").toInstant(),
        resultSet.getTimestamp("updated_at").toInstant());
  }
}
