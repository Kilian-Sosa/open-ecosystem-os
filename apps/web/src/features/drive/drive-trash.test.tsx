import {
  fireEvent,
  render,
  screen,
  waitFor,
  within,
  cleanup,
} from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { ThemeProvider } from "@/components/theme";
import { driveMockFiles } from "@/lib/drive-mock-data";
import { restoreDriveTrashItem } from "@/lib/drive-api";
import { DriveScreen } from "./drive-screen";
import { DriveTrashScreen } from "./drive-trash-screen";

vi.mock("@/components/layout/app-shell", () => ({
  AppShell: ({
    children,
    inspector,
  }: {
    children: import("react").ReactNode;
    inspector?: import("react").ReactNode;
  }) => (
    <main>
      {children}
      {inspector}
    </main>
  ),
}));

const entry = {
  groupId: "synthetic-group",
  rootItemType: "file",
  rootItemId: "synthetic-file",
  name: "Synthetic invoice.txt",
  originalParentFolderId: null,
  originalLocation: "Drive",
  trashedAt: "2026-10-22T12:00:00Z",
  purgeAfter: "2026-11-21T12:00:00Z",
  state: "TRASHED",
  canRestore: true,
};
const folder = {
  folderId: "synthetic-folder",
  parentFolderId: null,
  name: "Synthetic folder",
  createdAt: entry.trashedAt,
  updatedAt: entry.trashedAt,
};
let entries = [entry];
let files = [driveMockFiles[0]];
let folders: (typeof folder)[] = [];
let fetchMock: ReturnType<
  typeof vi.fn<(input: string, init?: RequestInit) => Promise<Response>>
>;
beforeEach(() => {
  entries = [{ ...entry }];
  files = [driveMockFiles[0]];
  folders = [];
  Object.defineProperty(HTMLDialogElement.prototype, "showModal", {
    configurable: true,
    value() {
      this.setAttribute("open", "");
    },
  });
  Object.defineProperty(HTMLDialogElement.prototype, "close", {
    configurable: true,
    value() {
      this.removeAttribute("open");
    },
  });
  fetchMock = vi.fn(async (input, init) => {
    const url = new URL(input, "http://localhost");
    if (url.pathname.endsWith("/restore")) {
      entries = [];
      files = [driveMockFiles[0]];
      return Response.json(entry);
    }
    if (url.pathname.endsWith("/purge")) {
      entries = [{ ...entry, state: "PURGING", canRestore: false }];
      return Response.json({ groupId: entry.groupId, state: "PURGING" });
    }
    if (url.pathname.endsWith("/trash") && init?.method === "POST") {
      files = [];
      folders = [];
      return Response.json(entry);
    }
    if (url.pathname.endsWith("/trash")) return Response.json(entries);
    if (url.pathname.endsWith("/contents"))
      return Response.json({ files, folders: url.search ? [] : folders });
    throw new Error(`Unexpected synthetic URL ${url.pathname}`);
  });
  vi.stubGlobal("fetch", fetchMock);
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});
function mount(trash = true) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <ThemeProvider>
        {trash ? <DriveTrashScreen /> : <DriveScreen />}
      </ThemeProvider>
    </QueryClientProvider>,
  );
}
function writes(suffix: string) {
  return fetchMock.mock.calls.filter(
    ([url, init]) => url.endsWith(suffix) && init?.method === "POST",
  );
}

