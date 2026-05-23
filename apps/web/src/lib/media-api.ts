import { API_BASE_URL, workspaceHeaders } from "@/lib/api";

export type OcrJobStatus = "queued" | "processing" | "completed" | "failed";

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
