import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DrivePreview } from "./drive-preview";
import { driveMockFiles } from "@/lib/drive-mock-data";
import { workspaceHeaders } from "@/lib/api";

vi.mock("./drive-pdf-preview", () => ({
  DrivePdfPreview: ({ name }: { name: string }) => (
    <div role="img" aria-label={`PDF canvas for ${name}`} />
  ),
}));

describe("Drive protected previews", () => {
  beforeEach(() => {
    Object.defineProperty(HTMLDialogElement.prototype, "showModal", {
      configurable: true,
      value: function (this: HTMLDialogElement) {
        this.setAttribute("open", "");
      },
    });
    Object.defineProperty(HTMLDialogElement.prototype, "close", {
      configurable: true,
      value: function (this: HTMLDialogElement) {
        this.removeAttribute("open");
      },
    });
    vi.stubGlobal(
      "URL",
      Object.assign(URL, {
        createObjectURL: vi.fn(() => "blob:synthetic-preview"),
        revokeObjectURL: vi.fn(),
      }),
    );
  });
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });
  it.each(["application/pdf", "image/png", "image/jpeg", "text/plain"])(
    "previewsPdfImageTextByProtectedFetch: %s",
    async (contentType) => {
      const fetchMock = vi.fn(
        async () =>
          new Response("<script>window.active = true</script>", {
            headers: { "Content-Type": contentType },
          }),
      );
      vi.stubGlobal("fetch", fetchMock);
      const { container, unmount } = render(
        <DrivePreview
          file={{ ...driveMockFiles[0], contentType }}
          onClose={() => {}}
        />,
      );
      await waitFor(() =>
        expect(screen.queryByText("Loading preview…")).not.toBeInTheDocument(),
      );
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining(`/files/${driveMockFiles[0].fileId}/preview`),
        expect.objectContaining({
          headers: workspaceHeaders,
          cache: "no-store",
          signal: expect.any(AbortSignal),
        }),
      );
      if (contentType === "application/pdf") {
        expect(
          screen.getByRole("img", { name: /PDF canvas/ }),
        ).toBeInTheDocument();
        expect(container.querySelector("iframe, embed, object")).toBeNull();
      } else if (contentType === "text/plain") {
        expect(
          screen.getByText("<script>window.active = true</script>"),
        ).toBeInTheDocument();
        expect(container.querySelector("script")).toBeNull();
        expect(container.querySelector("iframe")).toBeNull();
      } else
        expect(screen.getByRole("img")).toHaveAttribute(
          "src",
          "blob:synthetic-preview",
        );
      unmount();
      if (contentType.startsWith("image/"))
        expect(URL.revokeObjectURL).toHaveBeenCalledWith(
          "blob:synthetic-preview",
        );
    },
  );
  it("does not fetch or embed Office documents", () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    render(<DrivePreview file={driveMockFiles[1]} onClose={() => {}} />);
    expect(screen.getByText(/Preview is unavailable/)).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it.each([403, 404, 422, 500])(
    "fails safely for protected preview error %s",
    async (status) => {
      vi.stubGlobal(
        "fetch",
        vi.fn(async () => new Response("private server detail", { status })),
      );
      render(<DrivePreview file={driveMockFiles[0]} onClose={() => {}} />);
      expect(await screen.findByRole("alert")).toHaveTextContent(
        "Preview could not be loaded safely",
      );
      expect(
        screen.queryByText("private server detail"),
      ).not.toBeInTheDocument();
    },
  );
  it("rejects mismatched active content and oversized responses", async () => {
    const fetchMock = vi.fn(
      async () =>
        new Response("<script>active</script>", {
          headers: { "Content-Type": "text/html" },
        }),
    );
    vi.stubGlobal("fetch", fetchMock);
    const first = render(
      <DrivePreview file={driveMockFiles[0]} onClose={() => {}} />,
    );
    await screen.findByRole("alert");
    expect(URL.createObjectURL).not.toHaveBeenCalled();
    first.unmount();
    fetchMock.mockImplementation(
      async () =>
        new Response("oversized", {
          headers: {
            "Content-Type": "application/pdf",
            "Content-Length": String(26 * 1024 * 1024),
          },
        }),
    );
    render(<DrivePreview file={driveMockFiles[0]} onClose={() => {}} />);
    await screen.findByRole("alert");
    expect(URL.createObjectURL).not.toHaveBeenCalled();
  });
  it("aborts an in-flight preview when closed", async () => {
    const fetchMock = vi.fn<
      (url: string, init: RequestInit) => Promise<Response>
    >(() => new Promise<Response>(() => {}));
    vi.stubGlobal("fetch", fetchMock);
    const close = vi.fn();
    const view = render(
      <DrivePreview file={driveMockFiles[0]} onClose={close} />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
    expect(close).toHaveBeenCalled();
    view.unmount();
    expect(fetchMock.mock.calls[0][1].signal?.aborted).toBe(true);
  });
});
