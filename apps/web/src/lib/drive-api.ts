import { API_BASE_URL, workspaceHeaders } from "@/lib/api";

export type DriveFile = {
  fileId: string;
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

export async function uploadDriveFile(file: File): Promise<DriveFile> {
  const formData = new FormData();
  formData.append("file", file);

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
