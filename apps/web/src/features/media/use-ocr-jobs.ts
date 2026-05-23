"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { uploadDriveFile } from "@/lib/drive-api";
import type { DriveFile } from "@/lib/drive-api";
import {
  fetchOcrJob,
  fetchOcrJobs,
  type OcrJobDetail,
  type OcrJobListResponse,
} from "@/lib/media-api";

const ocrJobsQueryKey = ["ocr-jobs"];

export function useOcrJobs(
  enabled = true,
  refetchInterval: number | false = false,
) {
  return useQuery<OcrJobListResponse>({
    queryKey: ocrJobsQueryKey,
    queryFn: fetchOcrJobs,
    enabled,
    refetchInterval,
  });
}

export function useOcrJobDetail(jobId: string | null, enabled = true) {
  return useQuery<OcrJobDetail>({
    queryKey: [...ocrJobsQueryKey, jobId],
    queryFn: () => fetchOcrJob(jobId ?? ""),
    enabled: enabled && jobId !== null,
  });
}

export function useUploadOcrSourceFile(onUploaded?: (file: DriveFile) => void) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: uploadDriveFile,
    onSuccess: (file) => {
      queryClient.invalidateQueries({ queryKey: ocrJobsQueryKey });
      onUploaded?.(file);
    },
  });
}
