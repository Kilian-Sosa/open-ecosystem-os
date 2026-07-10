import { API_BASE_URL, workspaceHeaders } from "@/lib/api";

export type OcrJobStatus = "queued" | "processing" | "completed" | "failed";

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
};

export type OcrJobDetail = OcrJobSummary & {
  extractedText: string | null;
  nextAttemptAt: string | null;
  lifecycle: OcrJobLifecycle;
};

export type OcrJobListResponse = {
  jobs: OcrJobSummary[];
};

export async function fetchOcrJobs(): Promise<OcrJobListResponse> {
  const response = await fetch(`${API_BASE_URL}/api/media/ocr-jobs`, {
    headers: workspaceHeaders,
  });

  if (!response.ok) {
    throw new Error("OCR jobs could not be loaded");
  }

  return response.json() as Promise<OcrJobListResponse>;
}

export async function fetchOcrJob(jobId: string): Promise<OcrJobDetail> {
  const response = await fetch(`${API_BASE_URL}/api/media/ocr-jobs/${jobId}`, {
    headers: workspaceHeaders,
  });

  if (!response.ok) {
    throw new Error("OCR job could not be loaded");
  }

  return response.json() as Promise<OcrJobDetail>;
}
