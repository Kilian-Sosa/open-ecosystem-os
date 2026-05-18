import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { DashboardScreen } from "./dashboard-screen";
import { dashboardMockData } from "@/lib/dashboard-mock-data";

describe("DashboardScreen", () => {
  it("renders the normal dashboard with metrics and operational sections", () => {
    render(<DashboardScreen data={dashboardMockData} state="normal" />);

    expect(screen.getAllByText("Workspace Dashboard").length).toBeGreaterThan(
      0,
    );
    expect(screen.getAllByText("12,458").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Recent files").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Active automations").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Recent activity").length).toBeGreaterThan(0);
  });

  it("renders the loading dashboard state", () => {
    render(<DashboardScreen data={dashboardMockData} state="loading" />);

    expect(
      screen.getAllByLabelText("Loading workspace dashboard").length,
    ).toBeGreaterThan(0);
  });

  it("renders the empty dashboard state", () => {
    render(<DashboardScreen data={dashboardMockData} state="empty" />);

    expect(
      screen.getAllByText("No workspace activity yet").length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText("Upload first invoice").length).toBeGreaterThan(
      0,
    );
  });

  it("renders the error dashboard state", () => {
    render(<DashboardScreen data={dashboardMockData} state="error" />);

    expect(
      screen.getAllByText("Dashboard data could not load").length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText("Retry dashboard").length).toBeGreaterThan(0);
  });
});
