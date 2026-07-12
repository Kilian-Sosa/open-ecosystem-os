"use client";

import { FileText, Image as ImageIcon, RefreshCw } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

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
import { cn } from "@/lib/cn";
import {
  isActiveOcrJob,
  OcrApiError,
  shouldPollOcrJobDetail,
  shouldPollOcrJobs,
  type OcrJobDetail,
  type OcrJobStatus,
  type OcrJobSummary,
} from "@/lib/media-api";
import { MediaJobDetails } from "./media-job-details";
import {
  useOcrJobDetail,
  useOcrJobs,
  useUploadOcrSourceFile,
} from "./use-ocr-jobs";

type MediaScreenProps = {
  initialFileId?: string;
  initialJobId?: string;
};

const OCR_SOURCE_FILE_TYPES = [
  "application/pdf",
  "image/png",
  "image/jpeg",
] as const;
const OCR_JOB_POLL_INTERVAL_MS = 1500;
const OCR_JOB_WAIT_TIMEOUT_MS = 20000;

type UploadFeedback = {
  tone: "info" | "warning";
  message: string;
};

type PendingUpload = {
  fileId: string;
  fileName: string;
};

type TrackedUpload = PendingUpload & {
  jobId: string;
};

export function MediaScreen({ initialFileId, initialJobId }: MediaScreenProps) {
  const [pendingUpload, setPendingUpload] = useState<PendingUpload | null>(
    null,
  );
  const [trackedUpload, setTrackedUpload] = useState<TrackedUpload | null>(
    null,
  );
  const [selectedJobId, setSelectedJobId] = useState<string | null>(
    initialJobId ?? null,
  );
  const [mobileSheetOpen, setMobileSheetOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [uploadFeedback, setUploadFeedback] = useState<UploadFeedback | null>(
    null,
  );
  const jobsQuery = useOcrJobs(true, (queryState) =>
    shouldPollOcrJobs(queryState.state.data?.jobs ?? [])
      ? OCR_JOB_POLL_INTERVAL_MS
      : false,
  );
  const uploadMutation = useUploadOcrSourceFile((file) => {
    setPendingUpload({ fileId: file.fileId, fileName: file.name });
    setUploadFeedback({
      tone: "info",
      message: `Upload complete. Waiting for the OCR job for ${file.name}...`,
    });
  });

  const jobs = useMemo(
    () => jobsQuery.data?.jobs ?? [],
    [jobsQuery.data?.jobs],
  );
  const filteredJobs = useMemo(() => {
    const normalized = query.trim().toLowerCase();
    if (!normalized) {
      return jobs;
    }
    return jobs.filter(
      (job) =>
        job.fileName.toLowerCase().includes(normalized) ||
        job.jobId.toLowerCase().includes(normalized) ||
        job.status.toLowerCase().includes(normalized) ||
        job.provider?.toLowerCase().includes(normalized),
    );
  }, [jobs, query]);

  const linkedInitialJobId = useMemo(() => {
    if (selectedJobId || !initialFileId) return null;

    return jobs.find((job) => job.fileId === initialFileId)?.jobId ?? null;
  }, [initialFileId, jobs, selectedJobId]);
  const effectiveSelectedJobId = selectedJobId ?? linkedInitialJobId;
  const selectedSummary =
    filteredJobs.find((job) => job.jobId === effectiveSelectedJobId) ??
    filteredJobs[0] ??
    null;
  const selectedDetailQuery = useOcrJobDetail(
    selectedSummary?.jobId ?? null,
    selectedSummary !== null,
    (queryState) =>
      shouldPollOcrJobDetail(queryState.state.data ?? selectedSummary)
        ? OCR_JOB_POLL_INTERVAL_MS
        : false,
  );
  const selectedJob = selectedDetailQuery.data ?? selectedSummary;
  const detailLoading =
    selectedSummary !== null && selectedDetailQuery.isPending;
  const detailError =
    selectedSummary !== null && selectedDetailQuery.isError
      ? selectedDetailQuery.error
      : null;
  const state = resolvePageState(jobsQuery.isPending, jobsQuery.error, jobs);
  const inspector =
    state === "normal" && selectedJob ? (
      <MediaJobInspector
        job={selectedJob}
        detailLoading={detailLoading}
        detailError={detailError}
      />
    ) : undefined;
  const pendingOcrJob = useMemo(() => {
    if (!pendingUpload) {
      return null;
    }
    return (
      jobs.find((candidate) => candidate.fileId === pendingUpload.fileId) ??
      null
    );
  }, [jobs, pendingUpload]);

  useEffect(() => {
    if (!pendingUpload || !pendingOcrJob) {
      return;
    }

    const timeout = window.setTimeout(() => {
      setSelectedJobId(pendingOcrJob.jobId);
      setTrackedUpload({
        fileId: pendingUpload.fileId,
        fileName: pendingUpload.fileName,
        jobId: pendingOcrJob.jobId,
      });
      setUploadFeedback({
        tone: "info",
        message: `OCR job created for ${pendingUpload.fileName}. Tracking status...`,
      });
      setPendingUpload(null);
    }, 0);

    return () => window.clearTimeout(timeout);
  }, [pendingOcrJob, pendingUpload]);

  useEffect(() => {
    if (!trackedUpload) {
      return;
    }

    const trackedJob = jobs.find((job) => job.jobId === trackedUpload.jobId);
    if (!trackedJob || isActiveOcrJob(trackedJob)) {
      return;
    }

    const timeout = window.setTimeout(() => {
      setUploadFeedback(
        trackedJob.status === "completed"
          ? {
              tone: "info",
              message: `OCR completed for ${trackedUpload.fileName}.`,
            }
          : {
              tone: "warning",
              message: `OCR failed for ${trackedUpload.fileName}.`,
            },
      );
      setTrackedUpload(null);
    }, 0);

    return () => window.clearTimeout(timeout);
  }, [jobs, trackedUpload]);

  useEffect(() => {
    if (uploadFeedback?.message.startsWith("OCR completed for")) {
      const timeout = window.setTimeout(() => setUploadFeedback(null), 4000);
      return () => window.clearTimeout(timeout);
    }
  }, [uploadFeedback]);

  useEffect(() => {
    if (!pendingUpload) {
      return;
    }

    const timeout = window.setTimeout(() => {
      setUploadFeedback({
        tone: "warning",
        message:
          "Upload succeeded, but the OCR job has not appeared yet. It may still be moving through the event queue.",
      });
      setPendingUpload(null);
    }, OCR_JOB_WAIT_TIMEOUT_MS);

    return () => window.clearTimeout(timeout);
  }, [pendingUpload]);

  function handleUpload(file: File) {
    setPendingUpload(null);
    setTrackedUpload(null);
    setUploadFeedback(null);
    uploadMutation.mutate(file);
  }

  function handleRejectedUpload(file: File) {
    setPendingUpload(null);
    setTrackedUpload(null);
    setUploadFeedback({
      tone: "warning",
      message: `${file.name} was not uploaded. OCR accepts PDF, PNG, and JPEG files only.`,
    });
  }

  return (
    <AppShell activeHref="/app/media" inspector={inspector}>
      <div className="space-y-6">
        <PageHeader
          title="Media and OCR"
          subtitle="Track document OCR jobs created from Drive uploads."
          chips={<StatusChip status="queued" label="Event-driven OCR" />}
          primaryAction={
            <UploadDropzone
              compact
              label="Upload OCR file"
              acceptedFileTypes={OCR_SOURCE_FILE_TYPES}
              busy={uploadMutation.isPending}
              onReject={handleRejectedUpload}
              onUpload={handleUpload}
            />
          }
        />

        {uploadFeedback ? (
          <UploadFeedbackBanner feedback={uploadFeedback} />
        ) : null}

        {state === "loading" ? (
          <LoadingState label="Loading OCR jobs" />
        ) : state === "empty" ? (
          <EmptyState
            title="No OCR jobs yet"
            description="Upload a PDF or image in Drive or from this page to create the first OCR job."
            action={
              <UploadDropzone
                compact
                label="Upload first OCR file"
                acceptedFileTypes={OCR_SOURCE_FILE_TYPES}
                busy={uploadMutation.isPending}
                onReject={handleRejectedUpload}
                onUpload={handleUpload}
              />
            }
          />
        ) : state === "error" ? (
          <ErrorState
            title="OCR jobs could not load"
            description="The Media/OCR API did not return the job queue for this workspace."
            action={
              <button
                type="button"
                className="inline-flex min-h-10 items-center gap-2 rounded-card border border-border-strong bg-surface px-4 text-sm font-medium text-text-primary hover:bg-surface-muted focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                onClick={() => jobsQuery.refetch()}
              >
                <RefreshCw className="h-4 w-4" aria-hidden="true" />
                Retry OCR jobs
              </button>
            }
          />
        ) : state === "permission-denied" ? (
          <PermissionDeniedState
            title="Media/OCR access is not available"
            description="The current workspace role cannot view OCR job status or extracted text."
          />
        ) : (
          <MediaNormalState
            jobs={filteredJobs}
            allJobs={jobs}
            selectedJob={selectedJob}
            query={query}
            uploadBusy={uploadMutation.isPending}
            uploadError={uploadMutation.isError}
            onQueryChange={setQuery}
            onRejectUpload={handleRejectedUpload}
            onUpload={handleUpload}
            onSelect={(job) => {
              setSelectedJobId(job.jobId);
              setMobileSheetOpen(true);
            }}
          />
        )}
      </div>

      <MobileBottomSheet
        title={selectedJob?.fileName ?? "OCR job"}
        open={state === "normal" && mobileSheetOpen && selectedJob !== null}
        onClose={() => setMobileSheetOpen(false)}
      >
        {selectedJob ? (
          <MediaJobDetails
            job={selectedJob}
            detailLoading={detailLoading}
            detailError={detailError}
          />
        ) : null}
      </MobileBottomSheet>
    </AppShell>
  );
}

function resolvePageState(
  loading: boolean,
  error: unknown,
  jobs: OcrJobSummary[],
) {
  if (loading) {
    return "loading";
  }
  if (error) {
    return isPermissionError(error) ? "permission-denied" : "error";
  }
  return jobs.length === 0 ? "empty" : "normal";
}

function isPermissionError(error: unknown) {
  return (
    error instanceof OcrApiError &&
    (error.status === 401 || error.status === 403)
  );
}

function MediaNormalState({
  jobs,
  allJobs,
  selectedJob,
  query,
  uploadBusy,
  uploadError,
  onQueryChange,
  onRejectUpload,
  onUpload,
  onSelect,
}: {
  jobs: OcrJobSummary[];
  allJobs: OcrJobSummary[];
  selectedJob: OcrJobSummary | OcrJobDetail | null;
  query: string;
  uploadBusy: boolean;
  uploadError: boolean;
  onQueryChange: (query: string) => void;
  onRejectUpload: (file: File) => void;
  onUpload: (file: File) => void;
  onSelect: (job: OcrJobSummary) => void;
}) {
  const completed = allJobs.filter((job) => job.status === "completed").length;
  const active = allJobs.filter(isActiveOcrJob).length;
  const failed = allJobs.filter((job) => job.status === "failed").length;

  return (
    <div className="space-y-6">
      <section className="grid gap-4 sm:grid-cols-3">
        <MediaMetric
          label="Jobs"
          value={allJobs.length.toString()}
          detail="Current workspace"
        />
        <MediaMetric
          label="Active"
          value={active.toString()}
          detail="Queued or processing"
        />
        <MediaMetric
          label="Completed"
          value={completed.toString()}
          detail={`${failed} failed`}
        />
      </section>

      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_320px]">
        <SectionCard
          title="OCR jobs"
          description="PDF and image uploads that entered the OCR pipeline."
          action={<StatusChip status="processing" label="OCR queue" />}
        >
          <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center">
            <SearchInput
              aria-label="Search OCR jobs"
              className="min-w-0 flex-1"
              placeholder="Search jobs..."
              value={query}
              onChange={(event) => onQueryChange(event.currentTarget.value)}
            />
          </div>

          <div className="hidden overflow-hidden rounded-card border border-border md:block">
            <table className="w-full text-left text-sm">
              <thead className="bg-surface-muted text-xs font-medium uppercase tracking-normal text-text-secondary">
                <tr>
                  <th className="px-4 py-3">File</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Provider</th>
                  <th className="px-4 py-3">Extraction</th>
                  <th className="px-4 py-3">Updated</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {jobs.map((job) => (
                  <tr
                    key={job.jobId}
                    className={cn(
                      "bg-surface",
                      selectedJob?.jobId === job.jobId && "bg-primary-soft/50",
                    )}
                  >
                    <td className="px-4 py-3">
                      <button
                        type="button"
                        className="flex min-w-0 items-center gap-3 rounded-card text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                        onClick={() => onSelect(job)}
                      >
                        <MediaFileBadge contentType={job.contentType} />
                        <span className="min-w-0">
                          <span className="block truncate font-medium text-text-primary">
                            {job.fileName}
                          </span>
                          <span className="block text-xs text-text-secondary">
                            {job.jobId}
                          </span>
                        </span>
                      </button>
                    </td>
                    <td className="px-4 py-3">
                      <StatusChip
                        status={job.status}
                        label={formatStatus(job.status)}
                      />
                    </td>
                    <td className="px-4 py-3 text-text-secondary">
                      {job.provider ?? "Pending"}
                    </td>
                    <td className="px-4 py-3">
                      <ExtractionSummary job={job} />
                    </td>
                    <td className="px-4 py-3 text-text-secondary">
                      {formatDate(job.updatedAt)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="space-y-3 md:hidden">
            {jobs.map((job) => (
              <button
                key={job.jobId}
                type="button"
                className="w-full rounded-card border border-border bg-surface p-4 text-left shadow-card focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
                onClick={() => onSelect(job)}
              >
                <div className="flex items-start gap-3">
                  <MediaFileBadge contentType={job.contentType} />
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold text-text-primary">
                      {job.fileName}
                    </p>
                    <p className="mt-1 text-xs text-text-secondary">
                      {job.provider ?? "Provider pending"} ·{" "}
                      {formatDate(job.updatedAt)}
                    </p>
                  </div>
                  <StatusChip
                    status={job.status}
                    label={formatStatus(job.status)}
                  />
                </div>
                <div className="mt-3">
                  <ExtractionSummary job={job} />
                </div>
              </button>
            ))}
          </div>
        </SectionCard>

        <div className="space-y-4">
          <UploadDropzone
            label="Upload PDF or image"
            description="The Drive upload emits FileUploaded, then OCR queues from the event."
            acceptedFileTypes={OCR_SOURCE_FILE_TYPES}
            busy={uploadBusy}
            onReject={onRejectUpload}
            onUpload={onUpload}
          />
          {uploadError ? (
            <div className="rounded-card border border-danger-soft bg-danger-soft p-4 text-sm text-danger">
              Upload failed. Check the file type and size, then try again.
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}

function ExtractionSummary({ job }: { job: OcrJobSummary }) {
  if (job.extractionStatus === "review_required") {
    return <StatusChip status="review-required" label="Review required" />;
  }
  if (job.extractionStatus === "completed") {
    return <StatusChip status="completed" label="Completed" />;
  }
  return <span className="text-xs text-text-secondary">Pending</span>;
}

function UploadFeedbackBanner({ feedback }: { feedback: UploadFeedback }) {
  return (
    <div
      className={cn(
        "rounded-card border p-4 text-sm",
        feedback.tone === "warning"
          ? "border-warning-soft bg-warning-soft text-warning"
          : "border-info-soft bg-info-soft text-info",
      )}
    >
      {feedback.message}
    </div>
  );
}

function MediaMetric({
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

function MediaJobInspector({
  job,
  detailLoading,
  detailError,
}: {
  job: OcrJobSummary | OcrJobDetail;
  detailLoading: boolean;
  detailError: unknown;
}) {
  return (
    <RightInspectorPanel
      title="OCR job detail"
      description="Selected job status, OCR result, and structured extraction."
    >
      <MediaJobDetails
        job={job}
        detailLoading={detailLoading}
        detailError={detailError}
      />
    </RightInspectorPanel>
  );
}

function MediaFileBadge({ contentType }: { contentType: string }) {
  const kind = fileKind(contentType);
  const Icon = kind === "Image" ? ImageIcon : FileText;

  return (
    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-card bg-info-soft text-info">
      {kind === "PDF" ? (
        <span className="text-[10px] font-semibold">PDF</span>
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
  if (contentType.startsWith("image/")) {
    return "Image";
  }
  return "File";
}

function formatStatus(status: OcrJobStatus) {
  return status.charAt(0).toUpperCase() + status.slice(1);
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en", {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}
