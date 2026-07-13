import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  within,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { AppProviders } from "@/components/providers/app-providers";
import {
  shouldPollOcrJobDetail,
  type OcrJobDetail,
  type OcrJobSummary,
} from "@/lib/media-api";
import { MediaScreen } from "./media-screen";

const now = new Date("2026-07-11T12:00:00Z");

function summary(overrides: Partial<OcrJobSummary> = {}): OcrJobSummary {
  return {
    jobId: "job-1",
    fileId: "file-1",
    fileName: "document-example.pdf",
    contentType: "application/pdf",
    status: "completed",
    provider: "Tesseract",
    attemptCount: 1,
    maxAttempts: 3,
    extractedTextLength: 48,
    failureCode: null,
    failureMessage: null,
    correlationId: "corr-1",
    queuedAt: "2026-07-11T11:59:00Z",
    processingStartedAt: "2026-07-11T11:59:10Z",
    completedAt: "2026-07-11T11:59:30Z",
    failedAt: null,
    updatedAt: "2026-07-11T11:59:30Z",
    ocrResultPresent: true,
    extractionPresent: true,
    extractionStatus: "completed",
    reviewRequired: false,
    ...overrides,
  };
}

function detail(overrides: Partial<OcrJobDetail> = {}): OcrJobDetail {
  return {
    ...summary(),
    extractedText: "Recognized document text.",
    nextAttemptAt: null,
    lifecycle: { state: "complete", outcome: "completed", entries: [] },
    ocrResult: {
      ocrResultId: "result-1",
      provider: "Tesseract",
      providerVersion: "5.5.1",
      pageCount: 2,
      wordCount: 128,
    },
    extraction: {
      extractionId: "extraction-1",
      extractor: "heuristic-invoice",
      extractorVersion: "1.0.0",
      status: "completed",
      reviewRequired: false,
      confidence: 96,
      fieldCount: 1,
      warningCount: 0,
      warnings: [],
      fields: [
        {
          fieldKey: "invoice_number",
          label: "Invoice number",
          displayValue: "INV-EXAMPLE-01",
          normalizedValue: "INV-EXAMPLE-01",
          status: "extracted",
          confidence: 98,
          provenance: [
            {
              sourceRole: "value",
              ocrWordId: "word-1",
              pageNumber: 1,
              blockNumber: 3,
              paragraphNumber: 1,
              lineNumber: 4,
              wordNumber: 2,
              readingOrder: 12,
              sourceKind: "tesseract_tsv",
            },
          ],
        },
      ],
    },
    ...overrides,
  };
}

function jsonResponse(body: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  };
}

function renderMedia() {
  render(
    <AppProviders>
      <MediaScreen />
    </AppProviders>,
  );
}

function files(file: File) {
  return {
    0: file,
    length: 1,
    item: (index: number) => (index === 0 ? file : null),
  } as unknown as FileList;
}

