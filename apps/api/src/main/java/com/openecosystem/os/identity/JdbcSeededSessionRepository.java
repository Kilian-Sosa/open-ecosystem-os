package com.openecosystem.os.identity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSeededSessionRepository implements SeededSessionRepository {

  private final JdbcTemplate jdbcTemplate;

  public JdbcSeededSessionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public Optional<SeededSession> findActiveSession(String actorId, String workspaceId) {
    return jdbcTemplate.query(
        """
        select
          u.user_id,
          u.display_name,
          u.email,
          u.avatar_initials,
          w.workspace_id,
          w.name as workspace_name,
          w.slug as workspace_slug,
          wm.role
        from workspace_memberships wm
        join identity_users u on u.user_id = wm.user_id
        join workspaces w on w.workspace_id = wm.workspace_id
        where u.user_id = ?
          and w.workspace_id = ?
          and u.status = 'active'
          and w.status = 'active'
        order by wm.role
        """,
        (ResultSetExtractor<Optional<SeededSession>>) this::mapSession,
        actorId,
        workspaceId);
  }

  private Optional<SeededSession> mapSession(ResultSet resultSet) throws SQLException {
    if (!resultSet.next()) return Optional.empty();

    String actorId = resultSet.getString("user_id");
    String displayName = resultSet.getString("display_name");
    String email = resultSet.getString("email");
    String avatarInitials = resultSet.getString("avatar_initials");
    String workspaceId = resultSet.getString("workspace_id");
    String workspaceName = resultSet.getString("workspace_name");
    String workspaceSlug = resultSet.getString("workspace_slug");
    Set<String> roles = new LinkedHashSet<>();

    do {
      roles.add(resultSet.getString("role"));
    } while (resultSet.next());

    return Optional.of(
        new SeededSession(
            actorId,
            displayName,
            email,
            avatarInitials,
            workspaceId,
            workspaceName,
            workspaceSlug,
            roles,
            SeededSessionDefaults.AUTH_MODE));
  }
}
