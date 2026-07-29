package com.openecosystem.os.drive;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class FileViewAuthorizationRepository {

  private static final RowMapper<FileViewAuthorizationFacts> FACTS_ROW_MAPPER =
      FileViewAuthorizationRepository::mapFacts;

  private final JdbcTemplate jdbcTemplate;

  public FileViewAuthorizationRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<FileViewAuthorizationFacts> findFacts(
      String actorId, String workspaceId, String fileId) {
    if (actorId == null || workspaceId == null || fileId == null) {
      return Optional.empty();
    }
    return jdbcTemplate
        .query(
            """
            select f.file_id,
                   f.workspace_id,
                   f.owner_id,
                   f.visibility,
                   exists (
                     select 1
                     from identity_users actor
                     join workspaces workspace
                       on workspace.workspace_id = f.workspace_id
                      and workspace.status = 'active'
                     where actor.user_id = ?
                       and actor.status = 'active'
                       and exists (
                         select 1
                         from workspace_memberships membership
                         where membership.workspace_id = f.workspace_id
                           and membership.user_id = actor.user_id
                       )
                   ) as active_member,
                   exists (
                     select 1
                     from identity_users actor
                     join workspaces workspace
                       on workspace.workspace_id = f.workspace_id
                      and workspace.status = 'active'
                     join workspace_memberships membership
                       on membership.workspace_id = f.workspace_id
                      and membership.user_id = actor.user_id
                     where actor.user_id = ?
                       and actor.status = 'active'
                       and membership.role in ('INSTANCE_OWNER', 'WORKSPACE_ADMIN', 'DEVELOPER', 'EDITOR', 'VIEWER')
                   ) as workspace_visibility_role,
                   exists (
                     select 1
                     from identity_users actor
                     join workspaces workspace
                       on workspace.workspace_id = f.workspace_id
                      and workspace.status = 'active'
                     join workspace_memberships membership
                       on membership.workspace_id = f.workspace_id
                      and membership.user_id = actor.user_id
                     join drive_file_user_grants grant_record
                       on grant_record.workspace_id = f.workspace_id
                      and grant_record.file_id = f.file_id
                      and grant_record.grantee_user_id = actor.user_id
                      and grant_record.action = 'file:view'
                      and grant_record.revoked_at is null
                     where actor.user_id = ?
                       and actor.status = 'active'
                   ) as active_user_grant
            from drive_files f
            where f.file_id = ?
              and f.workspace_id = ?
            """,
            FACTS_ROW_MAPPER,
            actorId,
            actorId,
            actorId,
            fileId,
            workspaceId)
        .stream()
        .findFirst();
  }

  public Set<String> findAllowedFileIds(
      String actorId, String workspaceId, Collection<String> fileIds) {
    if (actorId == null || workspaceId == null || fileIds == null || fileIds.isEmpty()) {
      return Set.of();
    }
    List<String> ids =
        fileIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
    if (ids.isEmpty()) {
      return Set.of();
    }

    String placeholders = String.join(", ", Collections.nCopies(ids.size(), "?"));
    Object[] arguments = new Object[ids.size() + 2];
    arguments[0] = actorId;
    arguments[1] = workspaceId;
    for (int index = 0; index < ids.size(); index++) {
      arguments[index + 2] = ids.get(index);
    }
    return Set.copyOf(
        new LinkedHashSet<>(
            jdbcTemplate.queryForList(
                """
                select f.file_id
                from drive_files f
                where exists (
                  select 1
                  from identity_users actor
                  join workspaces workspace
                    on workspace.workspace_id = f.workspace_id
                   and workspace.status = 'active'
                  join workspace_memberships membership
                    on membership.workspace_id = f.workspace_id
                   and membership.user_id = actor.user_id
                  where actor.user_id = ?
                    and actor.status = 'active'
                )
                  and f.workspace_id = ?
                  and f.file_id in (
                """
                    + placeholders
                    + """
                    )
                      and (
                        f.owner_id = ?
                        or (
                          f.visibility = 'workspace'
                          and exists (
                            select 1
                            from workspace_memberships membership
                            where membership.workspace_id = f.workspace_id
                              and membership.user_id = ?
                              and membership.role in ('INSTANCE_OWNER', 'WORKSPACE_ADMIN', 'DEVELOPER', 'EDITOR', 'VIEWER')
                          )
                        )
                        or exists (
                          select 1
                          from drive_file_user_grants grant_record
                          where grant_record.workspace_id = f.workspace_id
                            and grant_record.file_id = f.file_id
                            and grant_record.grantee_user_id = ?
                            and grant_record.action = 'file:view'
                            and grant_record.revoked_at is null
                        )
                      )
                    """,
                String.class,
                batchArguments(arguments, actorId))));
  }

  private Object[] batchArguments(Object[] leadingArguments, String actorId) {
    Object[] arguments = new Object[leadingArguments.length + 3];
    System.arraycopy(leadingArguments, 0, arguments, 0, leadingArguments.length);
    arguments[leadingArguments.length] = actorId;
    arguments[leadingArguments.length + 1] = actorId;
    arguments[leadingArguments.length + 2] = actorId;
    return arguments;
  }

  private static FileViewAuthorizationFacts mapFacts(ResultSet resultSet, int rowNumber)
      throws SQLException {
    return new FileViewAuthorizationFacts(
        resultSet.getString("file_id"),
        resultSet.getString("workspace_id"),
        resultSet.getString("owner_id"),
        DriveFileVisibility.fromValue(resultSet.getString("visibility")),
        resultSet.getBoolean("active_member"),
        resultSet.getBoolean("workspace_visibility_role"),
        resultSet.getBoolean("active_user_grant"));
  }
}
