# MVP Core — Drive lifecycle design

**Status:** Revised written design for user review (folder organization added 2026-10-18)
**Milestone:** MVP Core
**Parent:** [MVP milestones](2026-10-18-mvp-milestones-design.md)

## User outcome

A private workspace user can organize documents into nested folders, upload,
list, rename, move, inspect, preview, download and delete files or folders,
and download a whole folder tree as a ZIP archive. Files are the durable
inputs and outputs of every Core workflow. Drive must work independently of
OCR or invoice extraction success.

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
5. **Delete:** explicit confirmation; enforce delete permission; make the
   source unavailable to future reads/search immediately after successful
   deletion. Safely remove or invalidate stored object, metadata, derived OCR,
   invoice drafts/confirmed data, generated provenance associations, active
   jobs and search projections. Background cleanup may complete later but
   must not expose deleted content. Record safe audit metadata.
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
10. **Delete folders:** deleting a non-empty folder requires explicit
    confirmation that all descendants will be removed. Immediately make the
    entire subtree unavailable, and safely clean up objects, derived OCR,
    invoices, index projections and active jobs without late-event
    resurrection. Deleting an empty folder is supported too.
11. **Download folders:** download any authorized folder as a ZIP archive,
    recursively preserving relative filenames, nested structure and empty
    subfolders. Apply configured limits on file count, total uncompressed
    bytes and processing time. Never expose raw storage URLs. Fail the
    request if any descendant is inaccessible rather than silently exporting
    a partial folder. Downloads do not create a persistent Drive file.

## Folder model and UI architecture

- Add a workspace-scoped `drive_folders` table with stable folder ID,
  encrypted name, nullable parent folder ID (root), creator, created/updated
  times, and lifecycle state. Add nullable `folder_id` on `drive_files`.
  Parent/child and file/folder links must not cross workspaces; use database
  constraints and authorization. Empty folders must be representable.
- Prefer an **ID-based adjacency tree** with a recursive query when a whole
  subtree is needed. Do not encode physical S3 paths or authorization as
  folder name/path strings: renames and moves should not rewrite objects.
- Persist encrypted folder names using existing encryption conventions.
  Enforce normalized sibling-name uniqueness across folders and files in a
  parent, with safe concurrency handling despite randomized name encryption.
  Do not persist plaintext names or full paths in audit/event payloads.
- Authorize each folder operation in the source and destination context.
  In Core, folders are private organization containers; they do not
  introduce implicit public links or recursive sharing grants. File
  permissions still govern reads. Folder download and recursive deletion
  must check access to all affected descendants.
- Drive UI provides folder navigation (breadcrumb and folder list), create
  folder, rename, move-to-folder, delete with confirmation, and folder
  download. A move picker is sufficient for Core: a drag-and-drop folder
  tree is optional, not an independent acceptance criterion.
- The ZIP contains only safely normalized relative entry names; reject or
  escape traversal names, archive entry collisions and unbounded archives.
  Stream or spool ZIP creation in a controlled temporary workspace with
  guaranteed cleanup, never exposing plaintext at rest outside that boundary.

## Interfaces and invariants

Reuse existing `/api/drive/files` metadata/upload routes. Add an
authorization-protected original-content download and preview surface,
file rename/move/delete operations and a distinct `/api/drive/folders`
resource for folder create/list/rename/move/delete/ZIP export. A list
request is scoped to a folder (or root) rather than flattening the entire
workspace. Final HTTP method naming, streaming and error response shapes
belong in the **Drive implementation plan**, after verifying existing API
conventions and security fixtures. Public API consumers must never receive
S3 secrets or ciphertext metadata required to decrypt objects.

Every action uses an actor/workspace-scoped resource authorization decision.
Generated files must have traceable file IDs. File deletes are idempotent
and remain secure against job completion/indexing races. Recursive moves
and deletions must preserve consistency under concurrent folder changes;
bounded ZIP exports must never include items added after an authorization
snapshot without revalidating their permissions.

## Acceptance criteria

- Upload PDF/image/text; list it; reopen the page; preview eligible types;
  download and compare bytes with the original.
- Reject a corrupt/unsupported file or a file beyond configured limits;
  no orphaned object or leaked sensitive error detail.
- An actor lacking `file:view` cannot list detail, preview, download,
  infer filenames from search, or exploit a guessed ID.
- A user lacking delete permission cannot remove it; allowed deletion
  hides the file immediately and eventually reclaims storage and derivatives.
- Delete while OCR/indexing is queued or processing: no stale searchable
  content and no later recreation of the deleted resource.
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
- Delete a non-empty folder after an explicit recursive-delete warning:
  all descendants become inaccessible/search-invisible immediately and
  workers cannot resurrect them; deleting an empty folder also works.
- Older pre-folder files remain visible at root following migration.

## Non-goals

Folder drag-and-drop (as opposed to a move picker), public share links,
full version history, cross-workspace folder movement, deduplication
economics, compression optimization, office-document rendering, trash/
undo retention, and multiuser folder-sharing UX are not required to finish
Core.
