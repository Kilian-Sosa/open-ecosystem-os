# MVP Core — permission-aware search design

**Status:** Proposed written design for user review
**Milestone:** MVP Core
**Parent:** [MVP milestones](2026-10-18-mvp-milestones-design.md)

## User outcome

Search private Drive files by filename immediately after successful upload,
by raw OCR text after OCR succeeds, and by normalized structured invoice
values only after a user has reviewed and confirmed them. Search links must
still work after files or their parent folders are renamed or moved.

## Verified baseline

- `GET /api/search` and `SearchService` exist. They combine Meilisearch
  with a PostgreSQL fallback and filter results using source-file
  `file:view` authorization.
- The index worker consumes `IndexingRequested` with retries and stores
  `SearchDocument` state.
- The production `WorkflowRunner` creates `invoice_extraction` search
  documents with field content before user confirmation.
- Current known production search-document creation is tied to the
  invoice-extraction workflow; independent filename and raw OCR source
  indexing are **not yet evidenced**. Do not mark them complete based on
  placeholder search examples.

## Indexable source classes

| Source               | Becomes searchable                        | Content exposed                                   |
| -------------------- | ----------------------------------------- | ------------------------------------------------- |
| `drive_file`         | After successful authorized upload        | Filename and safe file metadata                   |
| `ocr_result`         | After successful OCR persistence          | Raw recognized text associated with original file |
| `invoice_extraction` | **Only after explicit user confirmation** | Latest confirmed normalized invoice fields        |

The three source classes are independent projections with a stable source
file ID for permission checks and deletion. A document can have a filename
result without OCR, and raw OCR text without any invoice extraction.
Do not conflate a workflow's `completed` status with human confirmation.

## Required behavior

1. Use an eventually consistent outbox/index pipeline with transactional
   local source projection, so filename search works after the upload has
   succeeded even if Meilisearch is temporarily unavailable. OCR follows
   after job completion; structured fields follow after confirmation.
2. Search the existing workspace only. At read time and for both Meilisearch
   and PostgreSQL fallback, reauthorize each result against the underlying
   file's `file:view` permission. Do not expose a filename, snippet or count
   for unauthorized content.
3. Results identify file, source kind and relevant context, link to the
   authorized Drive/preview, OCR detail or confirmed-invoice view, and
   handle no results/loading/degraded-index errors honestly.
4. The indexing worker must not use extracted unconfirmed structured fields
   to construct snippets or metadata. Field values remain in protected
   data/search storage, never general audit/event/log payloads.
5. Moving a file or ancestor folder to Trash, permanent deletion, expiry
   or permission revocation immediately suppresses all associated results
   at the API even if an external search index is stale. Queue idempotent
   removal from local and external projections; delayed indexing must not
   resurrect trashed, expired, purged or superseded content.
6. File rename updates its filename projection; moving or renaming a folder
   changes navigation/breadcrumbs without changing document identities or
   exposing former paths. Moving an entire folder to Trash immediately
   suppresses all descendant search results, including raw OCR and
   confirmed invoices. Restoring the folder reindexes authorized filenames,
   retained OCR and only **confirmed** invoice values.
7. Index failures/retries do not prevent original Drive operations from
   succeeding; expose appropriate eventual-consistency state rather than
   claiming immediate remote-index publication.

## Data and event contracts

Reuse `search_documents`, `IndexingRequested`, the worker indexer and
existing authorization service. Add indexing/deletion handling for
`drive_file` and `ocr_result`. Gate `invoice_extraction` document
creation or release by persisted confirmation state. Preserve source
file lineage and per-source idempotency keys. Any new delete/reindex event
has metadata-only payload, transactional outbox and a consumer test.
The [Trash design](2026-10-18-mvp-core-drive-trash-design.md) defines the
30-day retained but non-searchable state and permanent purge behavior.

## Acceptance criteria

- A newly uploaded text or image filename is searchable without any invoice
  draft or OCR completion.
- An OCR term returns the authorized file after job completion, whether or
  not the invoice is reviewed.
- A candidate invoice number is **not** indexed as a structured field before
  confirmation; after correction/confirmation, only the corrected value is.
- Search works through the authorized PostgreSQL fallback when Meilisearch
  is unavailable, without bypassing the confirmation gate.
- A different actor without `file:view` cannot find or infer the file;
  trashed, expired, permanently deleted or revoked content cannot reappear
  through stale search or retry.
- Duplicate index messages do not create duplicate results or resurrect
  an obsolete confirmed revision.
- Renaming a file changes its searchable filename without orphaning OCR and
  invoice results; moving a parent folder preserves all descendant search
  links; trashing it hides all descendant results immediately. Restoring
  within 30 days reconstructs only allowed search projections; expiry or
  permanent deletion never reindexes the deleted subtree.

## Non-goals

No semantic/vector search, embeddings, AI Q&A, global cross-workspace
discovery, page/Knowledge indexing, or advanced query-language builder
is necessary for Core.
