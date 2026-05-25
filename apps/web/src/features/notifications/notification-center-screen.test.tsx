import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AppProviders } from "@/components/providers/app-providers";
import { NotificationCenterScreen } from "./notification-center-screen";

describe("NotificationCenterScreen", () => {
  function renderNotifications(
    state: "normal" | "loading" | "empty" | "error" | "permission-denied",
  ) {
    render(
      <AppProviders>
        <NotificationCenterScreen stateOverride={state} />
      </AppProviders>,
    );
  }

  it("renders persisted workflow notifications", () => {
    renderNotifications("normal");

    expect(screen.getAllByText("Notifications").length).toBeGreaterThan(0);
    expect(
      screen.getAllByText("Fake/test invoice ready for review").length,
    ).toBeGreaterThan(0);
    expect(
      screen.getAllByText(/corr_demo_invoice_mock/).length,
    ).toBeGreaterThan(0);
  });

  it("renders non-normal states", () => {
    renderNotifications("loading");
    expect(
      screen.getAllByLabelText("Loading notifications").length,
    ).toBeGreaterThan(0);
  });
});
