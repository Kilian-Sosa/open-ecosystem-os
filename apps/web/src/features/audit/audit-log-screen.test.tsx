import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AppProviders } from "@/components/providers/app-providers";
import { AuditLogScreen } from "./audit-log-screen";

describe("AuditLogScreen", () => {
  function renderAudit(
    state: "normal" | "loading" | "empty" | "error" | "permission-denied",
  ) {
    render(
      <AppProviders>
        <AuditLogScreen stateOverride={state} />
      </AppProviders>,
    );
  }

  it("renders audit records for the demo trace", () => {
    renderAudit("normal");

    expect(screen.getAllByText("Audit logs").length).toBeGreaterThan(0);
    expect(
      screen.getAllByText("flows.demo_invoice.extracted").length,
    ).toBeGreaterThan(0);
    expect(
      screen.getAllByText("demo_invoice_extraction").length,
    ).toBeGreaterThan(0);
  });

  it("renders non-normal states", () => {
    renderAudit("loading");
    expect(
      screen.getAllByLabelText("Loading audit records").length,
    ).toBeGreaterThan(0);
  });
});
