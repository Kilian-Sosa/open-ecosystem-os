import { afterEach, describe, expect, it, vi } from "vitest";

import {
  API_BASE_URL,
  DEFAULT_SEEDED_ACTOR_ID,
  DEFAULT_SEEDED_WORKSPACE_ID,
  workspaceHeaders,
} from "@/lib/api";
import { fetchSessionBootstrap } from "@/lib/session-api";

describe("session API", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("uses seeded default identity headers for MVP API calls", () => {
    expect(workspaceHeaders).toEqual({
      "X-Actor-Id": DEFAULT_SEEDED_ACTOR_ID,
      "X-Workspace-Id": DEFAULT_SEEDED_WORKSPACE_ID,
    });
  });

  it("loads the session bootstrap with the same seeded headers", async () => {
    const session = {
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
    const fetchMock = vi.fn(async () => jsonResponse(session));
    vi.stubGlobal("fetch", fetchMock);

    await expect(fetchSessionBootstrap()).resolves.toEqual(session);
    expect(fetchMock).toHaveBeenCalledWith(
      `${API_BASE_URL}/api/session/bootstrap`,
      {
        headers: workspaceHeaders,
      },
    );
  });
});

function jsonResponse(body: unknown) {
  return {
    ok: true,
    json: async () => body,
  } as Response;
}
