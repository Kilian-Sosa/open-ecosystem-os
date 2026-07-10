export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export const DEFAULT_SEEDED_ACTOR_ID = "usr_dev_placeholder";
export const DEFAULT_SEEDED_WORKSPACE_ID = "wrk_dev_placeholder";

export type WorkspaceHeaders = {
  "X-Actor-Id": string;
  "X-Workspace-Id": string;
};

export function seededWorkspaceHeaders(
  actorId = DEFAULT_SEEDED_ACTOR_ID,
  workspaceId = DEFAULT_SEEDED_WORKSPACE_ID,
): WorkspaceHeaders {
  return {
    "X-Actor-Id": actorId,
    "X-Workspace-Id": workspaceId,
  };
}

export const workspaceHeaders = seededWorkspaceHeaders();
