# MVP Core — Drive lifecycle design

**Status:** Approved 2026-10-18 (folders and 30-day Trash included)
**Milestone:** MVP Core
**Parent:** [MVP milestones](2026-10-18-mvp-milestones-design.md)
**Retention contract:** [Drive Trash design](2026-10-18-mvp-core-drive-trash-design.md)

## User outcome

A private workspace user can organize documents into nested folders, upload,
list, rename, move, inspect, preview and download files, move files/folders
to a recoverable Trash, and download a whole folder tree as a ZIP archive.
Trash permits restore for 30 days and immediate permanent deletion on
request. Files are the durable inputs and outputs of every Core workflow.
Drive must work independently of OCR or invoice extraction success.

## Verified baseline

- `DriveFileController` implements `GET /api/drive/files`,
  `GET /api/drive/files/{fileId}` (metadata), and
  `POST /api/drive/files` (multipart upload).
- `DriveUploadService` encrypts file bytes and filename before S3/MinIO
  storage; stores metadata and transactional `FileUploaded`/audit records.
- `DriveFileRepository` provides scoped metadata reads and visibility
  support; the web `DriveScreen` lists/uploads and inspects metadata.
- There is no public authenticated file-content download/preview/delete
  controller. The storage object's internal cleanup method is **not** a user
  deletion feature. Existing frontend state mocks are not proof of behavior.
- `V1__drive_upload_slice.sql` and `DriveFileMetadata` have no folder ID
  or parent relationship. No folder model, folder CRUD routes, file/folder
  rename/move routes, hierarchy UI, or folder ZIP download is implemented.
- There is no Trash lifecycle, restore/purge endpoint, retention scheduler
  or user-facing Trash view. Existing encrypted storage does not provide
  deletion recovery by itself.

## Required Core behavior

1. **Upload:** validate supported formats, per-file size and MIME/signature
   consistency; store ciphertext and private metadata; return stable file ID;
   failures clean up orphaned uploads; no unauthorized access.
2. **List, navigate and inspect:** show the current folder's child folders and
   files, breadcrumbs back to the root, filename, type, size, creation time,
   processing states and available actions. Persist nested folder structure,
   show empty folders and accessible empty/loading/error states, including
   responsive inspection.
3. **Download original:** permission-checked file stream, decrypting only
   within trusted service memory/controlled temporary storage; preserve bytes,
   MIME and safe filename; no plaintext public object URL or response caching.
4. **Preview:** support at least PDF, PNG, JPEG and plain text in a bounded
   authenticated viewer. Other supported uploads show metadata and download
   instead of an unsafe attempt to render. Do not execute active document
   content. Handle failure and inaccessible files gracefully.
5. **Move to Trash:** enforce delete permission; immediately hide the
   selected file from regular reads, listings, processing and search.
   Retain encrypted bytes, metadata and derivatives for restore during the
   next 30 days. Trash enables restoration or immediate irreversible deletion;
   expiration triggers automatic permanent cleanup. Keep safe audit metadata.
6. **Generated files:** PDF assembly output is a regular private Drive file,
   visible in lists/previews/downloads with input-file provenance. Save it
   in the explicitly selected destination folder, defaulting to the current
   folder. Original images remain unchanged.
7. **Create folders:** create a named folder at the Drive root or inside
   another folder, including an empty folder. The hierarchy and metadata
   survive restarts. All old files without a folder assignment live at root
   after the additive migration.
8. **Rename:** rename files and folders without replacing the file content,
   changing its stable ID, S3 key, encryption or OCR/invoice associations.
   Prevent invalid names and same-parent name conflicts; update dependent
   filename search projections without exposing old names.
9. **Move:** move a file or a whole folder tree to root or another authorized
   destination folder. Keep stable IDs and data lineage. Reject moves to the
   same node, a folder's own descendant, another workspace, unauthorized
   destinations or conflicting sibling names. Only metadata placement
   changes; encrypted blobs must not be copied solely to rename/move.
10. **Trash folders:** moving a non-empty folder to Trash requires an
    explicit confirmation that all its active descendants will be hidden
    together. Retain original tree structure and derived data for restoring
    the entire folder within 30 days. Restoring must preserve stable IDs,
    bytes and provenance; expiration or explicit permanent deletion removes
    the folder subtree irreversibly. Empty folders also support Trash.
11. **Download folders:** download any authorized folder as a ZIP archive,
    recursively preserving relative filenames, nested structure and empty
    subfolders. Apply configured limits on file count, total uncompressed
    bytes and processing time. Never expose raw storage URLs. Fail the
    request if any descendant is inaccessible rather than silently exporting
    a partial folder. Downloads do not create a persistent Drive file.

## Folder model and UI architecture

- Add a workspace-scoped `drive_folders` table with stable folder ID,
  encrypted name, nullable parent folder ID (root), creator, created/updated
  times, and lifecycle state including Trash. Add nullable `folder_id` on
  `drive_files`, with appropriate Trash state/parent snapshots.
  Parent/child and file/folder links must not cross workspaces; use database
  constraints and authorization. Empty folders must be representable.
