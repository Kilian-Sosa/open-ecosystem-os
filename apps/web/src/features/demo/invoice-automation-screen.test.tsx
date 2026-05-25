import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { AppProviders } from "@/components/providers/app-providers";
import { InvoiceAutomationScreen } from "./invoice-automation-screen";
import type { DemoInvoiceRun } from "@/lib/demo-invoice-api";

describe("InvoiceAutomationScreen", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  function renderDemo(state?: "normal" | "loading" | "empty" | "error") {
    render(
      <AppProviders>
        <InvoiceAutomationScreen stateOverride={state} />
      </AppProviders>,
    );
  }

  it("renders the seeded demo timeline and fake/test invoice fields", () => {
    renderDemo("normal");

    expect(
      screen.getAllByText("Invoice automation demo").length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText("Drive upload").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Search indexing").length).toBeGreaterThan(0);
    expect(screen.getAllByText("TEST-INV-2026-0001").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Test NIF").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Test IBAN").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Fake/test data").length).toBeGreaterThan(0);
  });

  it("renders loading, empty, error, and permission denied states", () => {
    const loading = render(
      <AppProviders>
        <InvoiceAutomationScreen stateOverride="loading" />
      </AppProviders>,
    );
    expect(
      screen.getAllByLabelText("Loading invoice automation demo").length,
    ).toBeGreaterThan(0);
    loading.unmount();

    renderDemo("empty");
    expect(
      screen.getAllByText("No invoice demo run yet").length,
    ).toBeGreaterThan(0);
  });

  it("starts a demo run through the API", async () => {
    const run = demoRun();
    const fetchMock = vi.fn(
      async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = input.toString();
        if (
          url.endsWith("/api/demo/invoice-automation/runs") &&
          init?.method === "POST"
        ) {
          return jsonResponse(run);
        }
        if (url.endsWith(`/api/demo/invoice-automation/runs/${run.runId}`)) {
          return jsonResponse(run);
        }
        return { ok: false, json: async () => ({}) } as Response;
      },
    );
    vi.stubGlobal("fetch", fetchMock);

    render(
      <AppProviders>
        <InvoiceAutomationScreen />
      </AppProviders>,
    );

    fireEvent.click(screen.getAllByRole("button", { name: /Run demo/i })[0]);

    await screen.findAllByText("TEST-INV-2026-0001");
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining("/api/demo/invoice-automation/runs"),
        expect.objectContaining({ method: "POST" }),
      );
    });
  });
});

function demoRun(): DemoInvoiceRun {
  return {
    runId: "demo_test_run",
    correlationId: "corr_demo_test",
    fileId: "file_demo_test",
    ocrJobId: "ocr_demo_test",
    workflowExecutionId: "wfe_demo_test",
    notificationId: "ntf_demo_test",
    searchDocumentId: "srch_demo_test",
    status: "completed",
    links: {
      drive: "/app/drive?fileId=file_demo_test",
      ocr: "/app/media?jobId=ocr_demo_test",
      flows: "/app/flows?executionId=wfe_demo_test",
      notifications: "/app/notifications?correlationId=corr_demo_test",
      audit: "/admin/audit?correlationId=corr_demo_test",
      search: "/app/search?q=TEST-INV-2026-0001",
    },
    timeline: [
      {
        key: "drive",
        label: "Drive upload",
        status: "completed",
        detail: "Fake/test invoice placeholder stored in Drive.",
        href: "/app/drive?fileId=file_demo_test",
        occurredAt: "2026-05-25T09:00:00Z",
      },
    ],
    extraction: {
      extractionId: "dinv_demo_test",
      invoiceNumber: "TEST-INV-2026-0001",
      supplierName: "Demo Supplies S.L. (fake/test data)",
      supplierTestNif: "B00000000 (test data)",
      supplierTestIban: "ES00 0000 0000 0000 0000 0000 (test data)",
      totalAmount: 124,
      currency: "EUR",
      dueDate: "2026-06-15",
      isTestData: true,
    },
    createdAt: "2026-05-25T09:00:00Z",
    updatedAt: "2026-05-25T09:00:13Z",
  };
}

function jsonResponse(body: unknown) {
  return {
    ok: true,
    json: async () => body,
  } as Response;
}
