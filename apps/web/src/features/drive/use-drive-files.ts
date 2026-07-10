"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import {
  fetchDriveFiles,
  uploadDriveFile,
  type DriveFile,
  type DriveFileListResponse,
} from "@/lib/drive-api";

const driveFilesQueryKey = ["drive-files"];

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
    mutationFn: uploadDriveFile,
    onSuccess: (file) => {
      queryClient.setQueryData<DriveFileListResponse>(
        driveFilesQueryKey,
        (current) => ({
          files: [
            file,
            ...(current?.files ?? []).filter(
              (currentFile) => currentFile.fileId !== file.fileId,
            ),
          ],
        }),
      );
      queryClient.invalidateQueries({ queryKey: driveFilesQueryKey });
      onUploaded?.(file);
    },
  });
}
