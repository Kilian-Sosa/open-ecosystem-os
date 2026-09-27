import { isActiveOcrJob, type OcrJobSummary } from "@/lib/media-api";

export type PollingPhase = "discovery" | "active-job" | "awaiting-extraction";

export type PollingExpiryReason =
  | "discovery-expired"
  | "active-job-expired"
  | "awaiting-extraction-expired";

export type PollingWindow = {
  phase: PollingPhase;
  startedAtMs: number;
  attempts: number;
  sourceFileId?: string;
  jobId?: string;
};

type PollingDecision = {
  poll: boolean;
  intervalMs: number | null;
  reason: PollingExpiryReason | null;
  window: PollingWindow;
  matchedJob: OcrJobSummary | null;
};

const POLLING_POLICIES = {
  discovery: { intervalMs: 2_000, timeoutMs: 30_000 },
  "active-job": { intervalMs: 5_000, timeoutMs: 15 * 60_000 },
  "awaiting-extraction": { intervalMs: 2_000, timeoutMs: 30_000 },
} as const;

export function nextPollingState(
  window: PollingWindow,
  jobs: OcrJobSummary[],
  nowMs: number,
): PollingDecision {
  const job = findTrackedJob(window, jobs);
  const nextWindow = transitionWindow(window, job, nowMs);

  if (!nextWindow) {
    return {
      poll: false,
      intervalMs: null,
      reason: null,
      window,
      matchedJob: job,
    };
  }

  const policy = POLLING_POLICIES[nextWindow.phase];
  if (nowMs - nextWindow.startedAtMs >= policy.timeoutMs) {
    return {
      poll: false,
      intervalMs: null,
      reason: expiryReason(nextWindow.phase),
      window: nextWindow,
      matchedJob: job,
    };
  }

  return {
    poll: true,
    intervalMs: policy.intervalMs,
    reason: null,
    window:
      nextWindow === window
        ? { ...nextWindow, attempts: nextWindow.attempts + 1 }
        : nextWindow,
    matchedJob: job,
  };
}

export function pollingIntervalFor(window: PollingWindow) {
  return POLLING_POLICIES[window.phase].intervalMs;
}

function findTrackedJob(window: PollingWindow, jobs: OcrJobSummary[]) {
  if (window.phase === "discovery") {
    return jobs.find((job) => job.fileId === window.sourceFileId) ?? null;
  }

  return jobs.find((job) => job.jobId === window.jobId) ?? null;
}

function transitionWindow(
  window: PollingWindow,
  job: OcrJobSummary | null,
  nowMs: number,
) {
  if (window.phase === "discovery" && job) {
    return windowForJob(job, nowMs);
  }

  if (window.phase === "active-job" && job && !isActiveOcrJob(job)) {
    return windowForJob(job, nowMs);
  }

  if (
    window.phase === "awaiting-extraction" &&
    job &&
    isTerminalExtraction(job)
  ) {
    return null;
  }

  return window;
}

function windowForJob(job: OcrJobSummary, nowMs: number): PollingWindow | null {
  if (isActiveOcrJob(job)) {
    return {
      phase: "active-job",
      startedAtMs: nowMs,
      attempts: 0,
      jobId: job.jobId,
    };
  }

  if (job.status === "completed" && !isTerminalExtraction(job)) {
    return {
      phase: "awaiting-extraction",
      startedAtMs: nowMs,
      attempts: 0,
      jobId: job.jobId,
    };
  }

  return null;
}

function isTerminalExtraction(job: OcrJobSummary) {
  return (
    job.extractionStatus === "completed" ||
    job.extractionStatus === "review_required"
  );
}

function expiryReason(phase: PollingPhase): PollingExpiryReason {
  return `${phase}-expired`;
}