- Prefer an **ID-based adjacency tree** with a recursive query when a whole
  subtree is needed. Do not encode physical S3 paths or authorization as
  folder name/path strings: renames and moves should not rewrite objects.
- Persist encrypted folder names using existing encryption conventions.
  Enforce normalized sibling-name uniqueness across folders and files in a
  parent's owner namespace, with safe concurrency handling despite randomized
  name encryption. Another owner's items must not influence conflict responses
  or require decrypting their private names, including at the workspace root.
  Do not persist plaintext names or full paths in audit/event payloads.
- Authorize each folder operation in the source and destination context.
  In Core, folders are private organization containers; they do not
  introduce implicit public links or recursive sharing grants. File
  permissions still govern reads. Folder download, subtree Trash,
  restore and permanent deletion must check access to affected descendants.
- Drive UI provides folder navigation (breadcrumb and folder list), create
  folder, rename, move-to-folder, **Move to Trash**, a separate Trash view
  with restore/permanent-delete actions, and folder download. A move picker
  is sufficient for Core: a drag-and-drop folder tree is optional, not an
  independent acceptance criterion.
- The ZIP contains only safely normalized relative entry names; reject or
  escape traversal names, archive entry collisions and unbounded archives.
  Valid Drive names with nonportable ZIP filename characters are reversibly
  escaped during export without changing stored names. Browser ZIP downloads
  use the native download manager through the same-origin Next.js
  `/downloads/drive/folders/{folderId}` route, streaming every server-permitted
  archive without collecting it into JavaScript memory or imposing a client
  byte cap. The fixed seeded identity adapter is trusted-private-Core only;
  it provides no public authentication. See the approved
  [streaming contract](2026-10-21-drive-browser-streaming-downloads-design.md).
  Stream or spool ZIP creation in a controlled temporary workspace with
  guaranteed cleanup, never exposing plaintext at rest outside that boundary.

## Interfaces and invariants

Reuse existing `/api/drive/files` metadata/upload routes. Add an
authorization-protected original-content download and preview surface,
file rename/move/Trash operations and a distinct `/api/drive/folders`
resource for folder create/list/rename/move/Trash/ZIP export, with separate
authorized Trash list/restore/permanent-delete operations. A list
request is scoped to a folder (or root) rather than flattening the entire
workspace. Final HTTP method naming, streaming and error response shapes
belong in the **Drive implementation plan**, after verifying existing API
conventions and security fixtures. Public API consumers must never receive
S3 secrets or ciphertext metadata required to decrypt objects.

Every action uses an actor/workspace-scoped resource authorization decision.
Generated files must have traceable file IDs. Moving to Trash, restoration
and permanent deletion must be idempotent and remain secure against job
completion/indexing races. Recursive moves and Trash operations must
preserve consistency under concurrent folder changes;
bounded ZIP exports must never include items added after an authorization
snapshot without revalidating their permissions.

## Acceptance criteria

- Upload PDF/image/text; list it; reopen the page; preview eligible types;
  download and compare bytes with the original.
- Reject a corrupt/unsupported file or a file beyond configured limits;
  no orphaned object or leaked sensitive error detail.
- An actor lacking `file:view` cannot list detail, preview, download,
  infer filenames from search, or exploit a guessed ID.
- A user lacking delete permission cannot move the item to Trash; an allowed
  trash action immediately hides it and retains recoverable bytes/metadata
  until expiry or permanent deletion.
- Moving to Trash while OCR/indexing is queued or processing must hide
  results and prevent later events from re-exposing the trashed resource.
- Existing sharing/visibility grants cannot bypass content authorization.
- Create `Projects/Invoices/2026`, upload files into `2026`, navigate
  through breadcrumbs, reload, and see exactly the same hierarchy.
- Rename a file and its parent folder, then move the entire `Invoices`
  subtree elsewhere: file IDs, previews, OCR results and invoice provenance
  remain valid; Search reflects current file names and opens the file.
- Move a file back to root and reject an attempt to move `Projects` into
  its own descendant, an inaccessible destination, or an existing sibling
  of the same normalized name.
- Download the `Projects` folder: ZIP contains authorized originals,
  nested relative paths and empty subfolders; entries have no `../`
  traversal and byte content matches source files.
- Attempt a ZIP download with an inaccessible descendant: entire request
  fails with no partial content leaked. Limits also reject oversized trees.
- Move a non-empty folder to Trash after an explicit recursive warning:
  descendants become inaccessible/search-invisible immediately; restoring
  within 30 days brings back the folder tree and authorized searchable data.
- Delete permanently from Trash after explicit confirmation: the subtree is
  non-restorable immediately, and deletion cleanup starts without waiting
  for the scheduled purge.
- After 30 days, expired Trash content is inaccessible and automatically
  purged; retrying cleanup cannot affect restored or unrelated items.
- Older pre-folder files remain visible at root following migration.

## Non-goals

Folder drag-and-drop (as opposed to a move picker), public share links,
full version history, cross-workspace folder movement, deduplication
economics, compression optimization, office-document rendering, custom
Trash retention settings, and multiuser folder-sharing UX are not required to finish
Core.
