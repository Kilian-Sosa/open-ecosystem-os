# MVP Core — invoice extraction and confirmation

**Status:** Proposed written design for user review
**Milestone:** MVP Core
**Parent:** [MVP milestones](2026-10-18-mvp-milestones-design.md)
**Reuses:** [OCR](2026-10-18-mvp-core-ocr-design.md),
[Search](2026-10-18-mvp-core-search-design.md)

## User outcome

From Drive, choose **Extract invoice data** on an authorized PDF, PNG or
JPEG. See extracted field values alongside the source, correct mistakes,
explicitly confirm the reviewed result and then find confirmed structured
fields through Search. Raw file text/search operates independently.

## Verified baseline and required change

`InvoiceExtractionPort` and the heuristic adapter persist structured fields,
warnings, confidence and OCR-word provenance. `OcrJobQueryService` exposes
that result to the existing Media/OCR inspector. The database has extraction
statuses `completed` and `review_required` but **no distinct user-reviewed
confirmation state or update/confirm endpoint**. The
`V8__invoice_automation_production_workflow.sql` seeded workflow calls
`request_search_indexing` after extraction regardless of human review.
This violates the newly approved Core rule and requires explicit remediation.

## Proposed lifecycle

1. **Requested:** the user launches the built-in action on an existing file.
   Reuse a completed OCR result or create/await OCR. OCR-only never forces
   invoice extraction.
2. **Draft:** local heuristic extraction creates an immutable OCR provenance
   source and a persistently editable **invoice draft**, including candidate
   fields, missing/conflicting warnings and processing version. The draft is
   private and **not indexable as structured invoice data**.
3. **Review:** display original/preview and detected values together. Allow
   editing the invoice number, supplier, total, currency, issue date and due
   date when present; clearly show omitted values and ambiguity. Preserve
   original extracted suggestions and provenance separately from edits.
4. **Confirm:** user explicitly submits the reviewed values. Server rechecks
   actor/workspace and source-file permissions, validates field types and
   relationships, and atomically marks the reviewed version **confirmed**,
   records who/when/which revision, and requests structured search indexing.
5. **Search:** only the confirmed normalized values of the latest accepted
   revision may be indexed. Drafts, suggested alternatives and unconfirmed
   edits must never be included. Raw OCR and filename results remain searchable.

The automatic workflow may record generic notification/audit metadata after
extraction, but must **not** call structured invoice indexing until
confirmation. Confirmation is a **separate user operation**, not an inference
from a high-confidence extractor result or a successful background job.

## Data and interface contracts

- Existing `invoice_extractions` and `invoice_extraction_fields` remain
  canonical starting points; use a forward-only migration for review state,
  confirmed revision, reviewer/timestamps and independently stored edits as
  required. Do not rewrite prior Flyway migrations.
- API must read a draft by authorized file/extraction ID, persist edited
  fields with optimistic concurrency or equivalent revision protection, and
  provide an explicit idempotent confirm operation. Exact request/response
  shapes are locked in the dedicated implementation plan against current
  controller conventions.
- Distinguish **OCR job completed**, **extraction draft available**,
  **review required**, and **user confirmed** in API/UI; existing extraction
  `completed` does **not** mean user approved it.
- Missing data must not be fabricated. A blank/unknown optional field can be
  explicitly acknowledged by the reviewer, but validation errors or
  contradictory total/currency/date data must be shown before confirmation.
  Confirmation cannot silently discard unresolved blocking validation errors.
- Never expose private invoice values in outbox payloads, notification text,
  logs, or audit records. Safe audit: extraction ID, file ID, revision,
  reviewer, action, timestamp and outcome.
- While a source or its ancestor folder is in Trash, do not expose its
  drafts, approved structured search fields or later asynchronous results.
  Retain review state privately for authorized restore within 30 days;
  permanent deletion/expiry removes it. Restoring must not auto-confirm
  draft fields. See the
  [Trash design](2026-10-18-mvp-core-drive-trash-design.md).

## Failure and concurrency invariants

- A failed OCR/extractor produces a failed operation, not an editable fake
  invoice.
- Duplicate extraction triggers or confirm retries do not duplicate drafts
  or search documents.
- Two concurrent review sessions may not silently overwrite each other or
  confirm an obsolete revision: reject a stale revision and require refresh.
- Search indexing retries must load **the confirmed snapshot**, not raw draft
  values. If a new confirmed revision is allowed later, replace/reindex its
  prior search projection without temporarily exposing newer unconfirmed
  edits.
- A trashed/expired/permanently deleted file cannot be confirmed or indexed
  by a late job; restoring the file does not imply confirmation.

## Acceptance criteria

- Extract a real invoice with fields/warnings and original preview; edit an
  incorrect value; confirm; search finds the **corrected** value, not the
  superseded suggestion.
- Before confirmation, raw OCR and filename search work but searching by
  candidate structured values does not return an invoice-fields result.
- A clean, high-confidence extraction **still requires explicit confirmation**.
- Missing/invalid fields are visible; reviewer corrections persist across
  refresh; validation errors block invalid confirmation.
- Unauthorized reads/edits/confirms, stale revisions, duplicate confirms and
  deletion races cannot index private or unconfirmed values.

## Non-goals

No bank integrations, automatic accounting export, AI/cloud invoice parser,
vendor templates, automatic line-item classification, tax filing or
human-independent approval. A future generalized document-extraction review
framework must not delay this narrow invoice-specific Core workflow.
