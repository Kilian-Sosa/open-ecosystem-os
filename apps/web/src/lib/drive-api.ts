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
    throw new Error("Drive file could not be uploaded");
  }

  return response.json() as Promise<DriveFile>;
}
