import type { DriveFile } from "@/lib/drive-api";

export type DriveState =
  | "normal"
  | "loading"
  | "empty"
  | "error"
  | "permission-denied";

export const driveMockFiles: DriveFile[] = [
  {
    fileId: "file_invoice_demo",
    name: "Invoice_2026_05.pdf",
    contentType: "application/pdf",
    sizeBytes: 245760,
    checksumSha256:
      "5f70bf18a0860070165f70bf18a0860070165f70bf18a0860070165f70bf18",
    encrypted: true,
    uploadedAt: "2026-05-22T09:15:00Z",
    updatedAt: "2026-05-22T09:15:00Z",
  },
  {
    fileId: "file_budget_demo",
    name: "Quarterly budget.xlsx",
    contentType:
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    sizeBytes: 98304,
    checksumSha256:
      "1e08aa22d44379f4011e08aa22d44379f4011e08aa22d44379f4011e08aa",
    encrypted: true,
    uploadedAt: "2026-05-21T15:45:00Z",
    updatedAt: "2026-05-21T15:45:00Z",
  },
  {
    fileId: "file_notes_demo",
    name: "Meeting notes.txt",
    contentType: "text/plain",
    sizeBytes: 12288,
    checksumSha256:
      "9f4c0fd1123d7f57009f4c0fd1123d7f57009f4c0fd1123d7f57009f4c0f",
    encrypted: true,
    uploadedAt: "2026-05-20T11:20:00Z",
    updatedAt: "2026-05-20T11:20:00Z",
  },
];

export function parseDriveState(
  value: string | string[] | undefined,
): DriveState | undefined {
  const state = Array.isArray(value) ? value[0] : value;
  return state === "normal" ||
    state === "loading" ||
    state === "empty" ||
    state === "error" ||
    state === "permission-denied"
    ? state
    : undefined;
}
