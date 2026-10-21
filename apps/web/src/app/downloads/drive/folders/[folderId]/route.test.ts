// @vitest-environment node
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET } from "./route";
import { seededWorkspaceHeaders } from "@/lib/api";

const headers = {
  "Content-Type": "application/zip",
  "Content-Disposition": 'attachment; filename="synthetic.zip"',
};
const context = (folderId = "folder_synthetic") => ({
  params: Promise.resolve({ folderId }),
});
const request = (signal?: AbortSignal) =>
  new Request(
    "http://web/downloads/drive/folders/folder_synthetic?url=http://evil",
    {
      signal,
      headers: {
        "X-Actor-Id": "evil",
        "X-Workspace-Id": "evil",
        Cookie: "private=value",
        "X-Internal": "private",
      },
    },
  );
const fetchMock = vi.fn<typeof fetch>();
beforeEach(() => {
  vi.stubEnv("API_BASE_URL", "http://api:8080");
  vi.stubGlobal("fetch", fetchMock);
  fetchMock.mockResolvedValue(new Response("zip", { headers }));
});
afterEach(() => {
  vi.unstubAllEnvs();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  fetchMock.mockReset();
});

describe("Drive streaming transport", () => {
  it("usesConfiguredOriginAndFixedIdentity", async () => {
    const response = await GET(request(), context());
    expect(fetchMock).toHaveBeenCalledWith(
      "http://api:8080/api/drive/folders/folder_synthetic/download",
      {
        headers: { ...seededWorkspaceHeaders(), "Accept-Encoding": "identity" },
        redirect: "error",
        cache: "no-store",
        signal: expect.any(AbortSignal),
      },
    );
    expect(await response.text()).toBe("zip");
    const encoded = await GET(request(), context("folder %?#é"));
    expect(fetchMock.mock.calls[1][0]).toBe(
      "http://api:8080/api/drive/folders/folder%20%25%3F%23%C3%A9/download",
    );
    await encoded.body!.cancel();
  });
  it.each([
    undefined,
    "",
    " ",
    "/api",
    "api:8080",
    "ftp://api",
    "http://u:p@api",
    "http://@api",
    "http://:@api",
    "http://api?x",
    "http://api#x",
    "http://api/base",
    "http://api/base/..",
    "http://api/./",
    "http://api\\",
    "http://a\npi",
  ])("rejectsInvalidOriginBeforeFetch: %s", async (origin) => {
    vi.stubEnv("API_BASE_URL", origin);
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "");
    expect((await GET(request(), context())).status).toBe(502);
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it("ignoresPublicAndCallerControlledOrigins", async () => {
    vi.stubEnv("NEXT_PUBLIC_API_BASE_URL", "http://evil");
    const response = await GET(request(), context());
    expect(fetchMock.mock.calls[0][0]).toBe(
      "http://api:8080/api/drive/folders/folder_synthetic/download",
    );
    await response.body!.cancel();
  });
  it.each(["", ".", "..", "a/b", "a\\b", "a\u0000b", "a\u007fb"])(
    "rejectsInvalidFolderSegments: %s",
    async (id) => {
      expect((await GET(request(), context(id))).status).toBe(400);
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );
  it.each([401, 403, 404, 409, 413, 422, 429, 503, 500, 302])(
    "sanitizesUpstreamFailures: %s",
    async (status) => {
      const cancel = vi.fn();
      fetchMock.mockResolvedValue(
        new Response(new ReadableStream({ cancel }), {
          status,
          headers: { ...headers, "Set-Cookie": "secret" },
        }),
      );
      const response = await GET(request(), context());
      expect(response.status).toBe([500, 302].includes(status) ? 502 : status);
      expect(response.headers.has("Content-Disposition")).toBe(false);
      expect(response.headers.has("Set-Cookie")).toBe(false);
      expect(await response.text()).not.toMatch(/secret|private/);
      expect(cancel).toHaveBeenCalledOnce();
    },
  );
  it.each<Record<string, string>>([
    { "Content-Type": "text/html" },
    { "Content-Disposition": "inline" },
    { "Content-Encoding": "gzip" },
  ])("requiresZipAttachment: %s", async (override) => {
    fetchMock.mockResolvedValue(
      new Response("private diagnostic", {
        headers: { ...headers, ...override },
      }),
    );
    const response = await GET(request(), context());
    expect(response.status).toBe(502);
    expect(await response.text()).not.toContain("private diagnostic");
  });
  it("requires a body and exactly status 200", async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(null, { headers }))
      .mockResolvedValueOnce(new Response("zip", { status: 206, headers }));
    expect((await GET(request(), context())).status).toBe(502);
    expect((await GET(request(), context())).status).toBe(502);
  });
  it.each(["3", "0", "-1", "3.2", "bad"])(
    "allowlists response headers and validates length %s",
    async (length) => {
      fetchMock.mockResolvedValue(
        new Response("zip", {
          headers: {
            ...headers,
            "Content-Length": length,
            "Set-Cookie": "secret",
            "X-Internal": "secret",
            "Content-Encoding": "identity",
          },
        }),
      );
      const response = await GET(request(), context());
      expect(response.headers.get("Content-Length")).toBe(
        /^\d+$/.test(length) ? length : null,
      );
      expect(response.headers.get("Cache-Control")).toBe("no-store");
      expect(response.headers.get("X-Content-Type-Options")).toBe("nosniff");
      expect(response.headers.get("Referrer-Policy")).toBe("no-referrer");
      expect(response.headers.get("Content-Security-Policy")).toContain(
        "sandbox",
      );
      for (const name of ["Set-Cookie", "X-Internal", "Content-Encoding"])
        expect(response.headers.has(name)).toBe(false);
      await response.body!.cancel();
    },
  );
  it("streamsBeforeUpstreamCompletes", async () => {
    let upstream!: ReadableStreamDefaultController<Uint8Array>;
    const pull = vi.fn();
    fetchMock.mockResolvedValue(
      new Response(
        new ReadableStream(
          {
            start(controller) {
              upstream = controller;
            },
            pull,
          },
          { highWaterMark: 0 },
        ),
        { headers },
      ),
    );
    const response = await GET(request(), context());
    expect(pull).not.toHaveBeenCalled();
    const reader = response.body!.getReader();
    const first = reader.read();
    upstream.enqueue(new Uint8Array([1]));
    expect(await first).toEqual({ value: new Uint8Array([1]), done: false });
    const second = reader.read();
    upstream.enqueue(new Uint8Array([2]));
    upstream.close();
    expect((await second).value).toEqual(new Uint8Array([2]));
    expect((await reader.read()).done).toBe(true);
    expect(fetchMock.mock.calls[0][1]!.signal!.aborted).toBe(false);
  });
  it("cancelBeforeHeadersAbortsFetch", async () => {
    const abort = new AbortController();
    fetchMock.mockImplementation(
      (_url, init) =>
        new Promise((_resolve, reject) =>
          init!.signal!.addEventListener(
            "abort",
            () => reject(new Error("private")),
            { once: true },
          ),
        ),
    );
    const result = GET(request(abort.signal), context());
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    abort.abort();
    expect((await result).status).toBe(502);
    expect(fetchMock.mock.calls[0][1]!.signal!.aborted).toBe(true);
  });
  it("does not fetch an already aborted request", async () => {
    const abort = new AbortController();
    abort.abort();
    expect((await GET(request(abort.signal), context())).status).toBe(502);
    expect(fetchMock).not.toHaveBeenCalled();
  });
  it.each(["request", "downstream"])(
    "cancelDuringReadReleasesUpstream: %s",
    async (mode) => {
      const abort = new AbortController();
      const cancel = vi.fn();
      const body = new ReadableStream<Uint8Array>(
        { cancel },
        { highWaterMark: 0 },
      );
      fetchMock.mockResolvedValue(new Response(body, { headers }));
      const response = await GET(request(abort.signal), context());
      const reader = response.body!.getReader();
      const pending = reader.read().catch(() => "aborted");
      if (mode === "request") abort.abort();
      else await reader.cancel();
      await pending;
      await vi.waitFor(() => expect(body.locked).toBe(false));
      expect(cancel).toHaveBeenCalledOnce();
      expect(fetchMock.mock.calls[0][1]!.signal!.aborted).toBe(true);
    },
  );
  it("upstreamFailureErrorsDownstream", async () => {
    let upstream!: ReadableStreamDefaultController<Uint8Array>;
    const body = new ReadableStream<Uint8Array>({
      start(controller) {
        upstream = controller;
      },
    });
    fetchMock.mockResolvedValue(new Response(body, { headers }));
    const response = await GET(request(), context());
    const pending = response.body!.getReader().read();
    upstream.error(new Error("private storage path"));
    await expect(pending).rejects.toThrow("Download interrupted");
    expect(body.locked).toBe(false);
    expect(fetchMock.mock.calls[0][1]!.signal!.aborted).toBe(true);
  });
  it("sanitizes connection and redirect failures", async () => {
    fetchMock.mockRejectedValue(new Error("private address"));
    const response = await GET(request(), context());
    expect(response.status).toBe(502);
    expect(await response.text()).not.toContain("private address");
  });
  it("cancels a late upstream response after request abort", async () => {
    const abort = new AbortController();
    const cancel = vi.fn();
    let resolve!: (response: Response) => void;
    fetchMock.mockImplementation(
      () =>
        new Promise<Response>((done) => {
          resolve = done;
        }),
    );
    const pending = GET(request(abort.signal), context());
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledOnce());
    abort.abort();
    resolve(new Response(new ReadableStream({ cancel }), { headers }));
    expect((await pending).status).toBe(502);
    expect(cancel).toHaveBeenCalledOnce();
  });
  it("removes the request abort listener when the download finishes", async () => {
    const incoming = request();
    const add = vi.spyOn(incoming.signal, "addEventListener");
    const remove = vi.spyOn(incoming.signal, "removeEventListener");
    const response = await GET(incoming, context());
    await response.text();
    expect(remove).toHaveBeenCalledWith("abort", add.mock.calls[0][1]);
  });
});
