# MVP Core — Drive Trash, recovery and retention design

**Status:** Proposed written design for user review (2026-10-18)
**Milestone:** MVP Core
**Parent:** [MVP milestones](2026-10-18-mvp-milestones-design.md)
**Depends on:** [Drive lifecycle and folders](2026-10-18-mvp-core-drive-design.md)

## User outcome and agreed product policy

Deleting a Drive file or folder **moves it to Trash**, where it can be
restored for **30 days**, including any nested contents of a deleted folder.
From Trash, the user may select **Delete permanently** without waiting
for expiry. After the 30-day retention period, the system automatically
permanently removes expired items. This is part of **MVP Core**, not
MVP Complete.

This supersedes the earlier Drive specification's immediate, irreversible
deletion and its exclusion of a trash/undo system.

## Existing implementation

Neither user-facing file deletion nor a folder model or Trash API/UI exists
in the 2026-10-18 baseline. The existing storage-layer deletion method
handles failed upload cleanup, not user-selected Trash or permanent removal.
Do not infer implementation from this proposed spec.

## Normal deletion (move to Trash)

1. A user with appropriate delete permission selects **Move to Trash** on
   one file or folder. If deleting a folder, the UI explains that its entire
   active nested subtree will be hidden. Moving to Trash is not a permanent
   deletion and does not delete encrypted source bytes.
2. The API atomically changes the selected item to a logically trashed state,
   recording `trashed_at`, `purge_after = trashed_at + 30 days`,
   the actor and the original parent folder. A folder's descendants are
   logically trashed as one deletion group, retaining the tree structure.
   Already-trashed independent items keep their own deletion dates.
3. Trashed items disappear **immediately** from regular folder listings,
   file previews/downloads, ZIP exports, built-in actions, OCR/invoice views
   and all search results. The source objects and existing OCR/invoice data
   are retained privately for a possible restore, not indexed as active
   content. Delayed worker or indexing events must not re-expose them.
4. The Trash view shows the trashed top-level files/folders with their safe
   display names, original location when still known, deletion date and
   scheduled permanent-deletion date. It offers Restore and Delete permanently.
   A trashed folder restores or permanently deletes its entire associated
   subtree, not an ambiguous partial folder.

## Restore

1. Restore is allowed only before `purge_after`, with current workspace,
   item and destination permissions rechecked. Restoring an unexpired folder
   restores its active-at-deletion subtree, preserving folder/file IDs,
   original file bytes, OCR results, invoice review state and provenance.
2. Prefer the original parent folder if it still exists and is active.
   If it does not, require an explicit accessible destination (root is
   available when authorized), rather than fabricating a parent silently.
3. If the destination already has a conflicting name, reject with a
   helpful conflict response; user can select another destination or
   rename the destination item. Do not overwrite an existing file/folder.
4. Search projections are reconstructed **after successful restoration**:
   current filenames, retained raw OCR text and only previously
   **confirmed** invoice fields become searchable again. Draft invoice
   fields remain excluded. Idempotent retries cannot create duplicates.
5. A folder restore must not accidentally resurrect files that had already
   been independently trashed before their parent was moved to Trash.

## Expiry and explicit permanent deletion

- Each deletion group expires exactly **30 × 24 hours** after the recorded
  UTC `trashed_at` timestamp; restore and content access are rejected from
  expiry onward even if a cleanup worker has not yet run.
- A durable scheduled cleanup repeatedly claims expired groups and
  permanently removes their object bytes, metadata and associated derived
  OCR/invoice/search data. Failures retry safely and surface as operational
  errors without extending user-visible retention or reactivating content.
- **Delete permanently** in Trash is available immediately after trashing,
  with an explicit irreversible-action confirmation. On success it makes
  the selected item/subtree immediately non-restorable and inaccessible,
  independently of the 30-day deadline; object/derived-data removal is
  started immediately and retried until physical cleanup completes. The
  UI must not claim physical erasure until the cleanup is confirmed.
- Permanent-deletion requests must be idempotent; duplicate requests and
  cleanup retries cannot affect unrelated objects. It must not be possible
  to restore a group after its purge was claimed.
- The 30-day Trash guarantee concerns live workspace storage, **not**
  independent administrative backups, whose policies belong to the
  MVP Complete backup/restore specification.

## Technical and security design

- Add additive, workspace-scoped lifecycle metadata for files/folders and a
  stable deletion-group record identifying top-level trashed items and their
  descendants, original placement, timestamps and purge progress.
  Preserve encrypted names and existing stable IDs/object keys. Use a
  forward-only migration; legacy files start as active.
- Prefer a **transactional logical tombstone** over physical moves of MinIO
  objects. File access, search authorization and workflow source reading
  must reject a file if it **or any ancestor folder** is trashed, expired,
  or permanently deleted. A repeated event cannot recreate an active entry.
- Apply fine-grained delete/restore/purge permissions and workspace isolation
  to each affected subtree. Never let guessed file/folder IDs, stale links,
  search indexes or the Trash view reveal other users' content.
- Retain document contents only within the encrypted private object store.
  Audit and outbox records contain IDs, action, status and timestamps—never
  plaintext filenames, OCR text or invoice values. Keep cleanup operations
  bounded and observable with metadata-only diagnostics.
- Specify clean transaction boundaries for the database state transition,
  object cleanup and search cleanup; physical storage and Meilisearch are
  not part of a single ACID transaction. Immediate **logical invisibility**
  must be enforced centrally even when cleanup is retrying.

## UI and acceptance criteria

- Trash is reachable from Drive and supports restoring or permanently
  deleting a file or folder; clear dates and permanent-deletion warnings
  are visible on desktop and mobile.
- Create `Projects/Invoices/2026` with files, move `Invoices` to Trash:
  it disappears from normal Drive, raw OCR search, confirmed-invoice search
  and previews, but its nested contents remain visible as a Trash item.
- Restore within 30 days and verify original hierarchy, file bytes,
  stable IDs, OCR data and **confirmed-only** invoice indexing are retained.
- Delete a source file, run an in-flight OCR/index worker, and confirm no
  stale result reappears; restore and reindex correctly afterward.
- A deleted parent folder or renamed/conflicting destination does not
  silently overwrite items; user can select a valid restore destination.
- At `trashed_at + 30 days`, a restore is rejected even if cleanup is
  pending; simulated scheduled cleanup eventually removes encrypted blobs
  and all derived records, including descendants, without collateral loss.
- Immediately use Delete permanently on a recently trashed folder: its
  descendants cannot be recovered; deletion cleanup starts without waiting
  30 days, retries safely after transient object-store failure and
  reports the real cleanup outcome.
- Other-workspace or unauthorized actors cannot list Trash, restore items,
  permanently delete items or access files through old URLs or ZIP exports.

## Non-goals

No customizable retention period, infinite Trash, recycle-bin version
history, restoration of previously overwritten data, multi-user Trash
sharing, or guarantee to erase administrator-managed external backups.
