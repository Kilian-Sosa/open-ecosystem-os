package com.openecosystem.os.drive;

final class DriveLifecycleSql {
  private DriveLifecycleSql() {}

  static final String ACTIVE_FOLDERS =
      """
      with recursive active_folders(folder_id, workspace_id) as (
        select folder_id, workspace_id from drive_folders
          where parent_folder_id is null and deletion_group_id is null
        union all
        select child.folder_id, child.workspace_id from drive_folders child
          join active_folders parent on child.parent_folder_id = parent.folder_id
            and child.workspace_id = parent.workspace_id
          where child.deletion_group_id is null
      ) select folder_id from active_folders
      """;

  static String activeFile(String alias) {
    return alias
        + ".deletion_group_id is null and ("
        + alias
        + ".folder_id is null or "
        + alias
        + ".folder_id in ("
        + ACTIVE_FOLDERS
        + "))";
  }

  static String activeFolder(String alias) {
    return alias + ".folder_id in (" + ACTIVE_FOLDERS + ")";
  }
}
