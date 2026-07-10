import { fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { AppProviders } from "@/components/providers/app-providers";
import type { MediaState } from "@/lib/media-mock-data";
import { MediaScreen, shouldPollOcrJobDetail } from "./media-screen";

describe("MediaScreen", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  function renderMedia(state: MediaState) {
    render(
      <AppProviders>
        <MediaScreen stateOverride={state} />
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

  it("polls active jobs and incomplete lifecycle projections", () => {
    expect(shouldPollOcrJobDetail(true, "complete")).toBe(true);
    expect(shouldPollOcrJobDetail(false, "active")).toBe(true);
    expect(shouldPollOcrJobDetail(false, "partial")).toBe(true);
    expect(shouldPollOcrJobDetail(false, "complete")).toBe(false);
    expect(shouldPollOcrJobDetail(false, null)).toBe(false);
  });

  it("renders the normal Media/OCR page with jobs and selected details", () => {
    renderMedia("normal");

    expect(screen.getAllByText("Media and OCR").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Invoice_2026_05.pdf").length).toBeGreaterThan(
      0,
    );
    expect(screen.getByLabelText("OCR job detail")).toBeInTheDocument();
    expect(
      screen.getByText(/Invoice number: TEST-INV-2026-0001/i),
    ).toBeInTheDocument();
  });

  it("renders the completed OCR, extraction workflow, and downstream lifecycle", () => {
    renderMedia("normal");

    const inspector = screen.getByLabelText("OCR job detail");
    expect(within(inspector).getByText("Lifecycle")).toBeInTheDocument();
    expect(within(inspector).getByText("File uploaded")).toBeInTheDocument();
    expect(within(inspector).getByText("OCR queued")).toBeInTheDocument();
    expect(within(inspector).getByText("OCR completed")).toBeInTheDocument();
    expect(
      within(inspector).getByText("Extract invoice fields"),
    ).toBeInTheDocument();
    expect(
      within(inspector).getByText("Notification created"),
    ).toBeInTheDocument();
    expect(
      within(inspector).getByText("Search indexing completed"),
    ).toBeInTheDocument();
    expect(
      within(inspector).getByRole("link", {
        name: "View correlated audit log",
      }),
    ).toHaveAttribute("href", "/admin/audit?correlationId=corr_invoice_demo");
  });

  it("shows an explicit awaiting state for an OCR job in progress", () => {
    renderMedia("normal");

    fireEvent.click(
      screen.getAllByRole("button", { name: /Receipt_scan\.png/i })[0],
    );

    const inspector = screen.getByLabelText("OCR job detail");
    expect(within(inspector).getByText("Lifecycle")).toBeInTheDocument();
    expect(
      within(inspector).getByText("Awaiting OCR outcome"),
    ).toBeInTheDocument();
    expect(within(inspector).getByText("Awaiting")).toBeInTheDocument();
  });

  it("shows retry context and the next scheduled attempt", () => {
    renderMedia("normal");

    fireEvent.click(
      screen.getAllByRole("button", { name: /Signed_contract\.pdf/i })[0],
    );

    const inspector = screen.getByLabelText("OCR job detail");
    expect(
      within(inspector).getByText("Awaiting scheduled OCR retry"),
    ).toBeInTheDocument();
    expect(within(inspector).getByText("Attempt 1 of 3")).toBeInTheDocument();
    expect(
      within(inspector).getByText(/Next attempt May 22/i),
    ).toBeInTheDocument();
  });

  it("renders a failed terminal lifecycle without exposing event payloads", () => {
    renderMedia("normal");

    fireEvent.click(
      screen.getAllByRole("button", { name: /Damaged_scan\.jpeg/i })[0],
    );

    const inspector = screen.getByLabelText("OCR job detail");
    expect(within(inspector).getByText("OCR failed")).toBeInTheDocument();
    expect(within(inspector).getAllByText("Failed").length).toBeGreaterThan(0);
    expect(inspector).not.toHaveTextContent("payload_json");
    expect(inspector).not.toHaveTextContent("envelope_json");
  });

  it("renders the loading state", () => {
    renderMedia("loading");
    expect(screen.getAllByLabelText("Loading OCR jobs").length).toBeGreaterThan(
      0,
    );
  });

  it("renders the empty state", () => {
    renderMedia("empty");
    expect(screen.getAllByText("No OCR jobs yet").length).toBeGreaterThan(0);
  });

  it("renders the error state", () => {
    renderMedia("error");
    expect(
      screen.getAllByText("OCR jobs could not load").length,
    ).toBeGreaterThan(0);
  });

  it("renders the permission denied state", () => {
    renderMedia("permission-denied");
    expect(
      screen.getAllByText("Media/OCR access is not available").length,
    ).toBeGreaterThan(0);
  });

  it("updates the selected job details when a job is selected", () => {
    renderMedia("normal");

    fireEvent.click(
      screen.getAllByRole("button", { name: /Damaged_scan\.jpeg/i })[0],
    );

    const inspector = screen.getByLabelText("OCR job detail");
    expect(
      within(inspector).getByText("Damaged_scan.jpeg"),
    ).toBeInTheDocument();
    expect(within(inspector).getByText("MOCK_OCR_FAILED")).toBeInTheDocument();
  });

  it("warns instead of uploading unsupported OCR file types", () => {
    const fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    renderMedia("normal");

    const input =
      document.querySelector<HTMLInputElement>("input[type='file']");
    expect(input).not.toBeNull();
    expect(input).toHaveAttribute(
      "accept",
      "application/pdf,image/png,image/jpeg",
    );

    fireEvent.change(input!, {
      target: {
        files: files(new File(["hello"], "notes.txt", { type: "text/plain" })),
      },
    });

    expect(
      screen.getAllByText(
        "notes.txt was not uploaded. OCR accepts PDF, PNG, and JPEG files only.",
      ).length,
    ).toBeGreaterThan(0);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("shows that the OCR job is pending after a valid upload succeeds", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          fileId: "file_new_invoice",
          name: "invoice.pdf",
          contentType: "application/pdf",
          sizeBytes: 512,
          checksumSha256: "checksum",
          encrypted: true,
          uploadedAt: "2026-05-23T07:00:00Z",
          updatedAt: "2026-05-23T07:00:00Z",
        }),
      }),
    );
    renderMedia("normal");

    const input =
      document.querySelector<HTMLInputElement>("input[type='file']");
    expect(input).not.toBeNull();
    fireEvent.change(input!, {
      target: {
        files: files(
          new File(["pdf"], "invoice.pdf", { type: "application/pdf" }),
        ),
      },
    });

    expect(
      (
        await screen.findAllByText(
          "Upload complete. Waiting for the OCR job for invoice.pdf...",
        )
      ).length,
    ).toBeGreaterThan(0);
  });
});
