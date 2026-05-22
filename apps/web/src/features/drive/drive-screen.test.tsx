import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AppProviders } from "@/components/providers/app-providers";
import { DriveScreen } from "./drive-screen";
import type { DriveState } from "@/lib/drive-mock-data";

describe("DriveScreen", () => {
  function renderDrive(state: DriveState) {
    render(
      <AppProviders>
        <DriveScreen stateOverride={state} />
      </AppProviders>,
    );
  }

  it("renders the normal Drive page with files and selected details", () => {
    renderDrive("normal");

    expect(screen.getAllByText("Drive").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Invoice_2026_05.pdf").length).toBeGreaterThan(
      0,
    );
    expect(screen.getByLabelText("File details")).toBeInTheDocument();
    expect(screen.getAllByText("Encrypted").length).toBeGreaterThan(0);
  });

  it("renders the loading state", () => {
    renderDrive("loading");
    expect(
      screen.getAllByLabelText("Loading Drive files").length,
    ).toBeGreaterThan(0);
  });

  it("renders the empty state", () => {
    renderDrive("empty");
    expect(screen.getAllByText("No files uploaded yet").length).toBeGreaterThan(
      0,
    );
  });

  it("renders the error state", () => {
    renderDrive("error");
    expect(
      screen.getAllByText("Drive files could not load").length,
    ).toBeGreaterThan(0);
  });

  it("renders the permission denied state", () => {
    renderDrive("permission-denied");
    expect(
      screen.getAllByText("Drive access is not available").length,
    ).toBeGreaterThan(0);
  });

  it("updates the selected file details when a file is selected", () => {
    renderDrive("normal");

    fireEvent.click(
      screen.getAllByRole("button", { name: /Quarterly budget\.xlsx/i })[0],
    );

    const inspector = screen.getByLabelText("File details");
    expect(
      within(inspector).getByText("Quarterly budget.xlsx"),
    ).toBeInTheDocument();
    expect(within(inspector).getAllByText("XLSX").length).toBeGreaterThan(0);
  });
});
