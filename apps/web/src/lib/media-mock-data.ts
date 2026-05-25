import type { OcrJobDetail } from "@/lib/media-api";

export type MediaState =
  | "normal"
  | "loading"
  | "empty"
  | "error"
  | "permission-denied";

export const mediaMockJobs: OcrJobDetail[] = [
  {
    jobId: "ocr_invoice_demo",
    fileId: "file_invoice_demo",
    fileName: "Invoice_2026_05.pdf",
    contentType: "application/pdf",
    status: "completed",
    provider: "mock",
    attemptCount: 1,
    maxAttempts: 3,
    extractedText:
      "Mock OCR result\nInvoice number: TEST-INV-2026-0001\nSupplier: Demo Supplies S.L. (fake/test data)\nTest NIF: B00000000 (test data)\nTest IBAN: ES00 0000 0000 0000 0000 0000 (test data)\nTotal: 124.00 EUR\nDue date: 2026-06-15",
    extractedTextLength: 232,
    failureCode: null,
    failureMessage: null,
    correlationId: "corr_invoice_demo",
    queuedAt: "2026-05-22T09:15:10Z",
    processingStartedAt: "2026-05-22T09:15:22Z",
    completedAt: "2026-05-22T09:15:34Z",
    failedAt: null,
    updatedAt: "2026-05-22T09:15:34Z",
  },
  {
    jobId: "ocr_receipt_processing",
    fileId: "file_receipt_demo",
    fileName: "Receipt_scan.png",
    contentType: "image/png",
    status: "processing",
    provider: "mock",
    attemptCount: 1,
    maxAttempts: 3,
    extractedText: null,
    extractedTextLength: null,
    failureCode: null,
    failureMessage: null,
    correlationId: "corr_receipt_demo",
    queuedAt: "2026-05-22T10:03:12Z",
    processingStartedAt: "2026-05-22T10:03:18Z",
    completedAt: null,
    failedAt: null,
    updatedAt: "2026-05-22T10:03:18Z",
  },
  {
    jobId: "ocr_contract_queued",
    fileId: "file_contract_demo",
    fileName: "Signed_contract.pdf",
    contentType: "application/pdf",
    status: "queued",
    provider: null,
    attemptCount: 0,
    maxAttempts: 3,
    extractedText: null,
    extractedTextLength: null,
    failureCode: null,
    failureMessage: null,
    correlationId: "corr_contract_demo",
    queuedAt: "2026-05-22T10:08:00Z",
    processingStartedAt: null,
    completedAt: null,
    failedAt: null,
    updatedAt: "2026-05-22T10:08:00Z",
  },
  {
    jobId: "ocr_scan_failed",
    fileId: "file_scan_failed",
    fileName: "Damaged_scan.jpeg",
    contentType: "image/jpeg",
    status: "failed",
    provider: "mock",
    attemptCount: 3,
    maxAttempts: 3,
    extractedText: null,
    extractedTextLength: null,
    failureCode: "MOCK_OCR_FAILED",
    failureMessage: "Mock OCR provider failed",
    correlationId: "corr_failed_demo",
    queuedAt: "2026-05-21T16:20:00Z",
    processingStartedAt: "2026-05-21T16:23:00Z",
    completedAt: null,
    failedAt: "2026-05-21T16:23:02Z",
    updatedAt: "2026-05-21T16:23:02Z",
  },
];

export function parseMediaState(
  value: string | string[] | undefined,
): MediaState | undefined {
  const state = Array.isArray(value) ? value[0] : value;
  return state === "normal" ||
    state === "loading" ||
    state === "empty" ||
    state === "error" ||
    state === "permission-denied"
    ? state
    : undefined;
}
