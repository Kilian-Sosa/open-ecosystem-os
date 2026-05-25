import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { AppProviders } from "@/components/providers/app-providers";
import { SearchScreen } from "./search-screen";

describe("SearchScreen", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("renders indexed demo invoice results", () => {
    render(
      <AppProviders>
        <SearchScreen stateOverride="normal" />
      </AppProviders>,
    );

    expect(screen.getAllByText("Search").length).toBeGreaterThan(0);
    expect(
      screen.getAllByText("Fake/test invoice TEST-INV-2026-0001").length,
    ).toBeGreaterThan(0);
    expect(
      screen.getAllByText(/corr_demo_invoice_mock/).length,
    ).toBeGreaterThan(0);
  });

  it("submits a query to the search API", async () => {
    const fetchMock = vi.fn(async () =>
      jsonResponse({
        query: "TEST-INV-2026-0001",
        backend: "meilisearch",
        results: [
          {
            id: "srch_demo_test",
            sourceType: "demo_invoice_extraction",
            sourceId: "dinv_demo_test",
            title: "Fake/test invoice TEST-INV-2026-0001",
            summary: "Seeded invoice result.",
            resourceHref: "/app/demo/invoice-automation",
            correlationId: "corr_demo_test",
            status: "indexed",
            metadata: { isTestData: true },
            createdAt: "2026-05-25T09:00:13Z",
          },
        ],
      }),
    );
    vi.stubGlobal("fetch", fetchMock);

    render(
      <AppProviders>
        <SearchScreen />
      </AppProviders>,
    );

    fireEvent.change(screen.getAllByLabelText("Search indexed documents")[0], {
      target: { value: "TEST-INV-2026-0001" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: /^Search$/i })[0]);

    await screen.findAllByText("Fake/test invoice TEST-INV-2026-0001");
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining("/api/search?q=TEST-INV-2026-0001"),
        expect.any(Object),
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
