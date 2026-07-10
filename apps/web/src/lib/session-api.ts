import {
  API_BASE_URL,
  DEFAULT_SEEDED_ACTOR_ID,
  DEFAULT_SEEDED_WORKSPACE_ID,
  workspaceHeaders,
} from "@/lib/api";

export type SessionActor = {
  actorId: string;
  displayName: string;
  email: string;
  avatarInitials: string;
};

export type SessionWorkspace = {
  workspaceId: string;
  name: string;
  slug: string;
};

export type SessionBootstrap = {
  authMode: string;
  authenticated: boolean;
  actor: SessionActor;
  workspace: SessionWorkspace;
  roles: string[];
};

export const defaultSessionBootstrap: SessionBootstrap = {
  authMode: "seeded_dev",
  authenticated: true,
  actor: {
    actorId: DEFAULT_SEEDED_ACTOR_ID,
    displayName: "Demo Admin",
    email: "demo.admin@example.test",
    avatarInitials: "DA",
  },
  workspace: {
    workspaceId: DEFAULT_SEEDED_WORKSPACE_ID,
    name: "Open Ecosystem Demo Workspace",
    slug: "demo",
  },
  roles: ["WORKSPACE_ADMIN", "DEVELOPMENT_PLACEHOLDER"],
};

export const sessionBootstrapQueryKey = ["session", "bootstrap"] as const;

const roleLabels: Record<string, string> = {
  AUDITOR: "Auditor",
  DEVELOPMENT_PLACEHOLDER: "Development Placeholder",
  DEVELOPER: "Developer",
  EDITOR: "Editor",
  GUEST: "Guest",
  INSTANCE_OWNER: "Instance Owner",
  VIEWER: "Viewer",
  WORKSPACE_ADMIN: "Workspace Admin",
};

export async function fetchSessionBootstrap(): Promise<SessionBootstrap> {
  const response = await fetch(`${API_BASE_URL}/api/session/bootstrap`, {
    headers: workspaceHeaders,
  });

  if (!response.ok) {
    throw new Error("Session bootstrap could not be loaded");
  }

  return response.json() as Promise<SessionBootstrap>;
}

export function primaryRoleLabel(roles: string[]): string {
  const primaryRole =
    roles.find((role) => role === "WORKSPACE_ADMIN") ??
    roles.find((role) => role !== "DEVELOPMENT_PLACEHOLDER") ??
    roles[0];

  return primaryRole ? (roleLabels[primaryRole] ?? primaryRole) : "Workspace";
}
