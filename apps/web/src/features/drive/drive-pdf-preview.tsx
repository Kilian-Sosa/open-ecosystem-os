"use client";

import { useEffect, useRef, useState } from "react";
import type {
  PDFDocumentProxy,
  PDFDocumentLoadingTask,
  PDFWorker,
} from "pdfjs-dist";
import { driveButton } from "./drive-action-dialog";

export function DrivePdfPreview({ blob, name }: { blob: Blob; name: string }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [document, setDocument] = useState<PDFDocumentProxy | null>(null);
  const [pageNumber, setPageNumber] = useState(1);
  const [error, setError] = useState(false);
  const [renderedPage, setRenderedPage] = useState<number | null>(null);
  useEffect(() => {
    let disposed = false;
    let expired = false;
    let port: Worker | undefined;
    let worker: PDFWorker | undefined;
    let task: PDFDocumentLoadingTask | undefined;
    const timeout = setTimeout(() => {
      if (!disposed) {
        expired = true;
        setError(true);
        port?.terminate();
      }
    }, 30_000);
    void (async () => {
      try {
        const pdfjs = await import("pdfjs-dist");
        const data = new Uint8Array(await blob.arrayBuffer());
        if (disposed || expired) return;
        port = new Worker(
          new URL("pdfjs-dist/build/pdf.worker.min.mjs", import.meta.url),
          { type: "module" },
        );
        worker = pdfjs.PDFWorker.create({ port });
        task = pdfjs.getDocument({
          data,
          worker,
          enableXfa: false,
          disableFontFace: true,
          useSystemFonts: false,
          useWorkerFetch: false,
          useWasm: false,
          stopAtErrors: true,
          maxImageSize: 4_000_000,
          isOffscreenCanvasSupported: false,
          isImageDecoderSupported: false,
          verbosity: 0,
        });
        const pdf = await task.promise;
        if (pdf.numPages < 1 || pdf.numPages > 40) throw new Error();
        if (!disposed && !expired) setDocument(pdf);
      } catch {
        if (!disposed) setError(true);
      } finally {
        clearTimeout(timeout);
      }
    })();
    return () => {
      disposed = true;
      clearTimeout(timeout);
      void task?.destroy().catch(() => {});
      worker?.destroy();
      port?.terminate();
    };
  }, [blob]);
  useEffect(() => {
    if (!document) return;
    let disposed = false;
    let cancel: (() => void) | undefined;
    const timeout = setTimeout(() => {
      if (!disposed) {
        cancel?.();
        setError(true);
      }
    }, 15_000);
    void (async () => {
      try {
        const page = await document.getPage(pageNumber);
        if (disposed || !canvasRef.current) return;
        const viewport = page.getViewport({ scale: 1 });
        if (
          !Number.isFinite(viewport.width) ||
          !Number.isFinite(viewport.height) ||
          viewport.width < 1 ||
          viewport.height < 1 ||
          viewport.width > 8192 ||
          viewport.height > 8192 ||
          Math.ceil(viewport.width) * Math.ceil(viewport.height) > 4_000_000
        )
          throw new Error();
        const canvas = canvasRef.current;
        const context = canvas.getContext("2d");
        if (!context) throw new Error();
        canvas.width = Math.ceil(viewport.width);
        canvas.height = Math.ceil(viewport.height);
        const render = page.render({
          canvas,
          canvasContext: context,
          viewport,
          annotationMode: 0,
        });
        cancel = () => render.cancel();
        await render.promise;
        if (!disposed) setRenderedPage(pageNumber);
      } catch {
        if (!disposed) setError(true);
      } finally {
        clearTimeout(timeout);
      }
    })();
    return () => {
      disposed = true;
      clearTimeout(timeout);
      cancel?.();
    };
  }, [document, pageNumber]);
  if (error)
    return (
      <p role="alert">
        PDF preview could not be rendered safely. Download the original instead.
      </p>
    );
  return (
    <div className="space-y-3">
      <p className="text-sm text-text-secondary">
        Image preview; use the original download for accessible document text.
      </p>
      {document ? (
        <div className="flex flex-wrap items-center gap-3">
          <button
            className={driveButton}
            disabled={pageNumber === 1 || renderedPage !== pageNumber}
            onClick={() => setPageNumber(pageNumber - 1)}
          >
            Previous page
          </button>
          <span>
            Page {pageNumber} of {document.numPages}
          </span>
          <button
            className={driveButton}
            disabled={
              pageNumber === document.numPages || renderedPage !== pageNumber
            }
            onClick={() => setPageNumber(pageNumber + 1)}
          >
            Next page
          </button>
        </div>
      ) : null}
      {renderedPage !== pageNumber ? (
        <p role="status">Rendering PDF page…</p>
      ) : null}
      <canvas
        hidden={renderedPage !== pageNumber}
        ref={canvasRef}
        role="img"
        aria-label={`Page ${pageNumber} of ${name}`}
        className="h-auto max-w-full"
      />
    </div>
  );
}
