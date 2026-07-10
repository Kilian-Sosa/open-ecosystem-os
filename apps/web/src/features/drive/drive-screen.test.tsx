import { fireEvent, render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { AppProviders } from "@/components/providers/app-providers";
import { DriveScreen } from "./drive-screen";
import type { DriveState } from "@/lib/drive-mock-data";

describe("DriveScreen", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  function renderDrive(state: DriveState) {
    render(
      <AppProviders>
        <DriveScreen stateOverride={state} />
      </AppProviders>,
    );
  }

  function fileList(file: File) {
    return {
      0: file,
      length: 1,
      item: (index: number) => (index === 0 ? file : null),
    } as unknown as FileList;
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

  it("filters files case-insensitively by filename and recognizable file type", () => {
    renderDrive("normal");

    const [search] = screen.getAllByRole("searchbox", {
      name: "Search Drive files",
    });

    fireEvent.change(search, { target: { value: "qUaRtErLy" } });

    expect(screen.getAllByText("Quarterly budget.xlsx").length).toBeGreaterThan(
      0,
    );
    expect(screen.queryAllByText("Invoice_2026_05.pdf")).toHaveLength(0);
    expect(screen.getByLabelText("File details")).toHaveTextContent(
      "Quarterly budget.xlsx",
    );

    fireEvent.change(search, { target: { value: "pDf" } });

    expect(screen.getAllByText("Invoice_2026_05.pdf").length).toBeGreaterThan(
      0,
    );
    expect(screen.queryAllByText("Quarterly budget.xlsx")).toHaveLength(0);
  });

  it("shows an inline no-match state and clears the search", () => {
    renderDrive("normal");

    const [search] = screen.getAllByRole("searchbox", {
      name: "Search Drive files",
    });

    fireEvent.change(search, { target: { value: "does-not-exist" } });

    expect(screen.getAllByText("No matching files").length).toBeGreaterThan(0);
    expect(screen.queryByText("No files uploaded yet")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("File details")).not.toBeInTheDocument();

    fireEvent.click(screen.getAllByRole("button", { name: "Clear search" })[0]);

    expect(search).toHaveValue("");
    expect(search).toHaveFocus();
    expect(screen.getAllByText("Invoice_2026_05.pdf").length).toBeGreaterThan(
      0,
    );
  });

  it("shows feedback without uploading when browser validation rejects a file", () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    renderDrive("normal");

    fireEvent.change(screen.getAllByLabelText("Upload file")[0], {
      target: {
        files: fileList(
          new File(["unsupported"], "unsafe.exe", {
            type: "application/x-msdownload",
          }),
        ),
      },
    });

    expect(screen.getAllByRole("alert")[0]).toHaveTextContent(
      "This file type is not supported",
    );
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});
