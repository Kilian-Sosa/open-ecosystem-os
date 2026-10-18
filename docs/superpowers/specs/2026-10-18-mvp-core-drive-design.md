# MVP Core — Drive lifecycle design

**Status:** Proposed written design for user review
**Milestone:** MVP Core
**Parent:** [MVP milestones](2026-10-18-mvp-milestones-design.md)

## User outcome

A private workspace user can upload, list, inspect, preview, download and
delete documents. Files are the durable inputs and outputs of every Core
workflow. Drive must work independently of OCR or invoice extraction success.

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

## Required Core behavior

1. **Upload:** validate supported formats, per-file size and MIME/signature
   consistency; store ciphertext and private metadata; return stable file ID;
   failures clean up orphaned uploads; no unauthorized access.
2. **List and inspect:** show filename, type, size, creation time, processing
   states and available actions for current authorized files; meaningful
   empty/loading/error states, including responsive inspection.
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
   visible in lists/previews/downloads with input-file provenance. Original
   images remain unchanged.

## Interfaces and invariants

Reuse existing `/api/drive/files` metadata/upload routes. Add an
authorization-protected original-content download and preview surface and a
delete operation under the same resource boundary. Final HTTP method naming,
streaming and error response shape belong in the **Drive implementation
plan**, after verifying the existing API conventions and security test
fixtures. Public API consumers must never receive S3 secrets or ciphertext
metadata required to decrypt objects.

Every action uses an actor/workspace-scoped resource authorization decision.
Generated files must have traceable file IDs. File deletes are idempotent
and remain secure against job completion/indexing races.

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

## Non-goals

Folders, drag-and-drop folder hierarchies, public share links, full version
history, deduplication economics, compression optimization, office-document
rendering and multiuser sharing UX are not required to finish Core.
