package com.openecosystem.os.worker.ocr;

import com.openecosystem.os.worker.drive.DriveLifecycleFence;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDriveOcrMetadataRepository implements DriveOcrMetadataRepository {

  private final JdbcTemplate jdbcTemplate;
  private final DriveLifecycleFence lifecycle;

  public JdbcDriveOcrMetadataRepository(JdbcTemplate jdbcTemplate, DriveLifecycleFence lifecycle) {
    this.jdbcTemplate = jdbcTemplate;
    this.lifecycle = lifecycle;
  }

  @Override
  public Optional<DriveOcrFileMetadata> findByFileIdAndWorkspaceId(
      String fileId, String workspaceId) {
    if (!lifecycle.isActiveFile(workspaceId, fileId)) return Optional.empty();
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
