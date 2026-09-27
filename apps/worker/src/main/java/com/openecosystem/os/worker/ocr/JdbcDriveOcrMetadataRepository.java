package com.openecosystem.os.worker.ocr;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDriveOcrMetadataRepository implements DriveOcrMetadataRepository {

  private final JdbcTemplate jdbcTemplate;

  public JdbcDriveOcrMetadataRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<DriveOcrFileMetadata> findByFileIdAndWorkspaceId(
      String fileId, String workspaceId) {
    List<DriveOcrFileMetadata> results =
        jdbcTemplate.query(
            """
            select file_id, workspace_id, content_type, size_bytes, checksum_sha256,
                   storage_key, encryption_algorithm, encryption_key_id, content_iv
            from drive_files where file_id = ? and workspace_id = ?
            """,
            (resultSet, rowNumber) ->
                new DriveOcrFileMetadata(
                    resultSet.getString("file_id"),
                    resultSet.getString("workspace_id"),
                    resultSet.getString("content_type"),
                    resultSet.getLong("size_bytes"),
                    resultSet.getString("checksum_sha256"),
                    resultSet.getString("storage_key"),
                    resultSet.getString("encryption_algorithm"),
                    resultSet.getString("encryption_key_id"),
                    resultSet.getString("content_iv")),
            fileId,
            workspaceId);
    return results.stream().findFirst();
  }
}
