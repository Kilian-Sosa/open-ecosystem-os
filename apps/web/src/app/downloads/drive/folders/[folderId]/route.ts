import { seededWorkspaceHeaders } from "@/lib/api";

export const runtime = "nodejs";
export const dynamic = "force-dynamic";

const safetyHeaders = {
  "Cache-Control": "no-store",
  "X-Content-Type-Options": "nosniff",
  "Referrer-Policy": "no-referrer",
  "Content-Security-Policy": "default-src 'none'; sandbox",
};
const errors: Record<number, string> = {
  400: "Invalid folder identifier.",
  401: "Sign in to download this folder.",
  403: "Folder download is not permitted.",
  404: "Folder is not available.",
  409: "Folder download conflicts with its current state.",
  413: "Folder exceeds the archive limits.",
  422: "Folder cannot be archived.",
  429: "Too many download requests.",
  502: "Folder download is unavailable.",
  503: "Folder download is temporarily unavailable.",
};
function failure(status: number): Response {
  return new Response(errors[status], {
    status,
    headers: { ...safetyHeaders, "Content-Type": "text/plain; charset=utf-8" },
  });
}

function apiOrigin(): string | null {
  const value = process.env.API_BASE_URL;
  if (!value || !/^https?:\/\/[^/\\\s?#@]+\/?$/i.test(value)) return null;
  try {
    const url = new URL(value);
    if (
      !url.hostname ||
      url.username ||
      url.password ||
      url.search ||
      url.hash ||
      url.pathname !== "/" ||
      /[?#]/.test(value)
    )
      return null;
    return url.origin;
  } catch {
    return null;
  }
}

export async function GET(
  request: Request,
  context: { params: Promise<{ folderId: string }> },
): Promise<Response> {
  const { folderId } = await context.params;
  if (
    !folderId ||
    folderId === "." ||
    folderId === ".." ||
    /[\\/\x00-\x1f\x7f]/.test(folderId)
  )
    return failure(400);
  const origin = apiOrigin();
  if (!origin) return failure(502);

  const abort = new AbortController();
  let reader: ReadableStreamDefaultReader<Uint8Array> | undefined;
  let downstream: ReadableStreamDefaultController<Uint8Array> | undefined;
  let finished = false;
  let cleanup: Promise<void> | undefined;
  function finish(cancel: boolean): Promise<void> {
    if (cleanup) return cleanup;
    finished = true;
    request.signal.removeEventListener("abort", onAbort);
    if (cancel) abort.abort();
    cleanup = (async () => {
      try {
        if (cancel) await reader?.cancel();
      } catch {
        // Cancellation must still release a failed upstream reader.
      } finally {
        reader?.releaseLock();
      }
    })();
    return cleanup;
  }
  function onAbort() {
    if (finished) return;
    downstream?.error(new Error("Download interrupted"));
    void finish(true);
  }
  request.signal.addEventListener("abort", onAbort, { once: true });
  if (request.signal.aborted) {
    await finish(true);
    return failure(502);
  }

  try {
    const destination = new URL(
      `/api/drive/folders/${encodeURIComponent(folderId)}/download`,
      origin,
    );
    const upstream = await fetch(destination.href, {
      headers: { ...seededWorkspaceHeaders(), "Accept-Encoding": "identity" },
      redirect: "error",
      cache: "no-store",
      signal: abort.signal,
    });
    if (finished) {
      await upstream.body?.cancel().catch(() => {});
      return failure(502);
    }
    const type = upstream.headers.get("Content-Type");
    const disposition = upstream.headers.get("Content-Disposition");
    const encoding = upstream.headers.get("Content-Encoding");
    if (
      upstream.status !== 200 ||
      type?.split(";")[0].trim().toLowerCase() !== "application/zip" ||
      !/^attachment(?:\s*;|\s*$)/i.test(disposition ?? "") ||
      !upstream.body ||
      (encoding !== null && encoding.toLowerCase() !== "identity")
    ) {
      await upstream.body?.cancel().catch(() => {});
      await finish(true);
      return failure(
        upstream.status !== 200 &&
          errors[upstream.status] &&
          upstream.status !== 400
          ? upstream.status
          : 502,
      );
    }
    reader = upstream.body.getReader();
    const responseHeaders = new Headers({
      ...safetyHeaders,
      "Content-Type": "application/zip",
      "Content-Disposition": disposition!,
    });
    const length = upstream.headers.get("Content-Length");
    if (length !== null && /^\d+$/.test(length))
      responseHeaders.set("Content-Length", length);
    const body = new ReadableStream<Uint8Array>(
      {
        start(controller) {
          downstream = controller;
        },
        async pull(controller) {
          try {
            const result = await reader!.read();
            if (finished) return;
            if (result.done) {
              controller.close();
              await finish(false);
            } else controller.enqueue(result.value);
          } catch {
            if (!finished) {
              controller.error(new Error("Download interrupted"));
              await finish(true);
            }
          }
        },
        cancel() {
          return finish(true);
        },
      },
      { highWaterMark: 0 },
    );
    return new Response(body, { headers: responseHeaders });
  } catch {
    await finish(true);
    return failure(502);
  }
}