it("trashShowsDeletionAndPurgeDates", async () => {
  mount();
  expect(await screen.findByText(entry.name)).toBeVisible();
  expect(
    document.querySelector(`time[datetime="${entry.trashedAt}"]`),
  ).toBeVisible();
  expect(
    document.querySelector(`time[datetime="${entry.purgeAfter}"]`),
  ).toBeVisible();
  expect(screen.getByRole("link", { name: "Back to Drive" })).toHaveAttribute(
    "href",
    "/app/drive",
  );
});
it("permanentDeleteRequiresExplicitConfirmationAndShowsStillProcessing", async () => {
  mount();
  fireEvent.click(
    await screen.findByRole("button", { name: "Delete permanently" }),
  );
  expect(writes("/purge")).toHaveLength(0);
  const dialog = screen.getByRole("dialog", { name: "Delete permanently" });
  expect(dialog).toHaveTextContent(/cannot be undone/i);
  fireEvent.click(
    within(dialog).getByRole("button", { name: "Delete permanently" }),
  );
  await waitFor(() => expect(writes("/purge")).toHaveLength(1));
  expect(await screen.findByText(/cleanup is still processing/i)).toBeVisible();
  expect(
    screen.queryByRole("button", { name: "Restore original location" }),
  ).not.toBeInTheDocument();
});
it("cancelPermanentDeletionMakesNoRequest", async () => {
  mount();
  fireEvent.click(
    await screen.findByRole("button", { name: "Delete permanently" }),
  );
  fireEvent.click(
    within(screen.getByRole("dialog")).getByRole("button", { name: "Cancel" }),
  );
  expect(writes("/purge")).toHaveLength(0);
});
it("restoreToRootSendsExplicitNull", async () => {
  mount();
  fireEvent.click(
    await screen.findByRole("button", { name: "Choose restore destination" }),
  );
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "Restore here" })).toBeEnabled(),
  );
  fireEvent.click(await screen.findByRole("button", { name: "Restore here" }));
  await waitFor(() => expect(writes("/restore")).toHaveLength(1));
  expect(JSON.parse(writes("/restore")[0][1]!.body as string)).toEqual({
    destinationFolderId: null,
  });
});
it("restoreFolderIntoChosenDestination", async () => {
  folders = [folder];
  mount();
  fireEvent.click(
    await screen.findByRole("button", { name: "Choose restore destination" }),
  );
  fireEvent.click(
    await screen.findByRole("button", {
      name: `Open destination ${folder.name}`,
    }),
  );
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "Restore here" })).toBeEnabled(),
  );
  fireEvent.click(await screen.findByRole("button", { name: "Restore here" }));
  await waitFor(() => expect(writes("/restore")).toHaveLength(1));
  expect(JSON.parse(writes("/restore")[0][1]!.body as string)).toEqual({
    destinationFolderId: folder.folderId,
  });
});
it("omittedRestoreDestinationIsDistinctFromRoot", async () => {
  await restoreDriveTrashItem(entry.groupId);
  expect(JSON.parse(writes("/restore")[0][1]!.body as string)).toEqual({});
});
it("nonEmptyFolderTrashExplainsRecursiveImpactAndCancelMakesNoRequest", async () => {
  folders = [folder];
  mount(false);
  fireEvent.click(
    await screen.findByRole("button", {
      name: `Move folder ${folder.name} to Trash`,
    }),
  );
  const dialog = screen.getByRole("dialog", { name: "Move folder to Trash" });
  expect(dialog).toHaveTextContent(/all active nested contents/i);
  expect(dialog).toHaveTextContent(/30 days/i);
  fireEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));
  expect(writes("/trash")).toHaveLength(0);
});
it("confirmFolderTrashSubmitsOnce", async () => {
  folders = [folder];
  mount(false);
  fireEvent.click(
    await screen.findByRole("button", {
      name: `Move folder ${folder.name} to Trash`,
    }),
  );
  const confirm = within(screen.getByRole("dialog")).getByRole("button", {
    name: "Move to Trash",
  });
  fireEvent.click(confirm);
  fireEvent.click(confirm);
  await waitFor(() => expect(writes("/trash")).toHaveLength(1));
  expect(await screen.findByRole("button", { name: "Undo" })).toBeVisible();
});
it("trashActionHidesFileWithUndoNotPermanentWarning", async () => {
  mount(false);
  fireEvent.click(
    await screen.findByRole("button", { name: "Move file to Trash" }),
  );
  fireEvent.click(await screen.findByRole("button", { name: "Undo" }));
  await waitFor(() => expect(writes("/restore")).toHaveLength(1));
  expect(screen.queryByText(/cannot be undone/i)).not.toBeInTheDocument();
});
it("supportsEmptyErrorAndPermissionDeniedStates", async () => {
  entries = [];
  mount();
  expect(await screen.findByText("Trash is empty")).toBeVisible();
  cleanup();
  fetchMock.mockImplementation(async () => Response.json({}, { status: 403 }));
  mount();
  expect(await screen.findByText(/permission denied/i)).toBeVisible();
  cleanup();
  fetchMock.mockImplementation(async () => Response.json({}, { status: 500 }));
  mount();
  expect(
    await screen.findByRole("button", { name: "Try again" }),
  ).toBeVisible();
});
