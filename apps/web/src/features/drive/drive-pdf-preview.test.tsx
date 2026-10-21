import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DrivePdfPreview } from "./drive-pdf-preview";

const mocks = vi.hoisted(() => ({
  getDocument: vi.fn(),
  createWorker: vi.fn(),
}));
vi.mock("pdfjs-dist", () => ({
  getDocument: mocks.getDocument,
  PDFWorker: { create: mocks.createWorker },
}));

describe("Drive PDF canvas isolation", () => {
  const destroy = vi.fn(async () => {});
  const terminate = vi.fn();
  const renderPage = vi.fn(() => ({
    promise: Promise.resolve(),
    cancel: vi.fn(),
  }));
  const getPage = vi.fn(async () => ({
    getViewport: () => ({ width: 400, height: 200 }),
    render: renderPage,
  }));
  beforeEach(() => {
    vi.stubGlobal(
      "Worker",
      class {
        terminate = terminate;
      },
    );
    vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(
      {} as CanvasRenderingContext2D,
    );
    mocks.createWorker.mockReturnValue({ destroy: vi.fn() });
    mocks.getDocument.mockReturnValue({
      promise: Promise.resolve({ numPages: 2, getPage }),
      destroy,
    });
  });
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
  });
  it("renders only canvases with annotations and forms disabled, paginates and destroys its worker", async () => {
    const view = render(
      <DrivePdfPreview
        blob={new Blob(["synthetic PDF"])}
        name="Synthetic.pdf"
      />,
    );
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Next page" })).toBeEnabled(),
    );
    expect(mocks.getDocument).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.any(Uint8Array),
        enableXfa: false,
        useWorkerFetch: false,
        disableFontFace: true,
        useWasm: false,
        maxImageSize: 4_000_000,
      }),
    );
    expect(renderPage).toHaveBeenCalledWith(
      expect.objectContaining({
        annotationMode: 0,
        canvas: expect.any(HTMLCanvasElement),
      }),
    );
    expect(
      view.container.querySelector("iframe, object, embed, script, a, form"),
    ).toBeNull();
    fireEvent.click(screen.getByRole("button", { name: "Next page" }));
    await waitFor(() => expect(getPage).toHaveBeenCalledWith(2));
    expect(screen.getByText("Page 2 of 2")).toBeInTheDocument();
    view.unmount();
    expect(destroy).toHaveBeenCalled();
    expect(terminate).toHaveBeenCalled();
  });
  it("fails closed on excessive page counts", async () => {
    mocks.getDocument.mockReturnValue({
      promise: Promise.resolve({ numPages: 41, getPage }),
      destroy,
    });
    render(
      <DrivePdfPreview
        blob={new Blob(["synthetic PDF"])}
        name="Synthetic.pdf"
      />,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "could not be rendered safely",
    );
    expect(getPage).not.toHaveBeenCalled();
  });
});
