import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { AppProviders } from "@/components/providers/app-providers";
import { SearchScreen } from "./search-screen";

describe("SearchScreen", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("renders indexed document extraction results", () => {
    render(
      <AppProviders>
        <SearchScreen stateOverride="normal" />
      </AppProviders>,
    );

    expect(screen.getAllByText("Search").length).toBeGreaterThan(0);
    expect(
      screen.getAllByText("Indexed document extraction").length,
    ).toBeGreaterThan(0);
    expect(
      screen.getAllByText(/corr_document_extraction/).length,
    ).toBeGreaterThan(0);
  });

  it("submits a query to the search API", async () => {
    const fetchMock = vi.fn(async () =>
      jsonResponse({
        query: "project brief",
        backend: "meilisearch",
        results: [
          {
            id: "srch_document_test",
            sourceType: "document_extraction",
            sourceId: "extraction_test",
            title: "Indexed document extraction",
            summary: "A document extraction is ready for workspace review.",
            resourceHref: "/app/media?jobId=job-test",
            correlationId: "corr_document_test",
            status: "indexed",
            metadata: { status: "completed" },
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
      target: { value: "project brief" },
    });
    fireEvent.click(screen.getAllByRole("button", { name: /^Search$/i })[0]);

    await screen.findAllByText("Indexed document extraction");
    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining("/api/search?q=project+brief"),
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
