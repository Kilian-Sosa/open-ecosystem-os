"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchDriveTrash } from "@/lib/drive-api";

export function useDriveTrash() {
  return useQuery({
    queryKey: ["drive-trash"],
    queryFn: ({ signal }) => fetchDriveTrash(signal),
    refetchInterval: 30_000,
  });
}

export function useRefreshDrive() {
  const client = useQueryClient();
  return async () => {
    await Promise.all([
      client.invalidateQueries({ queryKey: ["drive-trash"] }),
      client.invalidateQueries({ queryKey: ["drive-folder-contents"] }),
      client.invalidateQueries({ queryKey: ["drive-files"] }),
    ]);
  };
}
