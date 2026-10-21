import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ThemeProvider } from "@/components/theme";
import { DriveScreen } from "./drive-screen";
import { driveMockFiles } from "@/lib/drive-mock-data";
import type { DriveFile } from "@/lib/drive-api";

const folder = (
  folderId: string,
  name: string,
  parentFolderId: string | null = null,
) => ({
  folderId,
  name,
  parentFolderId,
  createdAt: "2026-10-18T00:00:00Z",
  updatedAt: "2026-10-18T00:00:00Z",
});
const projects = folder("projects", "Projects");
const invoices = folder("invoices", "Invoices", "projects");
const source = { ...driveMockFiles[0], folderId: null };

describe("Drive folder explorer", () => {
  let files: DriveFile[] = [source];
  let folders = [projects, invoices];
  let fetchMock: ReturnType<
    typeof vi.fn<(input: string, init?: RequestInit) => Promise<Response>>
  >;
  beforeEach(() => {
    files = [source];
    folders = [projects, invoices];
    Object.defineProperty(HTMLDialogElement.prototype, "showModal", {
      configurable: true,
      value: function (this: HTMLDialogElement) {
        this.setAttribute("open", "");
      },
    });
    Object.defineProperty(HTMLDialogElement.prototype, "close", {
      configurable: true,
      value: function (this: HTMLDialogElement) {
        this.removeAttribute("open");
      },
    });
    vi.stubGlobal(
      "URL",
      Object.assign(URL, {
        createObjectURL: vi.fn(() => "blob:synthetic"),
        revokeObjectURL: vi.fn(),
      }),
    );
    fetchMock = vi.fn(async (input: string, init?: RequestInit) => {
      const url = new URL(input, "http://localhost");
      const body = typeof init?.body === "string" ? JSON.parse(init.body) : {};
      if (url.pathname.endsWith("/contents")) {
        const id = url.searchParams.get("folderId");
        return Response.json({
          folders: folders.filter((f) => f.parentFolderId === id),
          files: files.filter((f) => f.folderId === id),
        });
      }
      if (url.pathname.endsWith("/name")) {
        if (url.pathname.includes("/folders/")) {
          folders = folders.map((f) =>
            url.pathname.includes(`/${f.folderId}/`)
              ? { ...f, name: body.name }
              : f,
          );
          return Response.json(
            folders.find((f) => url.pathname.includes(`/${f.folderId}/`)),
          );
        }
        files = files.map((f) => ({ ...f, name: body.name }));
        return Response.json(files[0]);
      }
      if (url.pathname.endsWith("/location")) {
        if (url.pathname.includes("/folders/")) {
          folders = folders.map((f) =>
            url.pathname.includes(`/${f.folderId}/`)
              ? { ...f, parentFolderId: body.destinationFolderId }
              : f,
          );
          return Response.json(
            folders.find((f) => url.pathname.includes(`/${f.folderId}/`)),
          );
        }
        files = files.map((f) => ({
          ...f,
          folderId: body.destinationFolderId,
        }));
        return Response.json(files[0]);
      }
      if (init?.method === "POST" && init.body instanceof FormData) {
        const uploaded = {
          ...source,
          fileId: "uploaded",
          name: "synthetic.txt",
          folderId: init.body.get("folderId") as string | null,
        };
        files.push(uploaded);
        return Response.json(uploaded, { status: 201 });
      }
      if (url.pathname.endsWith("/folders") && init?.method === "POST") {
        const created = folder("new", body.name, body.parentFolderId);
        folders.push(created);
        return Response.json(created, { status: 201 });
      }
      if (url.pathname.endsWith("/download"))
        return new Response("synthetic archive", {
          headers: { "Content-Type": "application/zip" },
        });
      if (url.pathname.endsWith(`/files/${source.fileId}`))
        return Response.json(files[0]);
      return Response.json(
        folders.find((folder) =>
          url.pathname.endsWith(`/${folder.folderId}`),
        ) ?? projects,
      );
    });
    vi.stubGlobal("fetch", fetchMock);
  });
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });
  function mount(initialFileId?: string) {
    const client = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    render(
      <QueryClientProvider client={client}>
        <ThemeProvider>
          <DriveScreen initialFileId={initialFileId} />
        </ThemeProvider>
      </QueryClientProvider>,
    );
    return client;
  }
  it("opens a linked file in its nested folder", async () => {
    files = [{ ...source, folderId: "invoices" }];
    mount(source.fileId);
    await waitFor(() =>
      expect(screen.getByLabelText("File details")).toHaveTextContent(
        source.name,
      ),
    );
    expect(
      screen.getAllByRole("navigation", { name: "Drive folders" })[0],
    ).toHaveTextContent("Invoices");
  });
  it("inspects a shared linked file without exposing its private parent", async () => {
    files = [{ ...source, folderId: "private-parent" }];
    const original = fetchMock.getMockImplementation()!;
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) =>
      url.endsWith("/folders/private-parent")
        ? Response.json({}, { status: 404 })
        : original(url, init),
    );
    mount(source.fileId);
    await waitFor(() =>
      expect(screen.getByLabelText("File details")).toHaveTextContent(
        source.name,
      ),
    );
    expect(
      screen.getAllByRole("navigation", { name: "Drive folders" })[0],
    ).not.toHaveTextContent("private-parent");
  });
  it("navigatesNestedFoldersWithBreadcrumbs", async () => {
    mount();
    fireEvent.click(
      (
        await screen.findAllByRole("button", { name: "Open folder Projects" })
      )[0],
    );
    fireEvent.click(
      (
        await screen.findAllByRole("button", { name: "Open folder Invoices" })
      )[0],
    );
    const crumbs = screen.getAllByRole("navigation", {
      name: "Drive folders",
    })[0];
    expect(crumbs).toHaveTextContent("Projects");
    expect(crumbs).toHaveTextContent("Invoices");
    fireEvent.click(within(crumbs).getByRole("button", { name: "Drive" }));
    expect(
      (
        await screen.findAllByRole("button", { name: "Open folder Projects" })
      )[0],
    ).toBeInTheDocument();
  });
  it("uploadsIntoCurrentFolder", async () => {
    mount();
    fireEvent.click(
      (
        await screen.findAllByRole("button", { name: "Open folder Projects" })
      )[0],
    );
    await screen.findAllByRole("button", { name: "Open folder Invoices" });
    fireEvent.change(screen.getAllByLabelText("Upload file")[0], {
      target: {
        files: {
          item: () =>
            new File(["synthetic"], "synthetic.txt", { type: "text/plain" }),
          length: 1,
        },
      },
    });
    await waitFor(() =>
      expect(
        files.some((f) => f.fileId === "uploaded" && f.folderId === "projects"),
      ).toBe(true),
    );
    expect(await screen.findAllByText("synthetic.txt")).not.toHaveLength(0);
  });
  it("renamesAndMovesFileWithoutLosingSelection", async () => {
    mount();
    await screen.findAllByRole("button", { name: "Open folder Projects" });
    fireEvent.click(screen.getByRole("button", { name: "Rename file" }));
    fireEvent.change(screen.getByRole("textbox", { name: "Name" }), {
      target: { value: "Renamed.pdf" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save name" }));
    await waitFor(() =>
      expect(screen.getByLabelText("File details")).toHaveTextContent(
        "Renamed.pdf",
      ),
    );
    fireEvent.click(screen.getByRole("button", { name: "Move file" }));
    const picker = screen.getByRole("dialog", { name: "Move file" });
    fireEvent.click(
      await within(picker).findByRole("button", {
        name: "Open destination Projects",
      }),
    );
    await waitFor(() =>
      expect(
        within(picker).getByRole("button", { name: "Move here" }),
      ).toBeEnabled(),
    );
    fireEvent.click(within(picker).getByRole("button", { name: "Move here" }));
    await waitFor(() =>
      expect(screen.getByLabelText("File details")).toHaveTextContent(
        "Renamed.pdf",
      ),
    );
    await waitFor(() => expect(files[0].folderId).toBe("projects"));
  });
  it("downloadsFolderZip", async () => {
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, "click")
      .mockImplementation(() => {});
    mount();
    await screen.findAllByRole("button", { name: "Open folder Projects" });
    fireEvent.click(
      screen.getAllByRole("button", { name: "Download Projects as ZIP" })[0],
    );
    await waitFor(() => expect(click).toHaveBeenCalled());
    expect(
      fetchMock.mock.calls.some(([url]) =>
        url.endsWith("/folders/projects/download"),
      ),
    ).toBe(true);
  });
  it("renames folders and invalidates only the affected parent", async () => {
    const client = mount();
    client.setQueryData(["drive-folder-contents", "unrelated"], {
      folders: [],
      files: [],
    });
    const invalidate = vi.spyOn(client, "invalidateQueries");
    await screen.findAllByRole("button", { name: "Open folder Projects" });
    fireEvent.click(
      screen.getAllByRole("button", { name: "Rename folder Projects" })[0],
    );
    fireEvent.change(screen.getByRole("textbox", { name: "Name" }), {
      target: { value: "Renamed projects" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save name" }));
    await screen.findAllByRole("button", {
      name: "Open folder Renamed projects",
    });
    expect(
      invalidate.mock.calls.every(
        ([filters]) =>
          JSON.stringify(filters?.queryKey) ===
            JSON.stringify(["drive-folder-contents", "root"]) && filters?.exact,
      ),
    ).toBe(true);
    expect(
      client.getQueryState(["drive-folder-contents", "unrelated"])
        ?.isInvalidated,
    ).toBe(false);
  });
  it("shows mutation conflicts without discarding the name or selection", async () => {
    mount();
    await screen.findAllByRole("button", { name: "Open folder Projects" });
    const original = fetchMock.getMockImplementation()!;
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) =>
      url.endsWith("/name")
        ? Response.json({ message: "private conflict detail" }, { status: 409 })
        : original(url, init),
    );
    fireEvent.click(screen.getByRole("button", { name: "Rename file" }));
    fireEvent.change(screen.getByRole("textbox", { name: "Name" }), {
      target: { value: "Duplicate.pdf" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save name" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "An item with that name already exists",
    );
    expect(screen.getByRole("textbox", { name: "Name" })).toHaveValue(
      "Duplicate.pdf",
    );
    expect(screen.getByLabelText("File details")).toHaveTextContent(
      source.name,
    );
    expect(
      screen.queryByText("private conflict detail"),
    ).not.toBeInTheDocument();
  });
  it("mobileFolderPickerAndEmptyState", async () => {
    mount();
    fireEvent.click(
      (
        await screen.findAllByRole("button", { name: "Open folder Projects" })
      )[0],
    );
    fireEvent.click(
      (
        await screen.findAllByRole("button", { name: "Open folder Invoices" })
      )[0],
    );
    expect(
      (await screen.findAllByText("This folder is empty"))[0],
    ).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: "New folder" })[0]);
    fireEvent.change(screen.getByRole("textbox", { name: "Name" }), {
      target: { value: "2026" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Create folder" }));
    expect(
      (await screen.findAllByRole("button", { name: "Open folder 2026" }))[0],
    ).toBeInTheDocument();
  });
  it("shows denied contents without leaking stale names", async () => {
    mount();
    fireEvent.click(
      (
        await screen.findAllByRole("button", { name: "Open folder Projects" })
      )[0],
    );
    fetchMock.mockImplementation(async () =>
      Response.json({}, { status: 404 }),
    );
    fireEvent.click(
      (
        await screen.findAllByRole("button", { name: "Open folder Invoices" })
      )[0],
    );
    expect(
      (await screen.findAllByText("Drive access is not available"))[0],
    ).toBeInTheDocument();
    expect(screen.queryByText(source.name)).not.toBeInTheDocument();
  });
});
