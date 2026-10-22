"use client";

import { useRef, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import {
  trashDriveFile,
  trashDriveFolder,
  type DriveTrashMutation,
} from "@/lib/drive-api";
import { DriveActionDialog, driveButton } from "./drive-action-dialog";
import { useRefreshDrive } from "./use-drive-trash";

export function DriveTrashAction({
  kind,
  id,
  name,
  onTrashed,
}: {
  kind: "file" | "folder";
  id: string;
  name: string;
  onTrashed?: (group: DriveTrashMutation) => void;
}) {
  const [confirm, setConfirm] = useState(false);
  const refresh = useRefreshDrive();
  const submitting = useRef(false);
  const mutation = useMutation({
    mutationFn: () =>
      kind === "file" ? trashDriveFile(id) : trashDriveFolder(id),
    onSuccess: async (group) => {
      onTrashed?.(group);
      setConfirm(false);
      await refresh();
    },
    onSettled: () => {
      submitting.current = false;
    },
  });
  function submit() {
    if (submitting.current) return;
    submitting.current = true;
    mutation.mutate();
  }
  return (
    <>
      <button
        type="button"
        className={driveButton}
        disabled={mutation.isPending}
        aria-label={
          kind === "folder"
            ? `Move folder ${name} to Trash`
            : "Move file to Trash"
        }
        onClick={() => (kind === "folder" ? setConfirm(true) : submit())}
      >
        {mutation.isPending ? "Moving to Trash…" : "Move to Trash"}
      </button>
      {!confirm && mutation.isError ? (
        <p role="alert">{mutation.error.message}</p>
      ) : null}
      {confirm ? (
        <DriveActionDialog
          title="Move folder to Trash"
          onClose={() => setConfirm(false)}
        >
          <p className="mb-4 text-sm text-text-secondary">
            Move {name} and all active nested contents to Trash? They will be
            hidden together and recoverable for 30 days. Independently trashed
            items keep their existing deadlines.
          </p>
          {mutation.isError ? (
            <p role="alert">{mutation.error.message}</p>
          ) : null}
          <button
            type="button"
            className={driveButton}
            disabled={mutation.isPending}
            onClick={submit}
          >
            {mutation.isPending ? "Moving to Trash…" : "Move to Trash"}
          </button>
        </DriveActionDialog>
      ) : null}
    </>
  );
}
