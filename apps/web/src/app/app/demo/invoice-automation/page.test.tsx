import { beforeEach, describe, expect, it, vi } from "vitest";

const { redirectMock } = vi.hoisted(() => ({ redirectMock: vi.fn() }));

vi.mock("next/navigation", () => ({ redirect: redirectMock }));

import DemoInvoiceAutomationPage from "./page";

describe("DemoInvoiceAutomationPage", () => {
  beforeEach(() => {
    redirectMock.mockReset();
  });

  it("redirects the retired invoice demo route to Media/OCR", async () => {
    await DemoInvoiceAutomationPage();

    expect(redirectMock).toHaveBeenCalledWith("/app/media");
  });
});