function stubJobsAndDetail(
  jobs = [summary()],
  selectedDetail: unknown = detail(),
  detailStatus = 200,
) {
  const fetchMock = vi.fn((input: RequestInfo | URL) => {
    const url = input.toString();
    if (url.endsWith("/api/media/ocr-jobs")) {
      return Promise.resolve(jsonResponse({ jobs }));
    }
    if (url.endsWith("/api/media/ocr-jobs/job-1")) {
      return Promise.resolve(jsonResponse(selectedDetail, detailStatus));
    }
    return Promise.resolve(jsonResponse({}, 404));
  });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

describe("MediaScreen", () => {
  afterEach(() => {
    cleanup();
    vi.useRealTimers();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("renders completed extraction details with the returned provider, confidence, and provenance", async () => {
    stubJobsAndDetail();
    renderMedia();

    const inspector = await screen.findByLabelText("OCR job detail");
    expect(
      await within(inspector).findByText("Tesseract 5.5.1"),
    ).toBeInTheDocument();
    expect(within(inspector).getAllByText("Completed").length).toBeGreaterThan(
      0,
    );
    expect(within(inspector).getByText("Invoice number")).toBeInTheDocument();
    expect(within(inspector).getByText("INV-EXAMPLE-01")).toBeInTheDocument();
    expect(within(inspector).getByText("98% confidence")).toBeInTheDocument();
    expect(
      within(inspector).getByText("Page 1, line 4, Tesseract TSV"),
    ).toBeInTheDocument();
    expect(inspector).not.toHaveTextContent("Mock provider");
    expect(inspector).not.toHaveTextContent("TEST-INV-2026-0001");
  });

  it("renders review-required extraction status and value-free warnings", async () => {
    stubJobsAndDetail(
      [summary({ extractionStatus: "review_required", reviewRequired: true })],
      detail({
        extraction: {
          ...detail().extraction!,
          status: "review_required",
          reviewRequired: true,
          confidence: 71,
          warningCount: 1,
          warnings: [
            {
              code: "LOW_CONFIDENCE",
              fieldKey: "invoice_number",
              message:
                "Invoice number needs review because the source confidence is low.",
            },
          ],
        },
      }),
    );
    renderMedia();

    const inspector = await screen.findByLabelText("OCR job detail");
    expect(
      await within(inspector).findByText("Review required"),
    ).toBeInTheDocument();
    expect(
      within(inspector).getByText(
        "Invoice number needs review because the source confidence is low.",
      ),
    ).toBeInTheDocument();
    expect(
      within(inspector).getByRole("list", { name: "Extraction warnings" }),
    ).toBeInTheDocument();
  });

  it("shows pending extraction after OCR completes and bounds detail polling", async () => {
    const completedWithoutExtraction = detail({
      extraction: null,
      extractionPresent: false,
      extractionStatus: null,
      completedAt: "2026-07-11T11:59:40Z",
    });
    stubJobsAndDetail(
      [summary(completedWithoutExtraction)],
      completedWithoutExtraction,
    );
    renderMedia();

    const inspector = await screen.findByLabelText("OCR job detail");
    expect(
      await within(inspector).findByText("Extraction is pending"),
    ).toBeInTheDocument();
    expect(shouldPollOcrJobDetail(completedWithoutExtraction, now)).toBe(true);
    expect(
      shouldPollOcrJobDetail(
        completedWithoutExtraction,
        new Date("2026-07-11T12:00:11Z"),
      ),
    ).toBe(false);
    expect(shouldPollOcrJobDetail(detail(), now)).toBe(false);
  });

  it("shows an explicit no-fields state without inventing structured values", async () => {
    stubJobsAndDetail(
      [summary()],
      detail({
        extraction: {
          ...detail().extraction!,
          fieldCount: 0,
          fields: [],
        },
      }),
    );
    renderMedia();

    const inspector = await screen.findByLabelText("OCR job detail");
    expect(
      await within(inspector).findByText("No structured fields were returned."),
    ).toBeInTheDocument();
    expect(inspector).not.toHaveTextContent("INV-EXAMPLE-01");
  });

  it("renders loading, empty, generic error, and permission denied states from API responses", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise(() => undefined)),
    );
    renderMedia();
    expect(
      (await screen.findAllByLabelText("Loading OCR jobs")).length,
    ).toBeGreaterThan(0);

    cleanup();
    vi.restoreAllMocks();
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve(jsonResponse({ jobs: [] }))),
    );
    renderMedia();
    expect(
      (await screen.findAllByText("No OCR jobs yet")).length,
    ).toBeGreaterThan(0);

    cleanup();
    vi.restoreAllMocks();
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve(jsonResponse({}, 500))),
    );
    renderMedia();
    expect(
      (await screen.findAllByText("OCR jobs could not load")).length,
    ).toBeGreaterThan(0);

    cleanup();
    vi.restoreAllMocks();
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve(jsonResponse({}, 403))),
    );
    renderMedia();
    expect(
      (await screen.findAllByText("Media/OCR access is not available")).length,
    ).toBeGreaterThan(0);
  });

  it("shows protected detail states when the selected job detail is unavailable", async () => {
    stubJobsAndDetail([summary()], {}, 403);
    renderMedia();
    expect(
      (
        await screen.findAllByText(
          "You do not have access to the selected OCR job.",
        )
      ).length,
    ).toBeGreaterThan(0);

    cleanup();
    vi.restoreAllMocks();
    stubJobsAndDetail([summary()], {}, 500);
    renderMedia();
    expect(
      (await screen.findAllByText("Selected OCR job details could not load."))
        .length,
    ).toBeGreaterThan(0);
  });

  it("opens the mobile bottom sheet with the same structured extraction details", async () => {
    Object.defineProperty(window, "matchMedia", {
      configurable: true,
      writable: true,
      value: vi.fn(() => ({
        matches: true,
        media: "(max-width: 1279px)",
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    });
    stubJobsAndDetail();
    renderMedia();

    const jobButton = (await screen.findAllByText("document-example.pdf"))
      .map((element) => element.closest("button"))
      .find((element): element is HTMLButtonElement => element !== null);
    expect(jobButton).toBeDefined();
    fireEvent.click(jobButton!);

    const [sheet] = await screen.findAllByRole("dialog", {
      name: "document-example.pdf",
    });
    expect(within(sheet).getByText("Invoice number")).toBeInTheDocument();
    expect(within(sheet).getByText("INV-EXAMPLE-01")).toBeInTheDocument();
  });

  it("rejects unsupported uploads and tracks a valid upload while awaiting its OCR job", async () => {
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = input.toString();
      if (url.endsWith("/api/media/ocr-jobs")) {
        return Promise.resolve(jsonResponse({ jobs: [summary()] }));
      }
      if (url.endsWith("/api/media/ocr-jobs/job-1")) {
        return Promise.resolve(jsonResponse(detail()));
      }
      if (url.endsWith("/api/drive/files")) {
        return Promise.resolve(
          jsonResponse({
            fileId: "file-uploaded",
            name: "new-document.pdf",
            contentType: "application/pdf",
            sizeBytes: 512,
            checksumSha256: "checksum",
            encrypted: true,
            uploadedAt: "2026-07-11T12:00:00Z",
            updatedAt: "2026-07-11T12:00:00Z",
          }),
        );
      }
      return Promise.resolve(jsonResponse({}, 404));
    });
    vi.stubGlobal("fetch", fetchMock);
    renderMedia();

    const input =
      document.querySelector<HTMLInputElement>("input[type='file']");
    expect(input).not.toBeNull();
    fireEvent.change(input!, {
      target: {
        files: files(new File(["plain"], "notes.txt", { type: "text/plain" })),
      },
    });
    expect(
      (
        await screen.findAllByText(
          "notes.txt was not uploaded. OCR accepts PDF, PNG, and JPEG files only.",
        )
      ).length,
    ).toBeGreaterThan(0);

    fireEvent.change(input!, {
      target: {
        files: files(
          new File(["pdf"], "new-document.pdf", {
            type: "application/pdf",
          }),
        ),
      },
    });
    expect(
      (
        await screen.findAllByText(
          "Upload complete. Waiting for the OCR job for new-document.pdf...",
        )
      ).length,
    ).toBeGreaterThan(0);
    expect(
      fetchMock.mock.calls.some(([input]) =>
        input.toString().endsWith("/api/drive/files"),
      ),
    ).toBe(true);
  });

  it("continues polling empty job lists after upload until the matching source job appears", async () => {
    vi.useFakeTimers();
    let listRequests = 0;
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = input.toString();
      if (url.endsWith("/api/media/ocr-jobs")) {
        listRequests += 1;
        return Promise.resolve(
          jsonResponse({
            jobs:
              listRequests < 3
                ? []
                : [
                    summary({
                      fileId: "file-uploaded",
                      fileName: "new-document.pdf",
                      status: "queued",
                    }),
                  ],
          }),
        );
      }
      if (url.endsWith("/api/media/ocr-jobs/job-1")) {
        return Promise.resolve(jsonResponse(detail()));
      }
      if (url.endsWith("/api/drive/files")) {
        return Promise.resolve(
          jsonResponse({
            fileId: "file-uploaded",
            name: "new-document.pdf",
            contentType: "application/pdf",
            sizeBytes: 512,
            checksumSha256: "checksum",
            encrypted: true,
            uploadedAt: "2026-07-11T12:00:00Z",
            updatedAt: "2026-07-11T12:00:00Z",
          }),
        );
      }
      return Promise.resolve(jsonResponse({}, 404));
    });
    vi.stubGlobal("fetch", fetchMock);
    renderMedia();

    const input =
      document.querySelector<HTMLInputElement>("input[type='file']");
    expect(input).not.toBeNull();
    fireEvent.change(input!, {
      target: {
        files: files(
          new File(["pdf"], "new-document.pdf", {
            type: "application/pdf",
          }),
        ),
      },
    });

    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(
      screen.getAllByText(
        "Upload complete. Waiting for the OCR job for new-document.pdf...",
      ).length,
    ).toBeGreaterThan(0);
    expect(screen.getByRole("status")).toHaveTextContent(
      "Upload complete. Waiting for the OCR job for new-document.pdf...",
    );
    expect(screen.getByRole("status")).toHaveAttribute("aria-atomic", "true");

    await act(async () => {
      await vi.advanceTimersByTimeAsync(2_000);
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(listRequests).toBeGreaterThanOrEqual(3);
    expect(
      screen
        .getAllByRole("status")
        .filter((region) => region.textContent?.includes("Upload complete")),
    ).toHaveLength(1);
  });

  it("stops upload discovery at thirty seconds and offers an accessible manual refresh", async () => {
    vi.useFakeTimers();
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = input.toString();
      if (url.endsWith("/api/media/ocr-jobs")) {
        return Promise.resolve(jsonResponse({ jobs: [] }));
      }
      if (url.endsWith("/api/drive/files")) {
        return Promise.resolve(
          jsonResponse({
            fileId: "file-uploaded",
            name: "new-document.pdf",
            contentType: "application/pdf",
            sizeBytes: 512,
            checksumSha256: "checksum",
            encrypted: true,
            uploadedAt: "2026-07-11T12:00:00Z",
            updatedAt: "2026-07-11T12:00:00Z",
          }),
        );
      }
      return Promise.resolve(jsonResponse({}, 404));
    });
    vi.stubGlobal("fetch", fetchMock);
    renderMedia();

    const input =
      document.querySelector<HTMLInputElement>("input[type='file']");
    fireEvent.change(input!, {
      target: {
        files: files(
          new File(["pdf"], "new-document.pdf", {
            type: "application/pdf",
          }),
        ),
      },
    });
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(
      screen.getAllByText(
        "Upload complete. Waiting for the OCR job for new-document.pdf...",
      ).length,
    ).toBeGreaterThan(0);

    await act(async () => {
      await vi.advanceTimersByTimeAsync(30_000);
    });

    expect(screen.getByRole("alert")).toHaveTextContent(
      /OCR job has not appeared yet/i,
    );
    expect(
      screen.getAllByRole("button", { name: "Refresh OCR job status" }).length,
    ).toBeGreaterThan(0);
  });

  it("uses alerts for upload rejection and request failures without duplicating announcements", async () => {
    stubJobsAndDetail();
    renderMedia();

    const input =
      document.querySelector<HTMLInputElement>("input[type='file']");
    fireEvent.change(input!, {
      target: {
        files: files(new File(["plain"], "notes.txt", { type: "text/plain" })),
      },
    });

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "notes.txt was not uploaded. OCR accepts PDF, PNG, and JPEG files only.",
    );
    expect(screen.getAllByRole("alert")).toHaveLength(1);
  });
});
