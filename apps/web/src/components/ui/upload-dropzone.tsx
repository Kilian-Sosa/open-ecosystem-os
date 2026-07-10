"use client";

import { UploadCloud } from "lucide-react";
import { useRef, useState } from "react";

import { cn } from "@/lib/cn";

type UploadDropzoneProps = {
  label?: string;
  description?: string;
  acceptedFileTypes?: readonly string[];
  busy?: boolean;
  compact?: boolean;
  onReject?: (file: File) => void;
  onUpload: (file: File) => void;
};

const defaultAcceptedFileTypes = [
  "application/pdf",
  "image/png",
  "image/jpeg",
  "text/plain",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "application/vnd.openxmlformats-officedocument.presentationml.presentation",
];

export function UploadDropzone({
  label = "Upload file",
  description = "Drop a PDF, image, or Office document here",
  acceptedFileTypes = defaultAcceptedFileTypes,
  busy = false,
  compact = false,
  onReject,
  onUpload,
}: UploadDropzoneProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [dragActive, setDragActive] = useState(false);
  const accept = acceptedFileTypes.join(",");

  function uploadFiles(files: FileList | null) {
    const file = files?.item(0);
    if (file) {
      if (!isAcceptedFile(file, acceptedFileTypes)) {
        onReject?.(file);
        return;
      }
      onUpload(file);
    }
  }

  if (compact) {
    return (
      <label className="inline-flex min-h-10 cursor-pointer items-center gap-2 rounded-card bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary-hover focus-within:outline-none focus-within:ring-2 focus-within:ring-primary focus-within:ring-offset-2 focus-within:ring-offset-background">
        <UploadCloud className="h-4 w-4" aria-hidden="true" />
        {busy ? "Uploading..." : label}
        <input
          ref={inputRef}
          type="file"
          accept={accept}
          className="sr-only"
          disabled={busy}
          onChange={(event) => {
            uploadFiles(event.currentTarget.files);
            event.currentTarget.value = "";
          }}
        />
      </label>
    );
  }

  return (
    <div
      className={cn(
        "rounded-card border border-dashed border-border bg-surface p-6 text-center shadow-card transition-colors",
        dragActive && "border-primary bg-primary-soft",
      )}
      onDragEnter={(event) => {
        event.preventDefault();
        setDragActive(true);
      }}
      onDragOver={(event) => event.preventDefault()}
      onDragLeave={() => setDragActive(false)}
      onDrop={(event) => {
        event.preventDefault();
        setDragActive(false);
        uploadFiles(event.dataTransfer.files);
      }}
    >
      <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-card bg-primary-soft text-primary">
        <UploadCloud className="h-6 w-6" aria-hidden="true" />
      </div>
      <p className="mt-3 text-sm font-semibold text-text-primary">
        {busy ? "Uploading..." : label}
      </p>
      <p className="mt-1 text-sm text-text-secondary">{description}</p>
      <button
        type="button"
        className="mt-4 inline-flex min-h-10 items-center rounded-card border border-border bg-surface px-4 text-sm font-medium text-text-primary hover:bg-surface-muted focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 focus:ring-offset-background"
        disabled={busy}
        onClick={() => inputRef.current?.click()}
      >
        Choose file
      </button>
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        className="sr-only"
        disabled={busy}
        onChange={(event) => {
          uploadFiles(event.currentTarget.files);
          event.currentTarget.value = "";
        }}
      />
    </div>
  );
}

function isAcceptedFile(file: File, acceptedFileTypes: readonly string[]) {
  if (acceptedFileTypes.includes(file.type)) {
    return true;
  }

  const fileName = file.name.toLowerCase();
  return acceptedFileTypes.some((fileType) => {
    if (!fileType.startsWith(".")) {
      return false;
    }
    return fileName.endsWith(fileType.toLowerCase());
  });
}
