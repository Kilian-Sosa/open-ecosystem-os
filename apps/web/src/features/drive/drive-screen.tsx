"use client";

import {
  File as FileIcon,
  FileText,
  Lock,
  RefreshCw,
  ShieldCheck,
} from "lucide-react";
import { useMemo, useRef, useState } from "react";

import { AppShell } from "@/components/layout/app-shell";
import {
  EmptyState,
  ErrorState,
  LoadingState,
  MobileBottomSheet,
  PageHeader,
  PermissionDeniedState,
  RightInspectorPanel,
  SearchInput,
  SectionCard,
  StatusChip,
  UploadDropzone,
} from "@/components/ui";
import { DriveUploadError, type DriveFile } from "@/lib/drive-api";
import { driveMockFiles, type DriveState } from "@/lib/drive-mock-data";
import { cn } from "@/lib/cn";
import { useDriveFiles, useUploadDriveFile } from "./use-drive-files";

type DriveScreenProps = {
  initialFileId?: string;
  stateOverride?: DriveState;
};

export function DriveScreen({
  initialFileId,
  stateOverride,
}: DriveScreenProps) {
  const filesQuery = useDriveFiles(stateOverride === undefined);
  const [selectedFileId, setSelectedFileId] = useState<string | null>(
    initialFileId ?? null,
  );
  const [mobileSheetOpen, setMobileSheetOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [rejectedUploadMessage, setRejectedUploadMessage] = useState<
    string | null
  >(null);
  const uploadMutation = useUploadDriveFile((file) => {
    setSearchQuery("");
    setSelectedFileId(file.fileId);
    setRejectedUploadMessage(null);
  });

  const files = useMemo(() => {
    if (stateOverride === "normal") {
      return driveMockFiles;
    }
    if (stateOverride === "empty") {
      return [];
    }
    return filesQuery.data?.files ?? [];
  }, [filesQuery.data?.files, stateOverride]);

  const filteredFiles = useMemo(() => {
    const normalizedQuery = searchQuery.trim().toLowerCase();
    if (!normalizedQuery) {
      return files;
    }

    return files.filter((file) =>
      [file.name, fileKind(file.contentType), file.contentType].some((value) =>
        value.toLowerCase().includes(normalizedQuery),
      ),
    );
  }, [files, searchQuery]);
  const selectedFile =
    filteredFiles.find((file) => file.fileId === selectedFileId) ??
    filteredFiles[0] ??
    null;
  const state = resolveState(
    stateOverride,
    filesQuery.isPending,
    filesQuery.isError,
    files,
  );
  const inspector =
    state === "normal" && selectedFile ? (
      <DriveFileInspector file={selectedFile} />
    ) : undefined;
  const uploadErrorMessage = uploadMutation.isError
    ? uploadMutation.error instanceof DriveUploadError
      ? uploadMutation.error.message
      : "Drive file could not be uploaded."
    : null;
  const uploadFeedback = rejectedUploadMessage ?? uploadErrorMessage;

  function handleUpload(file: File) {
    setRejectedUploadMessage(null);
    uploadMutation.mutate(file);
  }

  function handleRejectedUpload() {
    uploadMutation.reset();
    setRejectedUploadMessage(
      "This file type is not supported. Choose a PDF, PNG, JPEG, text, Word, Excel, or PowerPoint file.",
    );
  }

  return (
    <AppShell activeHref="/app/drive" inspector={inspector}>
      <div className="space-y-6">
        <PageHeader
          title="Drive"
          subtitle="Upload, inspect, and trace workspace files for the invoice automation path."
          chips={
            <>
              <StatusChip status="active" label="Workspace files" />
              <StatusChip status="healthy" label="Encrypted at rest" />
            </>
          }
          primaryAction={
            <UploadDropzone
              compact
              busy={uploadMutation.isPending}
              onReject={handleRejectedUpload}
              onUpload={handleUpload}
            />
          }
        />

        {uploadFeedback ? (
          <div
            role="alert"
            className="rounded-card border border-danger-soft bg-danger-soft p-4 text-sm text-text-primary"
          >
            {uploadFeedback}
          </div>
        ) : null}

        {state === "loading" ? (
          <LoadingState label="Loading Drive files" />
        ) : state === "empty" ? (
          <EmptyState
            title="No files uploaded yet"
            description="Upload the first invoice or workspace document to start the Drive slice."
            action={
              <UploadDropzone
                compact
                label="Upload first file by clicking or dragging here"
                busy={uploadMutation.isPending}
                onReject={handleRejectedUpload}
                onUpload={handleUpload}
              />
            }
          />
        ) : state === "error" ? (
          <ErrorState
            title="Drive files could not load"
            description="The Drive API did not return a file list for this workspace."
            action={
              <button
                type="button"
                className="inline-flex min-h-10 items-center gap-2 rounded-card border border-border-strong bg-surface px-4 text-sm font-medium text-text-primary hover:bg-surface-muted"
                onClick={() => filesQuery.refetch()}
              >
                <RefreshCw className="h-4 w-4" aria-hidden="true" />
                Retry Drive
              </button>
            }
          />
        ) : state === "permission-denied" ? (
          <PermissionDeniedState
            title="Drive access is not available"
            description="The current workspace role cannot view or upload files."
          />
        ) : (
          <DriveNormalState
            files={files}
            filteredFiles={filteredFiles}
            selectedFile={selectedFile}
            searchQuery={searchQuery}
            uploadBusy={uploadMutation.isPending}
            onSearchChange={setSearchQuery}
            onReject={handleRejectedUpload}
            onUpload={handleUpload}
            onSelect={(file) => {
              setSelectedFileId(file.fileId);
              setMobileSheetOpen(true);
            }}
          />
        )}
      </div>

      <MobileBottomSheet
        title={selectedFile?.name ?? "File details"}
        open={state === "normal" && mobileSheetOpen && selectedFile !== null}
        onClose={() => setMobileSheetOpen(false)}
      >
        {selectedFile ? <DriveFileDetails file={selectedFile} /> : null}
      </MobileBottomSheet>
    </AppShell>
  );
}

function resolveState(
  override: DriveState | undefined,
  loading: boolean,
  error: boolean,
  files: DriveFile[],
): DriveState {
  if (override) {
    return override;
  }
  if (loading) {
    return "loading";
  }
  if (error) {
    return "error";
  }
  return files.length === 0 ? "empty" : "normal";
}

function DriveNormalState({
  files,
  filteredFiles,
  selectedFile,
  searchQuery,
  uploadBusy,
  onSearchChange,
  onReject,
  onUpload,
  onSelect,
}: {
  files: DriveFile[];
  filteredFiles: DriveFile[];
  selectedFile: DriveFile | null;
  searchQuery: string;
  uploadBusy: boolean;
  onSearchChange: (query: string) => void;
  onReject: () => void;
  onUpload: (file: File) => void;
  onSelect: (file: DriveFile) => void;
}) {
  const hasSearchQuery = searchQuery.trim().length > 0;
  const searchInputRef = useRef<HTMLInputElement>(null);

  function handleClearSearch() {
    onSearchChange("");
    searchInputRef.current?.focus();
  }

  return (
    <div className="space-y-6">
      <section className="grid gap-4 sm:grid-cols-3">
        <DriveMetric
          label="Files"
          value={files.length.toString()}
          detail="Current workspace"
        />
        <DriveMetric
          label="Stored"
          value={formatBytes(
            files.reduce((total, file) => total + file.sizeBytes, 0),
          )}
          detail="Encrypted objects"
        />
        <DriveMetric
          label="Selected"
          value={selectedFile ? fileKind(selectedFile.contentType) : "None"}
          detail={selectedFile?.name ?? "No selection"}
        />
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
        <SectionCard
          title="Files"
          description="Newest uploads in this workspace."
          action={<StatusChip status="completed" label="Encrypted" />}
        >
          <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
            <SearchInput
              ref={searchInputRef}
              aria-label="Search Drive files"
              className="min-w-0 flex-1"
              placeholder="Search files..."
              value={searchQuery}
              onChange={(event) => onSearchChange(event.target.value)}
            />
          </div>

          {hasSearchQuery && filteredFiles.length === 0 ? (
            <div
              role="status"
              className="rounded-card border border-border bg-surface-muted p-4"
            >
              <p className="font-medium text-text-primary">No matching files</p>
              <p className="mt-1 text-sm text-text-secondary">
                Try another file name or type, or clear the search.
              </p>
              <button
                type="button"
                className="mt-3 inline-flex min-h-10 items-center rounded-card border border-border-strong bg-surface px-4 text-sm font-medium text-text-primary hover:bg-surface"
                onClick={handleClearSearch}
              >
                Clear search
              </button>
            </div>
          ) : (
            <>
              <div className="hidden overflow-hidden rounded-card border border-border md:block">
                <table className="w-full text-left text-sm">
                  <thead className="bg-surface-muted text-xs font-medium uppercase tracking-normal text-text-secondary">
                    <tr>
                      <th className="px-4 py-3">Name</th>
                      <th className="px-4 py-3">Type</th>
                      <th className="px-4 py-3">Uploaded</th>
                      <th className="px-4 py-3 text-right">Size</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border">
                    {filteredFiles.map((file) => (
                      <tr
                        key={file.fileId}
                        className={cn(
                          "bg-surface",
                          selectedFile?.fileId === file.fileId &&
                            "bg-primary-soft/50",
                        )}
                      >
                        <td className="px-4 py-3">
                          <button
                            type="button"
                            aria-label={`${file.name}, ${fileKind(file.contentType)}${
                              selectedFile?.fileId === file.fileId
                                ? ", selected"
                                : ""
                            }`}
                            className="flex min-w-0 items-center gap-3 text-left"
                            onClick={() => onSelect(file)}
                          >
                            <FileBadge contentType={file.contentType} />
                            <span className="truncate font-medium text-text-primary">
                              {file.name}
                            </span>
                          </button>
                        </td>
                        <td className="px-4 py-3 text-text-secondary">
                          {fileKind(file.contentType)}
                        </td>
                        <td className="px-4 py-3 text-text-secondary">
                          {formatDate(file.uploadedAt)}
                        </td>
                        <td className="px-4 py-3 text-right text-text-secondary">
                          {formatBytes(file.sizeBytes)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="space-y-3 md:hidden">
                {filteredFiles.map((file) => (
                  <button
                    key={file.fileId}
                    type="button"
                    aria-label={`${file.name}, ${fileKind(file.contentType)}${
                      selectedFile?.fileId === file.fileId ? ", selected" : ""
                    }`}
                    className={cn(
                      "w-full rounded-card border border-border bg-surface p-4 text-left shadow-card",
                      selectedFile?.fileId === file.fileId &&
                        "border-primary bg-primary-soft/50",
                    )}
                    onClick={() => onSelect(file)}
                  >
                    <div className="flex items-start gap-3">
                      <FileBadge contentType={file.contentType} />
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-semibold text-text-primary">
                          {file.name}
                        </p>
                        <p className="mt-1 text-xs text-text-secondary">
                          {formatBytes(file.sizeBytes)} -{" "}
                          {formatDate(file.uploadedAt)}
                        </p>
                      </div>
                      <Lock
                        className="h-4 w-4 text-success"
                        aria-hidden="true"
                      />
                    </div>
                  </button>
                ))}
              </div>
            </>
          )}
        </SectionCard>

        <div className="space-y-4">
          <UploadDropzone
            busy={uploadBusy}
            onReject={onReject}
            onUpload={onUpload}
          />
        </div>
      </div>
    </div>
  );
}

function DriveMetric({
  label,
  value,
  detail,
}: {
  label: string;
  value: string;
  detail: string;
}) {
  return (
    <div className="rounded-card border border-border bg-surface p-4 shadow-card">
      <p className="text-xs font-medium uppercase tracking-normal text-text-muted">
        {label}
      </p>
      <p className="mt-2 text-2xl font-semibold text-text-primary">{value}</p>
      <p className="mt-1 truncate text-sm text-text-secondary">{detail}</p>
    </div>
  );
}

function DriveFileInspector({ file }: { file: DriveFile }) {
  return (
    <RightInspectorPanel
      title="File details"
      description="Selected Drive object metadata."
    >
      <DriveFileDetails file={file} />
    </RightInspectorPanel>
  );
}

function DriveFileDetails({ file }: { file: DriveFile }) {
  return (
    <div className="space-y-5">
      <div className="rounded-card border border-border bg-surface-muted p-4">
        <div className="flex items-start gap-3">
          <FileBadge contentType={file.contentType} />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-text-primary">
              {file.name}
            </p>
            <p className="mt-1 text-xs text-text-secondary">
              {fileKind(file.contentType)}
            </p>
          </div>
        </div>
      </div>

      <dl className="space-y-3 text-sm">
        <DetailRow label="Size" value={formatBytes(file.sizeBytes)} />
        <DetailRow label="Uploaded" value={formatDate(file.uploadedAt)} />
        <DetailRow label="Updated" value={formatDate(file.updatedAt)} />
        <DetailRow
          label="Checksum"
          value={shortChecksum(file.checksumSha256)}
        />
      </dl>

      <div className="rounded-card border border-border bg-surface p-4">
        <div className="flex items-center gap-2">
          <ShieldCheck className="h-4 w-4 text-success" aria-hidden="true" />
          <p className="text-sm font-semibold text-text-primary">Encrypted</p>
        </div>
        <p className="mt-2 text-sm leading-5 text-text-secondary">
          File content and stored filename are protected before object storage.
        </p>
      </div>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-start justify-between gap-3">
      <dt className="text-text-secondary">{label}</dt>
      <dd className="break-all text-right font-medium text-text-primary">
        {value}
      </dd>
    </div>
  );
}

function FileBadge({ contentType }: { contentType: string }) {
  const kind = fileKind(contentType);
  const Icon = kind === "Text" ? FileText : FileIcon;

  return (
    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-card bg-info-soft text-info">
      {kind === "PDF" || kind === "XLSX" ? (
        <span className="text-[10px] font-semibold">{kind}</span>
      ) : (
        <Icon className="h-4 w-4" aria-hidden="true" />
      )}
    </span>
  );
}

function fileKind(contentType: string) {
  if (contentType === "application/pdf") {
    return "PDF";
  }
  if (contentType.includes("spreadsheet")) {
    return "XLSX";
  }
  if (contentType.includes("wordprocessing")) {
    return "DOCX";
  }
  if (contentType.includes("presentation")) {
    return "PPTX";
  }
  if (contentType.startsWith("image/")) {
    return "Image";
  }
  if (contentType === "text/plain") {
    return "Text";
  }
  return "File";
}

function formatBytes(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const kilobytes = bytes / 1024;
  if (kilobytes < 1024) {
    return `${kilobytes.toFixed(kilobytes >= 10 ? 0 : 1)} KB`;
  }
  const megabytes = kilobytes / 1024;
  return `${megabytes.toFixed(megabytes >= 10 ? 0 : 1)} MB`;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}

function shortChecksum(value: string) {
  return `${value.slice(0, 12)}...${value.slice(-8)}`;
}
