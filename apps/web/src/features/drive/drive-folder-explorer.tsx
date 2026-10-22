"use client";

import { useState } from "react";
import { Folder } from "lucide-react";
import { useQueryClient } from "@tanstack/react-query";
import { SectionCard } from "@/components/ui";
import {
  createDriveFolder,
  type DriveFolder,
  type DriveTrashMutation,
} from "@/lib/drive-api";
import { driveFolderQueryKey } from "./use-drive-files";
import { DriveFolderActions } from "./drive-folder-actions";
import { DriveNameDialog, driveButton } from "./drive-action-dialog";

export function DriveFolderExplorer({
  path,
  folders,
  onNavigate,
  available,
  onTrashed,
}: {
  path: DriveFolder[];
  folders: DriveFolder[];
  onNavigate: (path: DriveFolder[]) => void;
  available: boolean;
  onTrashed?: (group: DriveTrashMutation) => void;
}) {
  const [creating, setCreating] = useState(false);
  const client = useQueryClient();
  const currentId = path.at(-1)?.folderId ?? null;
  return (
    <section className="space-y-4" aria-label="Folder explorer">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <nav
          aria-label="Drive folders"
          className="flex flex-wrap items-center gap-2"
        >
          <button
            className={driveButton}
            aria-current={path.length === 0 ? "page" : undefined}
            onClick={() => onNavigate([])}
          >
            Drive
          </button>
          {path.map((folder, index) => (
            <button
              key={folder.folderId}
              className={driveButton}
              aria-current={index === path.length - 1 ? "page" : undefined}
              onClick={() => onNavigate(path.slice(0, index + 1))}
            >
              {folder.name}
            </button>
          ))}
        </nav>
        <button
          className={driveButton}
          disabled={!available}
          onClick={() => setCreating(true)}
        >
          New folder
        </button>
      </div>
      {folders.length > 0 ? (
        <SectionCard
          title="Folders"
          description="Open a folder to browse its contents."
        >
          <ul className="space-y-3">
            {folders.map((folder) => (
              <li
                key={folder.folderId}
                className="flex flex-col justify-between gap-3 rounded-card border border-border p-4 sm:flex-row sm:items-center"
              >
                <button
                  className="flex min-h-10 min-w-0 items-center gap-3 text-left font-medium focus-visible:outline-2 focus-visible:outline-primary"
                  aria-label={`Open folder ${folder.name}`}
                  onClick={() => onNavigate([...path, folder])}
                >
                  <Folder
                    className="h-5 w-5 shrink-0 text-primary"
                    aria-hidden="true"
                  />
                  <span className="break-all">{folder.name}</span>
                </button>
                <DriveFolderActions folder={folder} onTrashed={onTrashed} />
              </li>
            ))}
          </ul>
        </SectionCard>
      ) : null}
      {creating ? (
        <DriveNameDialog
          title="New folder"
          submitLabel="Create folder"
          onClose={() => setCreating(false)}
          onSave={async (name) => {
            await createDriveFolder(name, currentId);
            await client.invalidateQueries({
              queryKey: driveFolderQueryKey(currentId),
              exact: true,
            });
          }}
        />
      ) : null}
    </section>
  );
}
