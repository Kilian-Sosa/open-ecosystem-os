package com.openecosystem.os.worker.drive;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DriveLifecycleFence {
  private final JdbcTemplate jdbc;

  public DriveLifecycleFence(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public boolean lockActiveFile(String workspaceId, String fileId) {
    var workspaces =
        jdbc.queryForList(
            "select workspace_id from workspaces where workspace_id = ? for update",
            String.class,
            workspaceId);
    return !workspaces.isEmpty() && isActiveFile(workspaceId, fileId);
  }

  public boolean isActiveFile(String workspaceId, String fileId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
            with recursive active_folders(folder_id, workspace_id) as (
              select folder_id, workspace_id from drive_folders
                where parent_folder_id is null and deletion_group_id is null
              union all
              select child.folder_id, child.workspace_id from drive_folders child
                join active_folders parent on child.parent_folder_id = parent.folder_id
                  and child.workspace_id = parent.workspace_id
                where child.deletion_group_id is null
            ) select exists (select 1 from drive_files f
              where f.workspace_id = ? and f.file_id = ? and f.deletion_group_id is null
              and (f.folder_id is null or f.folder_id in (
                select folder_id from active_folders where workspace_id = f.workspace_id)))
            """,
            Boolean.class,
            workspaceId,
            fileId));
  }
}
