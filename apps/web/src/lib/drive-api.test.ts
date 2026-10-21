import { afterEach, expect, it, vi } from "vitest";
import { downloadDriveFile } from "./drive-api";
import { driveMockFiles } from "./drive-mock-data";
import { workspaceHeaders } from "./api";

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  vi.useRealTimers();
});

it("preserves original file bytes, workspace headers and delayed URL cleanup", async () => {
  vi.useFakeTimers();
  const createObjectURL = vi.fn<(blob: Blob) => string>(() => "blob:synthetic");
  const revokeObjectURL = vi.fn();
  vi.stubGlobal("URL", { createObjectURL, revokeObjectURL });
  const click = vi
    .spyOn(HTMLAnchorElement.prototype, "click")
    .mockImplementation(() => {});
  const bytes = new Uint8Array([0, 255, 1, 13, 10]);
  const fetch = vi.fn().mockResolvedValue(new Response(bytes));
  vi.stubGlobal("fetch", fetch);
  await downloadDriveFile(driveMockFiles[0]);
  expect(fetch).toHaveBeenCalledWith(
    expect.stringContaining(`/files/${driveMockFiles[0].fileId}/download`),
    expect.objectContaining({ headers: workspaceHeaders, cache: "no-store" }),
  );
  const blob = createObjectURL.mock.calls[0][0];
  expect(new Uint8Array(await blob.arrayBuffer())).toEqual(bytes);
  expect(click).toHaveBeenCalledOnce();
  expect(document.querySelector("a[download]")).toBeNull();
  expect(revokeObjectURL).not.toHaveBeenCalled();
  await vi.runAllTimersAsync();
  expect(revokeObjectURL).toHaveBeenCalledWith("blob:synthetic");
});
