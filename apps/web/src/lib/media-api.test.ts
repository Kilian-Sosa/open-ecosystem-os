import { afterEach, describe, expect, it, vi } from "vitest";

import { fetchOcrJob, fetchOcrJobs } from "./media-api";

describe("media API", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("preserves a list authorization status on API errors", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve({ ok: false, status: 403 })),
    );

    await expect(fetchOcrJobs()).rejects.toMatchObject({ status: 403 });
  });

  it("preserves a detail API status on errors", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve({ ok: false, status: 500 })),
    );

    await expect(fetchOcrJob("job-1")).rejects.toMatchObject({ status: 500 });
  });
});
