"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { uploadDriveFile } from "@/lib/drive-api";
import {
  fetchOcrJob,
  fetchOcrJobs,
  type OcrJobDetail,
  type OcrJobListResponse,
} from "@/lib/media-api";

const ocrJobsQueryKey = ["ocr-jobs"];

export function useOcrJobs(enabled = true) {
  return useQuery<OcrJobListResponse>({
    queryKey: ocrJobsQueryKey,
    queryFn: fetchOcrJobs,
    enabled,
  });
}

export function useOcrJobDetail(jobId: string | null, enabled = true) {
  return useQuery<OcrJobDetail>({
    queryKey: [...ocrJobsQueryKey, jobId],
    queryFn: () => fetchOcrJob(jobId ?? ""),
    enabled: enabled && jobId !== null,
  });
}

export function useUploadOcrSourceFile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: uploadDriveFile,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ocrJobsQueryKey });
    },
  });
}
