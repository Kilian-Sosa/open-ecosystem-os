# MVP Core — OCR design

**Status:** Proposed written design for user review
**Milestone:** MVP Core
**Parent:** [MVP milestones](2026-10-18-mvp-milestones-design.md)
**Predecessor:** [Real OCR design](2026-07-10-real-ocr-invoice-extraction-design.md)

## User outcome

From Drive, process an authorized PDF, PNG or JPEG and inspect its extracted
text and processing state without automatically committing its contents as
invoice data. OCR is a reusable document capability; invoice extraction is
an optional downstream action.

## Verified baseline

- `FileUploadedOcrJobService` automatically creates jobs on eligible
  `FileUploaded` events; type eligibility is PDF, PNG and JPEG.
- The worker has a `TesseractOcrProvider`, encrypted-source handling and
  structured OCR persistence. The prior approved real-OCR design establishes
  native PDF text-layer-first and scanned-page fallback behavior.
- `GET /api/media/ocr-jobs` and `GET /api/media/ocr-jobs/{jobId}`
  expose job state and structured results; `MediaScreen` displays the
  lifecycle, provenance and extraction warnings.
- The existing seeded `OcrCompleted` workflow triggers invoice extraction
  for eligible uploads. OCR-only versus invoice-extraction selection is **not**
  currently implemented.

## Required Core behavior

1. Provide an explicit **Extract text** built-in action from Drive for an
   eligible file. The user can inspect OCR results without creating invoice
   drafts. Avoid duplicate work when an identical file/job has already been
   processed; expose reprocessing only when meaningful.
2. Retain local/offline Tesseract as the default. For PDFs, use meaningful
   native text on each page before rasterizing scanned/textless pages.
   Persist page order, text and available word provenance/confidence.
3. Process asynchronously and display queued/running/completed/failed
   states; show sanitized errors, retry or rerun affordances according to
   actual retryability and job status.
4. Maintain encrypted source access, bounded page/file/image/CPU/time limits,
   a private temporary-processing boundary and proper cleanup. Preserve
   originals and separate OCR from the structured invoice model.
5. On OCR completion, index raw text for authorized search **independently of
   invoice confirmation**. Don't place raw text in outbox events, logs,
   metrics, notification bodies or audit data.
6. Invoice extraction explicitly reuses a successful OCR result; it must not
   force users to rerun OCR or fabricate missing/ambiguous fields.
7. OCR results are retained privately while the source is in Trash but are
   not readable or searchable. Restore makes retained results available
   again without an unnecessary rerun; expiry or permanent deletion cleans
   them up. See the [Trash contract](2026-10-18-mvp-core-drive-trash-design.md).

## Existing-automation compatibility

The present automatic FileUploaded → OCR → invoice workflow must be
reconciled with Drive-first selectable actions. It must not create or index
an unreviewed structured invoice behind an OCR-only action. Reuse the
current event/outbox/idempotency system rather than a parallel queue.

## Acceptance criteria

- Text-native PDF skips OCR for qualifying pages; mixed PDF and image files
  produce ordered text with correct source provenance.
- OCR-only produces a readable result and searchable raw text without an
  invoice draft or field index.
- Empty/low-quality/unreadable pages produce honest text and warnings or
  sanitized failure, never fictional values.
- Duplicate messages/retries do not duplicate result rows; job limits and
  timeout failures reach a visible terminal/retry state.
- Workspace and `file:view` authorization applies to job detail/results.
- Moving the source to Trash makes results inaccessible and suppresses
  indexing even if a worker event arrives afterward. Restoring it brings
  back retained OCR results; permanent deletion/purge removes them.

## Non-goals

Cloud OCR, external AI, arbitrary OCR providers, advanced model experimentation
and non-PDF/PNG/JPEG OCR are outside Core. Previously discussed alternative
models remain experimental and must not silently replace Tesseract.
