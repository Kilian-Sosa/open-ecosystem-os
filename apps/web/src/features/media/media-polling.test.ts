import { describe, expect, it } from "vitest";

import type { OcrJobSummary } from "@/lib/media-api";
import { nextPollingState, type PollingWindow } from "./media-polling";

function job(overrides: Partial<OcrJobSummary> = {}): OcrJobSummary {
  return {
    jobId: "job-1",
    fileId: "file-1",
    fileName: "invoice.pdf",
    contentType: "application/pdf",
    status: "queued",
    provider: null,
    attemptCount: 0,
    maxAttempts: 3,
    extractedTextLength: null,
    failureCode: null,
    failureMessage: null,
    correlationId: "corr-1",
    queuedAt: "2026-07-13T12:00:00Z",
    processingStartedAt: null,
    completedAt: null,
    failedAt: null,
    updatedAt: "2026-07-13T12:00:00Z",
    ocrResultPresent: false,
    extractionPresent: false,
    extractionStatus: null,
    reviewRequired: false,
    ...overrides,
  };
}

describe("nextPollingState", () => {
  it("continues discovery through empty list responses until the uploaded source job appears", () => {
    const discovery: PollingWindow = {
      phase: "discovery",
      startedAtMs: 0,
      attempts: 1,
      sourceFileId: "file-uploaded",
    };

    expect(nextPollingState(discovery, [], 2_000)).toMatchObject({
      poll: true,
      intervalMs: 2_000,
      reason: null,
      window: { phase: "discovery", attempts: 2 },
    });

    expect(
      nextPollingState(
        { ...discovery, attempts: 2 },
        [job({ fileId: "file-uploaded" })],
        4_000,
      ),
    ).toMatchObject({
      poll: true,
      intervalMs: 5_000,
      matchedJob: { jobId: "job-1" },
      window: { phase: "active-job", jobId: "job-1", attempts: 0 },
    });
  });

  it("expires upload-to-job discovery at thirty seconds without clearing its context", () => {
    const decision = nextPollingState(
      {
        phase: "discovery",
        startedAtMs: 0,
        attempts: 15,
        sourceFileId: "file-uploaded",
      },
      [],
      30_000,
    );

    expect(decision).toMatchObject({
      poll: false,
      reason: "discovery-expired",
      window: { sourceFileId: "file-uploaded" },
    });
  });

  it("expires a queued or processing job observation session at fifteen minutes", () => {
    const active: PollingWindow = {
      phase: "active-job",
      startedAtMs: 0,
      attempts: 179,
      jobId: "job-1",
    };

    expect(nextPollingState(active, [job()], 899_999)).toMatchObject({
      poll: true,
      intervalMs: 5_000,
      reason: null,
    });
    expect(nextPollingState(active, [job()], 900_000)).toMatchObject({
      poll: false,
      reason: "active-job-expired",
    });
  });

  it("bounds completed OCR waiting for extraction and stops immediately for terminal extraction", () => {
    const awaitingExtraction: PollingWindow = {
      phase: "awaiting-extraction",
      startedAtMs: 0,
      attempts: 14,
      jobId: "job-1",
    };
    const pendingExtraction = job({
      status: "completed",
      ocrResultPresent: true,
      completedAt: "2026-07-13T12:00:00Z",
    });

    expect(
      nextPollingState(awaitingExtraction, [pendingExtraction], 29_999),
    ).toMatchObject({ poll: true, intervalMs: 2_000, reason: null });
    expect(
      nextPollingState(awaitingExtraction, [pendingExtraction], 30_000),
    ).toMatchObject({
      poll: false,
      reason: "awaiting-extraction-expired",
    });
    expect(
      nextPollingState(
        awaitingExtraction,
        [job({ ...pendingExtraction, extractionStatus: "review_required" })],
        1,
      ),
    ).toMatchObject({ poll: false, reason: null });
  });
});
