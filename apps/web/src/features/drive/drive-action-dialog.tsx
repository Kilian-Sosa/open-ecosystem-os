"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";
import { useMutation } from "@tanstack/react-query";
import { useDriveFolderContents } from "./use-drive-files";

export const driveButton =
  "min-h-10 rounded-card border border-border-strong bg-surface px-3 py-2 text-sm font-medium text-text-primary hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-50";

export function DriveActionDialog({
  title,
  onClose,
  children,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const trigger = document.activeElement;
    const dialog = ref.current;
    dialog?.showModal();
    return () => {
      dialog?.close();
      if (trigger instanceof HTMLElement && trigger.isConnected)
        trigger.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      aria-label={title}
      onKeyDown={(event) => event.stopPropagation()}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
      className="fixed inset-x-0 bottom-0 top-auto m-0 max-h-[85dvh] w-full overflow-auto rounded-t-card border border-border bg-surface p-5 text-text-primary shadow-card backdrop:bg-text-primary/30 sm:inset-0 sm:m-auto sm:w-[min(32rem,90vw)] sm:rounded-card"
    >
      <div className="mb-4 flex items-center justify-between gap-3">
        <h2 className="font-semibold">{title}</h2>
        <button className={driveButton} type="button" onClick={onClose}>
          Cancel
        </button>
      </div>
      {children}
    </dialog>
  );
}

export function DriveNameDialog({
  title,
  initialName = "",
  submitLabel,
  onSave,
  onClose,
}: {
  title: string;
  initialName?: string;
  submitLabel: string;
  onSave: (name: string) => Promise<unknown>;
  onClose: () => void;
}) {
  const [name, setName] = useState(initialName);
  const mutation = useMutation({ mutationFn: onSave, onSuccess: onClose });
  return (
    <DriveActionDialog title={title} onClose={onClose}>
      <form
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          mutation.mutate(name.trim());
        }}
      >
        <label className="block text-sm">
          Name
          <input
            autoFocus
            required
            maxLength={255}
            className="mt-2 min-h-10 w-full rounded-card border border-border-strong bg-surface px-3 text-text-primary focus-visible:outline-2 focus-visible:outline-primary"
            value={name}
            onChange={(event) => setName(event.target.value)}
          />
        </label>
        {mutation.isError ? <p role="alert">{mutation.error.message}</p> : null}
        <button
          className={driveButton}
          disabled={!name.trim() || mutation.isPending}
        >
          {mutation.isPending ? "Saving…" : submitLabel}
        </button>
      </form>
    </DriveActionDialog>
  );
}

export function DriveMoveDialog({
  kind,
  excludeFolderId,
  sourceFolderId,
  onMove,
  onClose,
}: {
  kind: "file" | "folder";
  excludeFolderId?: string;
  sourceFolderId: string | null;
  onMove: (id: string | null) => Promise<unknown>;
  onClose: () => void;
}) {
  const [path, setPath] = useState<{ id: string; name: string }[]>([]);
  const destination = path.at(-1)?.id ?? null;
  const contents = useDriveFolderContents(destination);
  const mutation = useMutation({ mutationFn: onMove, onSuccess: onClose });
  return (
    <DriveActionDialog title={`Move ${kind}`} onClose={onClose}>
      <nav
        aria-label="Destination folders"
        className="mb-4 flex flex-wrap gap-2"
      >
        <button
          className={driveButton}
          type="button"
          onClick={() => setPath([])}
        >
          Drive
        </button>
        {path.map((folder, index) => (
          <button
            key={folder.id}
            className={driveButton}
            type="button"
            onClick={() => setPath(path.slice(0, index + 1))}
          >
            {folder.name}
          </button>
        ))}
      </nav>
      <p className="mb-3 text-sm text-text-secondary">
        Destination: {path.at(-1)?.name ?? "Drive"}
      </p>
      {contents.isPending ? (
        <p role="status">Loading destinations…</p>
      ) : contents.isError ? (
        <div role="alert">
          Destination unavailable.{" "}
          <button className={driveButton} onClick={() => contents.refetch()}>
            Retry
          </button>
        </div>
      ) : (
        <ul className="mb-4 space-y-2">
          {contents.data.folders
            .filter((folder) => folder.folderId !== excludeFolderId)
            .map((folder) => (
              <li key={folder.folderId}>
                <button
                  type="button"
                  className={driveButton}
                  aria-label={`Open destination ${folder.name}`}
                  onClick={() =>
                    setPath([
                      ...path,
                      { id: folder.folderId, name: folder.name },
                    ])
                  }
                >
                  {folder.name}
                </button>
              </li>
            ))}
        </ul>
      )}
      {mutation.isError ? <p role="alert">{mutation.error.message}</p> : null}
      <button
        type="button"
        className={driveButton}
        disabled={
          contents.isPending ||
          contents.isError ||
          destination === sourceFolderId ||
          mutation.isPending
        }
        onClick={() => mutation.mutate(destination)}
      >
        {mutation.isPending ? "Moving…" : "Move here"}
      </button>
    </DriveActionDialog>
  );
}
