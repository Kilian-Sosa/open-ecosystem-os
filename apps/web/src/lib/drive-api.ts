import { API_BASE_URL, workspaceHeaders } from "@/lib/api";

export type DriveFile = {
  fileId: string;
  folderId?: string | null;
  visibility: "private" | "workspace";
  name: string;
  contentType: string;
  sizeBytes: number;
  checksumSha256: string;
  encrypted: boolean;
  uploadedAt: string;
  updatedAt: string;
};

export type DriveFileListResponse = {
  files: DriveFile[];
};

export class DriveUploadError extends Error {}

export class DriveApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export type DriveFolder = {
  folderId: string;
  parentFolderId: string | null;
  name: string;
  createdAt: string;
  updatedAt: string;
};

export type DriveFolderContents = {
  folders: DriveFolder[];
  files: DriveFile[];
};

export type DriveTrashResponse = {
  groupId: string;
  rootItemType: "file" | "folder";
  rootItemId: string;
  name: string;
  originalParentFolderId: string | null;
  originalLocation: string | null;
  trashedAt: string;
  purgeAfter: string;
  state: "TRASHED" | "PURGING" | "PURGED" | "RESTORED";
  canRestore: boolean;
};

export type DriveTrashMutation = Pick<
  DriveTrashResponse,
  "groupId" | "trashedAt" | "purgeAfter"
>;

export async function fetchDriveTrash(
  signal?: AbortSignal,
): Promise<DriveTrashResponse[]> {
  return (await driveRequest("trash", { signal })).json();
}
export const trashDriveFile = (fileId: string) =>
  writeDrive<DriveTrashMutation>(
    `files/${encodeURIComponent(fileId)}/trash`,
    "POST",
    {},
  );
export const trashDriveFolder = (folderId: string) =>
  writeDrive<DriveTrashMutation>(
    `folders/${encodeURIComponent(folderId)}/trash`,
    "POST",
    {},
  );
export const restoreDriveTrashItem = (
  groupId: string,
  destinationFolderId?: string | null,
) =>
  writeDrive<DriveTrashResponse>(
    `trash/${encodeURIComponent(groupId)}/restore`,
    "POST",
    { destinationFolderId },
  );
export const permanentlyDeleteDriveTrashItem = (groupId: string) =>
  writeDrive<Pick<DriveTrashResponse, "groupId" | "state">>(
    `trash/${encodeURIComponent(groupId)}/purge`,
    "POST",
    {},
  );

async function driveRequest(
  path: string,
  init?: RequestInit,
): Promise<Response> {
  const response = await fetch(`${API_BASE_URL}/api/drive/${path}`, {
    ...init,
    cache: "no-store",
    headers: { ...workspaceHeaders, ...init?.headers },
  });
  if (!response.ok) {
    throw new DriveApiError(
      response.status,
      response.status === 403 || response.status === 404
        ? "This Drive item is unavailable or you do not have access."
        : response.status === 409
          ? "An item with that name already exists, or the destination is invalid."
          : response.status === 400
            ? "Check the name and destination, then try again."
            : "The Drive action could not be completed. Try again.",
    );
  }
  return response;
}

export async function fetchDriveFolderContents(
  folderId: string | null,
  signal?: AbortSignal,
): Promise<DriveFolderContents> {
  const query =
    folderId === null ? "" : `?folderId=${encodeURIComponent(folderId)}`;
  return (await driveRequest(`folders/contents${query}`, { signal })).json();
}

export async function fetchDriveFolder(
  folderId: string,
  signal?: AbortSignal,
): Promise<DriveFolder> {
  return (
    await driveRequest(`folders/${encodeURIComponent(folderId)}`, { signal })
  ).json();
}

export async function fetchDriveFile(
  fileId: string,
  signal?: AbortSignal,
): Promise<DriveFile> {
  return (
    await driveRequest(`files/${encodeURIComponent(fileId)}`, { signal })
  ).json();
}

export async function fetchDriveFolderPath(
  folderId: string | null,
  signal?: AbortSignal,
): Promise<DriveFolder[]> {
  const path: DriveFolder[] = [];
  const visited = new Set<string>();
  let id = folderId;
  while (id !== null) {
    if (visited.has(id) || path.length >= 100)
      throw new Error("Folder path is unavailable.");
    visited.add(id);
    const folder = await fetchDriveFolder(id, signal);
    path.unshift(folder);
    id = folder.parentFolderId;
  }
  return path;
}

