import { afterEach, describe, expect, it, vi } from "vitest";
import {
  createDriveFolder,
  downloadDriveFile,
  fetchDriveFolderContents,
  moveDriveFile,
  moveDriveFolder,
  renameDriveFile,
  renameDriveFolder,
} from "@/lib/drive-api";
import { driveMockFiles } from "@/lib/drive-mock-data";
import { workspaceHeaders } from "@/lib/api";

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

describe("Drive API contracts", () => {
  it("encodes folder IDs and omits the root parameter", async () => {
    const fetchMock = vi.fn(async () =>
      Response.json({ folders: [], files: [] }),
    );
    vi.stubGlobal("fetch", fetchMock);
    await fetchDriveFolderContents(null);
    await fetchDriveFolderContents("folder/id &");
    expect(fetchMock.mock.calls).toHaveLength(2);
    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      expect.stringMatching(/\/folders\/contents$/),
      expect.objectContaining({ cache: "no-store", headers: workspaceHeaders }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      expect.stringContaining("folderId=folder%2Fid%20%26"),
      expect.anything(),
    );
  });
  it.each([
    [
      "create",
      () => createDriveFolder("Synthetic", null),
      "/folders",
      "POST",
      { name: "Synthetic", parentFolderId: null },
    ],
    [
      "rename file",
      () => renameDriveFile("file/id", "Synthetic"),
      "/files/file%2Fid/name",
      "PATCH",
      { name: "Synthetic" },
    ],
    [
      "rename folder",
      () => renameDriveFolder("folder/id", "Synthetic"),
      "/folders/folder%2Fid/name",
      "PATCH",
      { name: "Synthetic" },
    ],
    [
      "move file to root",
      () => moveDriveFile("file/id", null),
      "/files/file%2Fid/location",
      "PATCH",
      { destinationFolderId: null },
    ],
    [
      "move folder",
      () => moveDriveFolder("folder/id", "target"),
      "/folders/folder%2Fid/location",
      "PATCH",
      { destinationFolderId: "target" },
    ],
  ] as const)(
    "%s uses the approved JSON contract",
    async (_label, invoke, path, method, body) => {
      const fetchMock = vi.fn(async () => Response.json({}));
      vi.stubGlobal("fetch", fetchMock);
      await invoke();
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining(path),
        expect.objectContaining({
          method,
          headers: { ...workspaceHeaders, "Content-Type": "application/json" },
          body: JSON.stringify(body),
        }),
      );
    },
  );
  it("downloads originals with workspace headers and revokes blob URLs", async () => {
    vi.useFakeTimers();
    const create = vi.fn(() => "blob:synthetic");
    const revoke = vi.fn();
    vi.stubGlobal(
      "URL",
      Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke }),
    );
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, "click")
      .mockImplementation(function (this: HTMLAnchorElement) {
        expect(this.download).not.toMatch(/[\\/]/);
        expect(this.href).toBe("blob:synthetic");
      });
    const fetchMock = vi.fn(async () => new Response("synthetic source bytes"));
    vi.stubGlobal("fetch", fetchMock);
    await downloadDriveFile({ ...driveMockFiles[0], name: "../Synthetic.pdf" });
    expect(click).toHaveBeenCalledOnce();
    expect(create).toHaveBeenCalledOnce();
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining(`/files/${driveMockFiles[0].fileId}/download`),
      expect.objectContaining({ headers: workspaceHeaders, cache: "no-store" }),
    );
    await vi.runAllTimersAsync();
    expect(revoke).toHaveBeenCalledOnce();
    expect(document.querySelector("a[download]")).toBeNull();
  });
});
