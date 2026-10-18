# Real OCR implementation history

This document preserves the durable implementation timeline for the real OCR/invoice-extraction
slice. It condenses the former `.superpowers/sdd/progress.md` working ledger; temporary worktree
paths, recovery snapshots, backup archives, and resumption instructions are intentionally omitted.

## 2026-07-12 — Core slice integration

### API extraction and persistence

- Added the V7/V8 structured OCR and invoice-extraction persistence model, production workflow,
  workspace-scoped repositories, heuristic extraction, metadata-only side effects, and the
  authenticated OCR detail surface.
- Repaired test cleanup for the V7 foreign-key graph and ensured `FileUploaded` fixtures create the
  required Drive parent.
- Integrated source commit: `c3f31c8` (`feat(api): add real OCR invoice extraction persistence`).

### Worker OCR

- Added the local Tesseract/PDF processing path, encrypted Drive source reading, structured OCR
  persistence, bounded process execution, stale-claim fencing, retry/DLQ preservation, and
  temporary-file cleanup.
- Integrated source commit: `cd09983` (`feat(worker): add real OCR extraction`).

### API detail contract

- Reconciled the Media/OCR API with the approved nested `ocrResult` and `extraction` contract
  while keeping summary responses metadata-only.
- Integrated source commit: `6851114` (`fix(api): align OCR detail contract`).

### Media/OCR web UI

- Added the structured OCR/extraction inspector, review-required states, confidence/provenance
  rendering, responsive desktop/mobile behavior, and removed the runtime demo path.
- Integrated source commit: `17ba962` (`feat(web): add real OCR extraction review`).

## 2026-07-13 — Runtime and integrated verification

### Real local OCR runtime

- Worker image installs Tesseract and English language data and runs non-root.
- Compose and Kubernetes default to the local `tesseract` provider with bounded temporary storage
  and OCR resource limits.
- Added synthetic real-OCR smoke coverage.
- Integrated source commit: `28d5de3` (`chore(ocr): configure real Tesseract runtime`).
- Historical runtime evidence:
  [task-runtime-ocr-report.md](task-runtime-ocr-report.md).

### Integrated verification

The first complete integrated verification covered worker/API/web tests, formatting, production
builds, Compose, the worker image, Kubernetes validation, real Tesseract smoke, dependency checks,
privacy, and idempotency. See
[real-ocr-integration-verification.md](real-ocr-integration-verification.md).

### Final review and remediation

The initial final review identified the bounded-execution, PDF, extraction, list-query, polling,
runtime-tooling, accessibility, and verification issues later addressed by the approved amendment.
See [real-ocr-final-review.md](real-ocr-final-review.md) and the approved
[remediation amendment](../specs/2026-07-13-real-ocr-final-review-amendment.md).

The remediation was integrated in focused steps:

1. **PDF isolation and claim budget** — isolated PDFBox in the worker-local helper, added bounded
   helper execution and document deadlines, improved native-text selection/grouping, and aligned
   runtime limits. Source commit: `7b9d73a`.
2. **Worker bounds, provenance, and cleanup** — added image pre-decode bounds, word caps, real
   Tesseract version provenance, cleanup observability, and correct AES-GCM ciphertext allowance.
   Source commit: `8431c1b`.
3. **API extraction safety and list metadata** — made conflicting labelled values review-required,
   stopped bare-dollar currency fabrication, replaced content-heavy list reads with metadata
   projections, and added safe extraction audit metadata. Source commit: `f9c3c8f`. Detailed
   review: [task-final-review-api-report.md](task-final-review-api-report.md).
4. **Bounded Media/OCR polling and accessible feedback** — added bounded discovery/job/extraction
   polling plus status/alert live regions and recoverable refresh behavior. Source commit:
   `ad4f257`. Detailed review:
   [task-final-review-web-report.md](task-final-review-web-report.md).
5. **Source-file `file:view` authorization** — enforced the current private-file owner decision
   before Drive/OCR protected reads, preserving non-enumerating denial behavior. Source commit:
   `109291d`. Detailed review:
   [task-final-review-file-view-report.md](task-final-review-file-view-report.md).
6. **Runtime tooling and product documentation** — retired obsolete fake-runtime demo tooling,
   aligned product lifecycle documentation, and recorded evidence limitations truthfully. Source
   commit: `c333ec9`. Detailed review:
   [task-final-review-tooling-docs-report.md](task-final-review-tooling-docs-report.md).

## Later production-equivalent verification

PostgreSQL/Testcontainers proof, POSIX smoke evidence, differential frontend-audit analysis,
scanner evidence, and the later source-file authorization/sharing work are consolidated in
[real-ocr-remediation-verification.md](real-ocr-remediation-verification.md).

The approved specs and plans remain authoritative for requirements; this document is an
implementation history, not a replacement specification.
