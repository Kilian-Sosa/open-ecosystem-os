import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { AppProviders } from "@/components/providers/app-providers";
import { AppShell } from "./app-shell";

describe("AppShell", () => {
  it("renders seeded actor and workspace details from the default bootstrap", () => {
    render(
      <AppProviders>
        <AppShell activeHref="/app/dashboard">
          <div>Workspace content</div>
        </AppShell>
      </AppProviders>,
    );

    expect(screen.getAllByText("Demo Admin")).toHaveLength(1);
    expect(
      screen.getAllByText("Open Ecosystem Demo Workspace").length,
    ).toBeGreaterThan(0);
    expect(screen.getAllByText("Workspace Admin").length).toBeGreaterThan(0);
  });
});
