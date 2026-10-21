"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  fetchDriveFiles,
  fetchDriveFolderContents,
  uploadDriveFile,
  type DriveFile,
  type DriveFileListResponse,
  type DriveFolderContents,
} from "@/lib/drive-api";

const driveFilesQueryKey = ["drive-files"];

export const driveFolderQueryKey = (folderId: string | null) => [
  "drive-folder-contents",
  folderId ?? "root",
];

export function useDriveFolderContents(
  folderId: string | null,
  enabled = true,
) {
  return useQuery({
    queryKey: driveFolderQueryKey(folderId),
    queryFn: ({ signal }) => fetchDriveFolderContents(folderId, signal),
    enabled,
  });
}

export function useDriveFiles(enabled = true) {
  return useQuery<DriveFileListResponse>({
    queryKey: driveFilesQueryKey,
    queryFn: fetchDriveFiles,
    enabled,
  });
}

export function useUploadDriveFile(onUploaded?: (file: DriveFile) => void) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ file, folderId }: { file: File; folderId: string | null }) =>
      uploadDriveFile(file, folderId),
    onSuccess: (file, { folderId }) => {
      queryClient.setQueryData<DriveFolderContents>(
        driveFolderQueryKey(folderId),
        (current) => ({
          folders: current?.folders ?? [],
          files: [
            file,
            ...(current?.files ?? []).filter(
              (currentFile) => currentFile.fileId !== file.fileId,
            ),
          ],
        }),
      );
      queryClient.invalidateQueries({
        queryKey: driveFolderQueryKey(folderId),
        exact: true,
      });
      onUploaded?.(file);
    },
  });
}
