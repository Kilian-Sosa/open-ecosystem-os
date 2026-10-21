"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  downloadDriveFolderZip,
  moveDriveFolder,
  renameDriveFolder,
  type DriveFolder,
} from "@/lib/drive-api";
import { driveFolderQueryKey } from "./use-drive-files";
import {
  DriveMoveDialog,
  DriveNameDialog,
  driveButton,
} from "./drive-action-dialog";

export function DriveFolderActions({ folder }: { folder: DriveFolder }) {
  const [action, setAction] = useState<"rename" | "move" | null>(null);
  const client = useQueryClient();
  const download = useMutation({
    mutationFn: () => downloadDriveFolderZip(folder),
  });
  async function changed(updated: DriveFolder) {
    await Promise.all(
      [folder.parentFolderId, updated.parentFolderId].map((id) =>
        client.invalidateQueries({
          queryKey: driveFolderQueryKey(id),
          exact: true,
        }),
      ),
    );
  }
  return (
    <div className="flex flex-wrap gap-2">
      <button
        className={driveButton}
        aria-label={`Rename folder ${folder.name}`}
        onClick={() => setAction("rename")}
      >
        Rename
      </button>
      <button
        className={driveButton}
        aria-label={`Move folder ${folder.name}`}
        onClick={() => setAction("move")}
      >
        Move
      </button>
      <button
        className={driveButton}
        aria-label={`Download ${folder.name} as ZIP`}
        disabled={download.isPending}
        onClick={() => download.mutate()}
      >
        {download.isPending ? "Preparing ZIP…" : "Download ZIP"}
      </button>
      {download.isError ? <p role="alert">{download.error.message}</p> : null}
      {action === "rename" ? (
        <DriveNameDialog
          title="Rename folder"
          initialName={folder.name}
          submitLabel="Save name"
          onClose={() => setAction(null)}
          onSave={async (name) =>
            changed(await renameDriveFolder(folder.folderId, name))
          }
        />
      ) : null}
      {action === "move" ? (
        <DriveMoveDialog
          kind="folder"
          sourceFolderId={folder.parentFolderId}
          excludeFolderId={folder.folderId}
          onClose={() => setAction(null)}
          onMove={async (destination) =>
            changed(await moveDriveFolder(folder.folderId, destination))
          }
        />
      ) : null}
    </div>
  );
}
