"use client";

import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryObserverOptions,
} from "@tanstack/react-query";

import { uploadDriveFile } from "@/lib/drive-api";
import type { DriveFile } from "@/lib/drive-api";
import {
  fetchOcrJob,
  fetchOcrJobs,
  type OcrJobDetail,
  type OcrJobListResponse,
} from "@/lib/media-api";

const ocrJobsQueryKey = ["ocr-jobs"];
type OcrJobsRefetchInterval =
  QueryObserverOptions<OcrJobListResponse>["refetchInterval"];
type OcrJobDetailRefetchInterval =
  QueryObserverOptions<OcrJobDetail>["refetchInterval"];

export function useOcrJobs(
  enabled = true,
  refetchInterval: OcrJobsRefetchInterval = false,
) {
  return useQuery<OcrJobListResponse>({
    queryKey: ocrJobsQueryKey,
    queryFn: fetchOcrJobs,
    enabled,
    refetchInterval,
  });
}

export function useOcrJobDetail(
  jobId: string | null,
  enabled = true,
  refetchInterval: OcrJobDetailRefetchInterval = false,
) {
  return useQuery<OcrJobDetail>({
    queryKey: [...ocrJobsQueryKey, jobId],
    queryFn: () => fetchOcrJob(jobId ?? ""),
    enabled: enabled && jobId !== null,
    refetchInterval,
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