async function writeDrive<T>(
  path: string,
  method: string,
  body: object,
): Promise<T> {
  return (
    await driveRequest(path, {
      method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    })
  ).json();
}

export const createDriveFolder = (
  name: string,
  parentFolderId: string | null,
) => writeDrive<DriveFolder>("folders", "POST", { name, parentFolderId });
export const renameDriveFile = (fileId: string, name: string) =>
  writeDrive<DriveFile>(`files/${encodeURIComponent(fileId)}/name`, "PATCH", {
    name,
  });
export const renameDriveFolder = (folderId: string, name: string) =>
  writeDrive<DriveFolder>(
    `folders/${encodeURIComponent(folderId)}/name`,
    "PATCH",
    { name },
  );
export const moveDriveFile = (
  fileId: string,
  destinationFolderId: string | null,
) =>
  writeDrive<DriveFile>(
    `files/${encodeURIComponent(fileId)}/location`,
    "PATCH",
    { destinationFolderId },
  );
export const moveDriveFolder = (
  folderId: string,
  destinationFolderId: string | null,
) =>
  writeDrive<DriveFolder>(
    `folders/${encodeURIComponent(folderId)}/location`,
    "PATCH",
    { destinationFolderId },
  );

export async function fetchDrivePreview(
  file: DriveFile,
  signal: AbortSignal,
): Promise<Blob> {
  const response = await driveRequest(
    `files/${encodeURIComponent(file.fileId)}/preview`,
    { signal },
  );
  const contentType = response.headers
    .get("Content-Type")
    ?.split(";")[0]
    .trim();
  if (
    contentType !== file.contentType ||
    !["application/pdf", "image/png", "image/jpeg", "text/plain"].includes(
      contentType,
    )
  ) {
    throw new Error("Preview is unavailable for this file type.");
  }
  const maxBytes = 25 * 1024 * 1024;
  if (Number(response.headers.get("Content-Length")) > maxBytes) {
    await response.body?.cancel();
    throw new Error("Preview is too large.");
  }
  const reader = response.body?.getReader();
  if (!reader) throw new Error("Preview could not be loaded.");
  const chunks: Uint8Array<ArrayBuffer>[] = [];
  let size = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      size += value.byteLength;
      if (size > maxBytes) throw new Error("Preview is too large.");
      chunks.push(new Uint8Array(value));
    }
  } finally {
    await reader.cancel();
    reader.releaseLock();
  }
  return new Blob(chunks, { type: contentType });
}

async function saveDownload(path: string, name: string) {
  const response = await driveRequest(path);
  const blob = await response.blob();
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = name.replace(/[\\/\x00-\x1f\x7f]/g, "_");
  document.body.append(link);
  try {
    link.click();
  } finally {
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}

export const downloadDriveFile = (file: DriveFile) =>
  saveDownload(`files/${encodeURIComponent(file.fileId)}/download`, file.name);
type ApiErrorResponse = {
  message?: unknown;
};

export async function fetchDriveFiles(): Promise<DriveFileListResponse> {
  const response = await fetch(`${API_BASE_URL}/api/drive/files`, {
    headers: workspaceHeaders,
  });

  if (!response.ok) {
    throw new Error("Drive files could not be loaded");
  }

  return response.json() as Promise<DriveFileListResponse>;
}

export async function uploadDriveFile(
  file: File,
  folderId: string | null = null,
): Promise<DriveFile> {
  const formData = new FormData();
  formData.append("file", file);
  if (folderId !== null) formData.append("folderId", folderId);

  const response = await fetch(`${API_BASE_URL}/api/drive/files`, {
    method: "POST",
    headers: workspaceHeaders,
    body: formData,
  });

  if (!response.ok) {
    throw new DriveUploadError(
      await publicApiErrorMessage(response, "Drive file could not be uploaded"),
    );
  }

  return response.json() as Promise<DriveFile>;
}

async function publicApiErrorMessage(
  response: Response,
  fallback: string,
): Promise<string> {
  try {
    const payload: unknown = await response.json();
    if (
      isApiErrorResponse(payload) &&
      typeof payload.message === "string" &&
      payload.message.trim()
    ) {
      return payload.message.trim();
    }
  } catch {
    return fallback;
  }

  return fallback;
}

function isApiErrorResponse(payload: unknown): payload is ApiErrorResponse {
  return (
    typeof payload === "object" && payload !== null && "message" in payload
  );
}
