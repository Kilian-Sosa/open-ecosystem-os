# MVP Core — ordered images to PDF

**Status:** Proposed written design for user review
**Milestone:** MVP Core
**Parent:** [MVP milestones](2026-10-18-mvp-milestones-design.md)

## User outcome

Select a set of images from Drive, control page order, generate one standard
PDF with one image per page, and find that PDF in Drive for preview/download.
This is a predefined built-in operation, **not a general PDF editor**.

## Verified baseline

Drive upload/encrypted object storage, jobs/events and Flows execution history
exist. However, the current supported workflow actions in
`WorkflowDefinitionValidator` are invoice extraction, notification, audit,
Knowledge placeholder and search indexing. There is no PDF-merge action,
PDF-assembly worker, or selectable image-to-PDF Drive UX.

## Core interaction

1. Choose **Create PDF from images** in Drive, select two or more authorized
   existing PNG/JPEG files (or add them through the normal upload flow).
2. Show an ordered list of selected images with thumbnails where available.
   The user can rearrange pages, remove selections and review the final order
   before starting; source files stay unmodified.
3. Start the predefined operation. Show queued/running/completed/failed state
   and allow returning later without losing the persisted execution record.
4. On success, create **one new encrypted private PDF in Drive** with a safe,
   meaningful filename, stable file ID, correct MIME, page count and ordered
   source-file lineage. Provide preview and original download.
5. Index the generated PDF filename when it is saved. The resulting PDF is
   eligible for ordinary PDF OCR, but conversion itself does not imply
   invoice extraction or approval.

## Processing and interface boundaries

- API validates actor/workspace permissions, input ownership, type, count,
  duplication and input ordering before queuing a durable execution.
- Worker accesses decrypted inputs only within a trusted processing boundary,
  creates pages at an appropriate image aspect ratio (no cropping by default),
  re-encrypts the finished PDF via the standard Drive path and cleans up
  temporary images/plaintext/output after success or failure.
- Keep the existing job/event/outbox/idempotency facilities. Add only the
  bounded action needed; do not build arbitrary user-defined workflows.
- Persist an operation/execution ID and deterministic relationship between
  that request and its generated file so retries cannot create multiple
  outputs. Duplicate input selection is rejected or explicitly deduplicated
  before generation; the user must see the final page list.
- Reject inaccessible, corrupt, unsupported or oversized inputs before
  publication. Enforce limits on number of pages, decoded pixels, final size,
  processing time and concurrent jobs using documented configuration.
- Never embed absolute object keys, credentials or original plaintext
  filenames in events/audit. Generated content inherits private visibility,
  not broader access than any input.

## Failure and deletion

Failures return a safe error with the operation ID, leave original images
intact and do not expose a partially assembled PDF. Retrying an operation
cannot silently duplicate generated files. If a source image is deleted
before assembly completes, the job must fail safely rather than read
unauthorized/stale bytes. If it is deleted **after** successful generation,
the independent generated PDF remains available; stored lineage refers to
historical source IDs without exposing inaccessible data.

## Acceptance criteria

- Three images, reordered as C/A/B, yield one readable three-page PDF in
  precisely C/A/B page order with sensible page orientation.
- Preview/download the PDF; it remains in Drive across an app restart.
- Unsupported/corrupt/missing/inaccessible image or empty selection gives a
  user-readable failure and no output artifact.
- Duplicate event delivery and retry do not create multiple PDF files.
- Concurrent removal of a selected source cannot bypass permissions or leave
  plaintext artifacts; already successful output survives later source deletion.
- Generated PDF is available by filename in Search, independently of invoice
  confirmation.

## Non-goals

No PDF-page manipulation, watermarking, annotations, signatures, permanent
redaction, optical enhancement, layouts with multiple images per page, or
arbitrary image formats beyond current supported JPEG/PNG handling.
