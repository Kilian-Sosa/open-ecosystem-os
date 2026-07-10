package com.openecosystem.os.identity;

import java.util.Set;

public final class SeededSessionDefaults {

  public static final String DEFAULT_ACTOR_ID = "usr_dev_placeholder";
  public static final String DEFAULT_WORKSPACE_ID = "wrk_dev_placeholder";
  public static final String AUTH_MODE = "seeded_dev";

  private static final Set<String> DEFAULT_ROLES =
      Set.of("WORKSPACE_ADMIN", "DEVELOPMENT_PLACEHOLDER");

  private SeededSessionDefaults() {}

  public static boolean isDefaultMembership(String actorId, String workspaceId) {
    return DEFAULT_ACTOR_ID.equals(actorId) && DEFAULT_WORKSPACE_ID.equals(workspaceId);
  }

  public static SeededSession defaultSession() {
    return new SeededSession(
        DEFAULT_ACTOR_ID,
        "Demo Admin",
        "demo.admin@example.test",
        "DA",
        DEFAULT_WORKSPACE_ID,
        "Open Ecosystem Demo Workspace",
        "demo",
        DEFAULT_ROLES,
        AUTH_MODE);
  }
}
