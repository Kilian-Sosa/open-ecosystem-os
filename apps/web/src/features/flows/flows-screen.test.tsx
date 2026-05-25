import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { AppProviders } from "@/components/providers/app-providers";
import {
  flowsMockExecutionDetails,
  flowsMockExecutions,
  flowsMockWorkflowDetails,
  flowsMockWorkflows,
  type FlowsState,
} from "@/lib/flows-mock-data";
import { FlowsScreen } from "./flows-screen";

describe("FlowsScreen", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  function renderFlows(state: FlowsState) {
    return render(
      <AppProviders>
        <FlowsScreen stateOverride={state} />
      </AppProviders>,
    );
  }

  it("renders the normal Flows page with builder and execution details", () => {
    renderFlows("normal");

    expect(screen.getAllByText("Open Ecosystem Flows").length).toBeGreaterThan(
      0,
    );
    expect(
      screen.getAllByText("Invoice Processing Automation").length,
    ).toBeGreaterThan(0);
    expect(
      screen.getAllByText("Create review notification").length,
    ).toBeGreaterThan(0);
    expect(screen.getByLabelText("Execution detail")).toBeInTheDocument();
  });

  it("renders loading, empty, error, and permission denied states", () => {
    const { unmount } = render(
      <AppProviders>
        <FlowsScreen stateOverride="loading" />
      </AppProviders>,
    );
    expect(
      screen.getAllByLabelText("Loading workflows").length,
    ).toBeGreaterThan(0);
    unmount();

    const empty = renderFlows("empty");
    expect(screen.getAllByText("No workflows yet").length).toBeGreaterThan(0);
    empty.unmount();

    const error = renderFlows("error");
    expect(screen.getAllByText("Flows could not load").length).toBeGreaterThan(
      0,
    );
    error.unmount();

    renderFlows("permission-denied");
    expect(
      screen.getAllByText("Flows access is not available").length,
    ).toBeGreaterThan(0);
  });

  it("updates the selected execution detail when a run is selected", () => {
    renderFlows("normal");

    fireEvent.click(screen.getAllByRole("button", { name: /failed/i })[0]);

    const inspector = screen.getByLabelText("Execution detail");
    expect(
      within(inspector).getAllByText("Notification title is required").length,
    ).toBeGreaterThan(0);
  });

  it("runs the selected workflow through the API", async () => {
    const runExecution = {
      ...flowsMockExecutionDetails[0],
      executionId: "wfe_manual_run",
      triggerType: "manual",
    };
    const fetchMock = vi.fn(
      async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = input.toString();
        if (url.endsWith("/api/flows/workflows")) {
          return jsonResponse({ workflows: [flowsMockWorkflows[0]] });
        }
        if (url.endsWith("/api/flows/executions")) {
          return jsonResponse({ executions: flowsMockExecutions });
        }
        if (url.endsWith("/api/flows/workflows/flow_invoice_automation")) {
          return jsonResponse(flowsMockWorkflowDetails[0]);
        }
        if (url.endsWith("/api/flows/executions/wfe_success")) {
          return jsonResponse(flowsMockExecutionDetails[0]);
        }
        if (
          url.endsWith("/api/flows/workflows/flow_invoice_automation/runs") &&
          init?.method === "POST"
        ) {
          return jsonResponse(runExecution);
        }
        if (url.endsWith("/api/flows/executions/wfe_manual_run")) {
          return jsonResponse(runExecution);
        }
        return { ok: false, json: async () => ({}) } as Response;
      },
    );
    vi.stubGlobal("fetch", fetchMock);

    render(
      <AppProviders>
        <FlowsScreen />
      </AppProviders>,
    );

    await screen.findAllByText("Invoice Processing Automation");
    await waitFor(() => {
      expect(
        screen
          .getAllByRole("button", { name: /Run workflow/i })
          .some((button) => !button.hasAttribute("disabled")),
      ).toBe(true);
    });
    const runButton = screen
      .getAllByRole("button", { name: /Run workflow/i })
      .find((button) => !button.hasAttribute("disabled"));
    expect(runButton).toBeDefined();
    fireEvent.click(runButton!);

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining(
          "/api/flows/workflows/flow_invoice_automation/runs",
        ),
        expect.objectContaining({ method: "POST" }),
      );
    });
  });
});

function jsonResponse(body: unknown) {
  return {
    ok: true,
    json: async () => body,
  } as Response;
}
