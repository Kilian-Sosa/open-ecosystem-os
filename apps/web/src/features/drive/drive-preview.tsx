"use client";

import { useEffect, useState } from "react";
import { fetchDrivePreview, type DriveFile } from "@/lib/drive-api";
import { DriveActionDialog } from "./drive-action-dialog";
import { DrivePdfPreview } from "./drive-pdf-preview";

type Preview =
  | { url: string }
  | { pdf: Blob }
  | { text: string }
  | { error: string };

export function DrivePreview({
  file,
  onClose,
}: {
  file: DriveFile;
  onClose: () => void;
}) {
  const [preview, setPreview] = useState<Preview | null>(null);
  const supported = [
    "application/pdf",
    "image/png",
    "image/jpeg",
    "text/plain",
  ].includes(file.contentType);
  useEffect(() => {
    if (!supported) return;
    const controller = new AbortController();
    let url: string | undefined;
    async function load() {
      try {
        const blob = await fetchDrivePreview(file, controller.signal);
        if (file.contentType === "text/plain") {
          const text = await blob.text();
          if (!controller.signal.aborted) setPreview({ text });
        } else if (
          file.contentType === "application/pdf" &&
          !controller.signal.aborted
        ) {
          setPreview({ pdf: blob });
        } else if (!controller.signal.aborted) {
          url = URL.createObjectURL(blob);
          setPreview({ url });
        }
      } catch {
        if (!controller.signal.aborted)
          setPreview({
            error:
              "Preview could not be loaded safely. Download the original instead.",
          });
      }
    }
    void load();
    return () => {
      controller.abort();
      if (url) URL.revokeObjectURL(url);
    };
  }, [file, supported]);
  return (
    <DriveActionDialog title={`Preview ${file.name}`} onClose={onClose}>
      {!supported ? (
        <p>
          Preview is unavailable for this file type. Download the original
          instead.
        </p>
      ) : !preview ? (
        <p role="status">Loading preview…</p>
      ) : "error" in preview ? (
        <p role="alert">{preview.error}</p>
      ) : "text" in preview ? (
        <pre className="max-h-[60dvh] overflow-auto whitespace-pre-wrap break-words text-sm">
          {preview.text}
        </pre>
      ) : "pdf" in preview ? (
        <DrivePdfPreview blob={preview.pdf} name={file.name} />
      ) : (
        // Protected blob URLs require a local image element rather than the Next image proxy.
        // eslint-disable-next-line @next/next/no-img-element
        <img
          src={preview.url}
          alt={`Preview of ${file.name}`}
          className="max-h-[60dvh] max-w-full object-contain"
        />
      )}
    </DriveActionDialog>
  );
}
