import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AppProviders } from "@/components/providers/app-providers";
import type { MediaState } from "@/lib/media-mock-data";
import { MediaScreen } from "./media-screen";

describe("MediaScreen", () => {
  function renderMedia(state: MediaState) {
    render(
      <AppProviders>
        <MediaScreen stateOverride={state} />
      </AppProviders>,
    );
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
});
