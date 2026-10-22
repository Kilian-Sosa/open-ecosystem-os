package com.openecosystem.os.drive;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class DriveTrashRepository {
  private static final Duration LEASE = Duration.ofMinutes(5);
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;

  public DriveTrashRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
    transactions = new TransactionTemplate(new JdbcTransactionManager(jdbc.getDataSource()));
  }

  public void createGroup(DriveTrashGroup group) {
    jdbc.update(
        """
        insert into drive_trash_groups (group_id, workspace_id, root_item_type, root_item_id,
          original_parent_folder_id, actor_id, trashed_at, purge_after, state)
        values (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        group.groupId(),
        group.workspaceId(),
        group.rootItemType(),
        group.rootItemId(),
        group.originalParentFolderId(),
        group.actorId(),
        Timestamp.from(group.trashedAt()),
        Timestamp.from(group.purgeAfter()),
        group.state().name());
  }

  public List<DriveTrashGroup> findVisibleGroups(String workspaceId) {
    return jdbc.query(
        "select * from drive_trash_groups where workspace_id = ? and state in ('TRASHED',"
            + " 'PURGING') order by trashed_at desc, group_id",
        DriveTrashRepository::mapGroup,
        workspaceId);
  }

  public Optional<DriveTrashGroup> findByIdForWorkspace(String groupId, String workspaceId) {
    return jdbc
        .query(
            "select * from drive_trash_groups where group_id = ? and workspace_id = ?",
            DriveTrashRepository::mapGroup,
            groupId,
            workspaceId)
        .stream()
        .findFirst();
  }

  public Optional<DriveTrashGroup> lockGroup(String groupId, String workspaceId) {
    return jdbc
        .query(
            "select * from drive_trash_groups where group_id = ? and workspace_id = ? for update",
            DriveTrashRepository::mapGroup,
            groupId,
            workspaceId)
        .stream()
        .findFirst();
  }

  public boolean attachFile(String workspaceId, String fileId, String groupId) {
    return jdbc.update(
            """
            update drive_files set deletion_group_id = ?, original_folder_id = folder_id
            where workspace_id = ? and file_id = ? and deletion_group_id is null
              and exists (select 1 from drive_trash_groups g where g.group_id = ? and g.workspace_id = ? and g.state = 'TRASHED')
            """,
            groupId,
            workspaceId,
            fileId,
            groupId,
            workspaceId)
        == 1;
  }

  public boolean attachFolder(String workspaceId, String folderId, String groupId) {
    return jdbc.update(
            """
            update drive_folders set deletion_group_id = ?, original_parent_folder_id = parent_folder_id
            where workspace_id = ? and folder_id = ? and deletion_group_id is null
              and exists (select 1 from drive_trash_groups g where g.group_id = ? and g.workspace_id = ? and g.state = 'TRASHED')
            """,
            groupId,
            workspaceId,
            folderId,
            groupId,
            workspaceId)
        == 1;
  }

  public List<DriveTrashCleanupClaim> claimCleanupWork(Instant now, int maxGroups) {
    if (maxGroups <= 0) return List.of();
    var candidates =
        jdbc.query(
            """
            select * from drive_trash_groups where
              (state = 'TRASHED' and purge_after <= ?) or
              (state = 'PURGING' and (next_retry_at is null or next_retry_at <= ?)
                and (lease_until is null or lease_until <= ?))
            order by workspace_id, group_id limit ?
            """,
            DriveTrashRepository::mapGroup,
            Timestamp.from(now),
            Timestamp.from(now),
            Timestamp.from(now),
            maxGroups);
    List<DriveTrashCleanupClaim> claims = new ArrayList<>();
    for (var candidate : candidates)
      claim(candidate.groupId(), candidate.workspaceId(), now, false).ifPresent(claims::add);
    return List.copyOf(claims);
  }

  public Optional<DriveTrashCleanupClaim> claimForPurge(
      String groupId, String workspaceId, Instant now) {
    return claim(groupId, workspaceId, now, true);
  }

  private Optional<DriveTrashCleanupClaim> claim(
      String groupId, String workspaceId, Instant now, boolean manual) {
    return transactions.execute(
        status -> {
          new DriveFolderRepository(jdbc).lockWorkspaceForMutation(workspaceId);
          String token = UUID.randomUUID().toString();
          int changed =
              jdbc.update(
                  """
                  update drive_trash_groups set state = 'PURGING', claim_token = ?, lease_until = ?,
                    cleanup_attempt = cleanup_attempt + 1, next_retry_at = null, last_error_code = null
                  where group_id = ? and workspace_id = ? and
                    ((state = 'TRASHED' and (purge_after <= ? or ?)) or
                     (state = 'PURGING' and (next_retry_at is null or next_retry_at <= ?)
                      and (lease_until is null or lease_until <= ?)))
                  """,
                  token,
                  Timestamp.from(now.plus(LEASE)),
                  groupId,
                  workspaceId,
                  Timestamp.from(now),
                  manual,
                  Timestamp.from(now),
                  Timestamp.from(now));
          if (changed == 0) return Optional.empty();
          return jdbc
              .query(
                  "select * from drive_trash_groups where group_id = ? and workspace_id = ?",
                  (row, number) ->
                      new DriveTrashCleanupClaim(
                          mapGroup(row, number),
                          row.getString("claim_token"),
                          row.getTimestamp("lease_until").toInstant(),
                          row.getInt("cleanup_attempt"),
                          row.getBoolean("objects_deleted"),
                          row.getBoolean("derived_deleted"),
                          row.getBoolean("search_deleted")),
                  groupId,
                  workspaceId)
              .stream()
              .findFirst();
        });
  }

  public boolean checkpoint(
      DriveTrashCleanupClaim claim, DriveTrashCleanupPhase phase, Instant now) {
    String column =
        switch (phase) {
          case OBJECTS -> "objects_deleted";
          case DERIVED -> "derived_deleted";
          case SEARCH -> "search_deleted";
        };
    return jdbc.update(
            "update drive_trash_groups set "
                + column
                + " = true where group_id = ? and workspace_id = ? and state = 'PURGING' and"
                + " claim_token = ? and lease_until > ?",
            claim.group().groupId(),
            claim.group().workspaceId(),
            claim.token(),
            Timestamp.from(now))
        == 1;
  }

  public boolean retryCleanup(DriveTrashCleanupClaim claim, Instant nextRetry, String errorCode) {
    return jdbc.update(
            """
            update drive_trash_groups set claim_token = null, lease_until = null, next_retry_at = ?, last_error_code = ?
            where group_id = ? and workspace_id = ? and state = 'PURGING' and claim_token = ?
            """,
            Timestamp.from(nextRetry),
            errorCode,
            claim.group().groupId(),
            claim.group().workspaceId(),
            claim.token())
        == 1;
  }

  public boolean completeCleanup(DriveTrashCleanupClaim claim, Instant now) {
    return jdbc.update(
            """
            update drive_trash_groups set state = 'PURGED', claim_token = null, lease_until = null,
              next_retry_at = null, last_error_code = null
            where group_id = ? and workspace_id = ? and state = 'PURGING' and claim_token = ?
              and lease_until > ? and objects_deleted and derived_deleted and search_deleted
            """,
            claim.group().groupId(),
            claim.group().workspaceId(),
            claim.token(),
            Timestamp.from(now))
        == 1;
  }

  public boolean isActiveFile(String workspaceId, String fileId, Instant now) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists (select 1 from drive_files f where f.workspace_id = ? and f.file_id = ?"
                + " and "
                + DriveLifecycleSql.activeFile("f")
                + ")",
            Boolean.class,
            workspaceId,
            fileId));
  }

  public boolean isActiveFolder(String workspaceId, String folderId, Instant now) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "select exists (select 1 from drive_folders f where f.workspace_id = ? and f.folder_id"
                + " = ? and "
                + DriveLifecycleSql.activeFolder("f")
                + ")",
            Boolean.class,
            workspaceId,
            folderId));
  }

  private static DriveTrashGroup mapGroup(ResultSet row, int number) throws SQLException {
    return new DriveTrashGroup(
        row.getString("group_id"),
        row.getString("workspace_id"),
        row.getString("root_item_type"),
        row.getString("root_item_id"),
        row.getString("original_parent_folder_id"),
        row.getString("actor_id"),
        row.getTimestamp("trashed_at").toInstant(),
        row.getTimestamp("purge_after").toInstant(),
        DriveTrashState.valueOf(row.getString("state")));
  }
}
