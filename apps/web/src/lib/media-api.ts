import { API_BASE_URL, workspaceHeaders } from "@/lib/api";

export type OcrJobStatus = "queued" | "processing" | "completed" | "failed";
export type OcrExtractionStatus = "completed" | "review_required";

export class OcrApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = "OcrApiError";
  }
}

export type OcrLifecycleState = "active" | "complete" | "partial";
export type OcrLifecycleOutcome = "in_progress" | "completed" | "failed";
export type OcrLifecyclePhase =
  | "upload"
  | "ocr"
  | "workflow"
  | "notification"
  | "search"
  | "audit";
export type OcrLifecycleKind =
  | "event"
  | "job"
  | "workflow_execution"
  | "workflow_step"
  | "audit"
  | "pending"
  | "unknown";

export type OcrLifecycleConsumption = {
  consumer: string;
  state: "consumption_recorded";
  consumedAt: string;
};

export type OcrLifecycleEvent = {
  eventId: string;
  eventType: string;
  eventVersion: number;
  correlationId: string;
  causationId: string | null;
  publicationState: "outbox_pending" | "publish_recorded";
  publishedAt: string | null;
  consumptions: OcrLifecycleConsumption[];
};

export type OcrLifecycleWorkflow = {
  executionId: string;
  workflowId: string;
  workflowVersionId: string | null;
  workflowVersionNumber: number;
  stepKey: string | null;
  actionType: string | null;
  retryCount: number;
};

export type OcrLifecycleRetry = {
  attemptCount: number;
  maxAttempts: number;
  nextAttemptAt: string | null;
};

export type OcrLifecycleFailure = {
  code: string | null;
  reason: string | null;
};

export type OcrLifecycleResource = {
  resourceType: string;
  resourceId: string | null;
};

export type OcrLifecycleEntry = {
  entryId: string;
  phase: OcrLifecyclePhase;
  kind: OcrLifecycleKind;
  label: string;
  status: string;
  observed: boolean;
  occurredAt: string | null;
  source: string;
  event: OcrLifecycleEvent | null;
  workflow: OcrLifecycleWorkflow | null;
  retry: OcrLifecycleRetry | null;
  failure: OcrLifecycleFailure | null;
  resource: OcrLifecycleResource | null;
};

export type OcrJobLifecycle = {
  state: OcrLifecycleState;
  outcome: OcrLifecycleOutcome;
  entries: OcrLifecycleEntry[];
};

export type OcrJobSummary = {
  jobId: string;
  fileId: string;
  fileName: string;
  contentType: string;
  status: OcrJobStatus;
  provider: string | null;
  attemptCount: number;
  maxAttempts: number;
  extractedTextLength: number | null;
  failureCode: string | null;
  failureMessage: string | null;
  correlationId: string;
  queuedAt: string;
  processingStartedAt: string | null;
  completedAt: string | null;
  failedAt: string | null;
  updatedAt: string;
  ocrResultPresent: boolean;
  extractionPresent: boolean;
  extractionStatus: OcrExtractionStatus | null;
  reviewRequired: boolean;
};

export type OcrJobDetail = OcrJobSummary & {
  extractedText: string | null;
  nextAttemptAt: string | null;
  lifecycle: OcrJobLifecycle;
  ocrResult: {
    ocrResultId: string;
    provider: string;
    providerVersion: string;
    pageCount: number;
    wordCount: number;
  } | null;
  extraction: {
    extractionId: string;
    extractor: string;
    extractorVersion: string;
    status: OcrExtractionStatus;
    reviewRequired: boolean;
    confidence: number | null;
    fieldCount: number;
    warningCount: number;
    warnings: Array<{
      code: string;
      fieldKey: string | null;
      message: string;
    }>;
    fields: Array<{
      fieldKey: string;
      label: string;
      displayValue: string;
      normalizedValue: string;
      status: "extracted" | "low_confidence";
      confidence: number | null;
      provenance: Array<{
        sourceRole: "label" | "value";
        ocrWordId: string;
        pageNumber: number;
        blockNumber: number;
        paragraphNumber: number;
        lineNumber: number;
        wordNumber: number;
        readingOrder: number;
        sourceKind: "pdf_text_layer" | "tesseract_tsv";
      }>;
    }>;
  } | null;
};

export type OcrJobListResponse = {
  jobs: OcrJobSummary[];
};

export async function fetchOcrJobs(): Promise<OcrJobListResponse> {
  const response = await fetch(`${API_BASE_URL}/api/media/ocr-jobs`, {
    headers: workspaceHeaders,
  });

  if (!response.ok) {
    throw new OcrApiError("OCR jobs could not be loaded", response.status);
  }

  return response.json() as Promise<OcrJobListResponse>;
}

export async function fetchOcrJob(jobId: string): Promise<OcrJobDetail> {
  const response = await fetch(`${API_BASE_URL}/api/media/ocr-jobs/${jobId}`, {
    headers: workspaceHeaders,
  });

  if (!response.ok) {
    throw new OcrApiError("OCR job could not be loaded", response.status);
  }

  return response.json() as Promise<OcrJobDetail>;
}

export function isActiveOcrJob(job: OcrJobSummary | OcrJobDetail) {
  return job.status === "queued" || job.status === "processing";
}

export function shouldPollOcrJobs(jobs: OcrJobSummary[]) {
  return jobs.some(isActiveOcrJob);
}

export function shouldPollOcrJobDetail(
  job: OcrJobSummary | OcrJobDetail | null,
  now = new Date(),
) {
  if (!job || isTerminalExtraction(job.extractionStatus)) {
    return false;
  }

  if (isActiveOcrJob(job)) {
    return true;
  }

  if (job.status !== "completed" || job.extractionPresent || !job.completedAt) {
    return false;
  }

  const completedAt = Date.parse(job.completedAt);
  return Number.isFinite(completedAt) && now.getTime() - completedAt < 30_000;
}

function isTerminalExtraction(status: OcrExtractionStatus | null) {
  return status === "completed" || status === "review_required";
}
