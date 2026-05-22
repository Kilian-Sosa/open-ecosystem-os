export const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export const workspaceHeaders = {
  "X-Actor-Id": "usr_dev_placeholder",
  "X-Workspace-Id": "wrk_dev_placeholder",
};
