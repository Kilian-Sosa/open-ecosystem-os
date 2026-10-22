"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { AppShell } from "@/components/layout/app-shell";
import {
  EmptyState,
  ErrorState,
  LoadingState,
  PageHeader,
  PermissionDeniedState,
  SectionCard,
} from "@/components/ui";
import {
  DriveApiError,
  permanentlyDeleteDriveTrashItem,
  restoreDriveTrashItem,
  type DriveTrashResponse,
} from "@/lib/drive-api";
import {
  DriveActionDialog,
  DriveMoveDialog,
  driveButton,
} from "./drive-action-dialog";
import { useDriveTrash, useRefreshDrive } from "./use-drive-trash";

export function DriveTrashScreen() {
  const query = useDriveTrash();
  const denied =
    query.error instanceof DriveApiError &&
    [403, 404].includes(query.error.status);
  return (
    <AppShell activeHref="/app/drive">
      <div className="space-y-6">
        <PageHeader
          title="Trash"
          subtitle="Restore files and folder trees within 30 days. After their deadline, they are automatically deleted permanently."
          secondaryAction={
            <Link className={driveButton} href="/app/drive">
              Back to Drive
            </Link>
          }
        />
        {query.isPending ? (
          <LoadingState label="Loading Trash" />
        ) : denied ? (
          <PermissionDeniedState
            title="Permission denied"
            description="Trash is unavailable for this workspace."
          />
        ) : query.isError ? (
          <ErrorState
            title="Trash could not be loaded"
            description="Try again to check retained items."
            action={
              <button className={driveButton} onClick={() => query.refetch()}>
                Try again
              </button>
            }
          />
        ) : query.data.length === 0 ? (
          <EmptyState
            title="Trash is empty"
            description="Items moved to Trash will appear here for 30 days."
          />
        ) : (
          <ul className="space-y-4" aria-label="Trashed items">
            {query.data.map((item) => (
              <li key={item.groupId}>
                <TrashItem item={item} observedAt={query.dataUpdatedAt} />
              </li>
            ))}
          </ul>
        )}
      </div>
    </AppShell>
  );
}

function TrashItem({
  item,
  observedAt,
}: {
  item: DriveTrashResponse;
  observedAt: number;
}) {
  const [action, setAction] = useState<"restore" | "purge" | null>(null);
  const refresh = useRefreshDrive();
  const submitting = useRef(false);
  const restore = useMutation({
    mutationFn: (destination?: string | null) =>
      restoreDriveTrashItem(item.groupId, destination),
    onSuccess: refresh,
  });
  const purge = useMutation({
    mutationFn: () => permanentlyDeleteDriveTrashItem(item.groupId),
    onSuccess: async () => {
      setAction(null);
      await refresh();
    },
    onSettled: () => {
      submitting.current = false;
    },
  });
  const busy = restore.isPending || purge.isPending;
  const hoursRemaining = Math.max(
    0,
    Math.min(
      720,
      Math.ceil((Date.parse(item.purgeAfter) - observedAt) / 3_600_000),
    ),
  );
  return (
    <SectionCard
      title={item.name}
      description={`${item.rootItemType === "folder" ? "Folder tree" : "File"} · Original location: ${item.originalLocation ?? "Unavailable"}`}
    >
      <div className="space-y-4 break-words">
        <dl className="grid gap-3 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-text-secondary">Moved to Trash (UTC)</dt>
            <dd>
              <TrashDate value={item.trashedAt} />
            </dd>
          </div>
          <div>
            <dt className="text-text-secondary">
              Permanent deletion deadline (UTC)
            </dt>
            <dd>
              <TrashDate value={item.purgeAfter} />
            </dd>
          </div>
        </dl>
        {item.canRestore ? (
          <p className="text-sm text-text-secondary">
            Approximately {hoursRemaining} hours remaining, as of the last
            refresh. The server checks the exact deadline when restoring.
          </p>
        ) : null}
        {item.state === "PURGING" ? (
          <p role="status">
            Permanent deletion requested. Cleanup is still processing; this item
            cannot be restored.
          </p>
        ) : !item.canRestore ? (
          <p role="status">Retention expired. Waiting for permanent cleanup.</p>
        ) : null}
        {restore.isError ? (
          <p role="alert">
            Restore failed. Choose an accessible destination, check for name
            conflicts, or refresh to check the retention deadline.
          </p>
        ) : null}
        {purge.isError ? (
          <p role="alert">
            The deletion request could not be confirmed. Refresh Trash to check
            its status before trying again.
          </p>
        ) : null}
        <div className="flex flex-wrap gap-2">
          {item.canRestore ? (
            <>
              <button
                className={driveButton}
                disabled={busy}
                onClick={() => restore.mutate(undefined)}
              >
                Restore original location
              </button>
              <button
                className={driveButton}
                disabled={busy}
                onClick={() => setAction("restore")}
              >
                Choose restore destination
              </button>
            </>
          ) : null}
          {item.state === "TRASHED" ? (
            <button
              className={driveButton}
              disabled={busy}
              onClick={() => setAction("purge")}
            >
              Delete permanently
            </button>
          ) : null}
        </div>
        {action === "restore" ? (
          <DriveMoveDialog
            kind={item.rootItemType}
            title="Restore destination"
            submitLabel="Restore here"
            allowCurrentDestination
            sourceFolderId={null}
            excludeFolderId={
              item.rootItemType === "folder" ? item.rootItemId : undefined
            }
            onClose={() => setAction(null)}
            onMove={async (destination) => {
              await restore.mutateAsync(destination);
            }}
          />
        ) : null}
        {action === "purge" ? (
          <DriveActionDialog
            title="Delete permanently"
            onClose={() => setAction(null)}
          >
            <p className="mb-4 text-sm text-text-secondary">
              Permanently delete {item.name}
              {item.rootItemType === "folder"
                ? " and its associated nested contents"
                : ""}
              ? This cannot be undone. Restoration becomes unavailable
              immediately; physical cleanup may continue in the background.
            </p>
            {purge.isError ? (
              <p role="alert">
                The request could not be confirmed. Check Trash again.
              </p>
            ) : null}
            <button
              className={`${driveButton} text-danger`}
              disabled={busy}
              onClick={() => {
                if (submitting.current) return;
                submitting.current = true;
                purge.mutate();
              }}
            >
              Delete permanently
            </button>
          </DriveActionDialog>
        ) : null}
      </div>
    </SectionCard>
  );
}

function TrashDate({ value }: { value: string }) {
  const date = new Date(value);
  return (
    <time dateTime={value}>
      {Number.isNaN(date.getTime())
        ? "Unavailable"
        : date.toISOString().replace("T", " ").replace("Z", " UTC")}
    </time>
  );
}
