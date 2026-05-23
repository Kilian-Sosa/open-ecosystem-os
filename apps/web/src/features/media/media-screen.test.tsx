import { fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { AppProviders } from "@/components/providers/app-providers";
import type { MediaState } from "@/lib/media-mock-data";
import { MediaScreen } from "./media-screen";

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

  it("renders the normal Media/OCR page with jobs and selected details", () => {
    renderMedia("normal");

    expect(screen.getAllByText("Media and OCR").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Invoice_2026_05.pdf").length).toBeGreaterThan(
      0,
    );
    expect(screen.getByLabelText("OCR job detail")).toBeInTheDocument();
    expect(
      screen.getByText(/Invoice number: INV-2026-0517/i),
    ).toBeInTheDocument();
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
