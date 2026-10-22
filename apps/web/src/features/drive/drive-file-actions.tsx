"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  downloadDriveFile,
  moveDriveFile,
  renameDriveFile,
  type DriveFile,
  type DriveTrashMutation,
} from "@/lib/drive-api";
import { driveFolderQueryKey } from "./use-drive-files";
import {
  DriveMoveDialog,
  DriveNameDialog,
  driveButton,
} from "./drive-action-dialog";
import { DrivePreview } from "./drive-preview";
import { DriveTrashAction } from "./drive-trash-action";

export function DriveFileActions({
  file,
  onChanged,
  onTrashed,
}: {
  file: DriveFile;
  onChanged: (file: DriveFile, moved: boolean) => void;
  onTrashed?: (group: DriveTrashMutation) => void;
}) {
  const [action, setAction] = useState<"rename" | "move" | "preview" | null>(
    null,
  );
  const client = useQueryClient();
  const download = useMutation({ mutationFn: () => downloadDriveFile(file) });
  async function changed(updated: DriveFile, moved: boolean) {
    onChanged(updated, moved);
    await Promise.all(
      [file.folderId ?? null, updated.folderId ?? null].map((id) =>
        client.invalidateQueries({
          queryKey: driveFolderQueryKey(id),
          exact: true,
        }),
      ),
    );
  }
  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        <button className={driveButton} onClick={() => setAction("preview")}>
          Preview file
        </button>
        <button
          className={driveButton}
          disabled={download.isPending}
          onClick={() => download.mutate()}
        >
          {download.isPending ? "Downloading…" : "Download file"}
        </button>
        <button className={driveButton} onClick={() => setAction("rename")}>
          Rename file
        </button>
        <button className={driveButton} onClick={() => setAction("move")}>
          Move file
        </button>
        <DriveTrashAction
          kind="file"
          id={file.fileId}
          name={file.name}
          onTrashed={onTrashed}
        />
      </div>
      {download.isError ? <p role="alert">{download.error.message}</p> : null}
      {action === "rename" ? (
        <DriveNameDialog
          title="Rename file"
          initialName={file.name}
          submitLabel="Save name"
          onClose={() => setAction(null)}
          onSave={async (name) =>
            changed(await renameDriveFile(file.fileId, name), false)
          }
        />
      ) : null}
      {action === "move" ? (
        <DriveMoveDialog
          kind="file"
          sourceFolderId={file.folderId ?? null}
          onClose={() => setAction(null)}
          onMove={async (destination) =>
            changed(await moveDriveFile(file.fileId, destination), true)
          }
        />
      ) : null}
      {action === "preview" ? (
        <DrivePreview
          key={`${file.fileId}:${file.updatedAt}`}
          file={file}
          onClose={() => setAction(null)}
        />
      ) : null}
    </div>
  );
}
