# Real OCR and invoice extraction implementation handoff

Read [the approved design](2026-07-10-real-ocr-invoice-extraction-design.md) before starting implementation.

## Outcome

Ship the MVP OCR/invoice extraction slice with Tesseract as the initial self-hosted production provider, structured persisted OCR output, heuristic persisted invoice fields, review-required safety states, and a Media/OCR UI that shows the result.

## Hard constraints

- Never keep hard-coded invoice results in `WorkflowRunner` or any production runtime path.
- Do not OCR native PDF text pages with Tesseract; use per-page text-layer extraction first and Tesseract only for scanned/image-only pages.
- Preserve Tesseract TSV word, confidence, grouping, and bounding-box data through `InvoiceExtractionPort`.
- Process one page at a time; cap concurrency, page count, timeout, and temporary-resource use.
- Keep mock OCR and fake invoices test-only. Production/Compose must start with Tesseract and reject a mock provider.
- Do not emit or log OCR text, TSV, parsed values, or document content. Events stay metadata-only.
- Missing or uncertain data must create warnings and `review_required`; do not fabricate values.
- Preserve idempotency for worker retries, duplicate event delivery, workflow execution, extraction persistence, and downstream search/notification/audit side effects.

## Required review questions

1. Which current API/worker classes need refactoring versus replacement so the worker can access/decrypt source bytes safely?
2. Which Flyway constraints and indexes make result persistence safely idempotent and workspace-scoped?
3. How will the API detail response enforce source-file access before exposing OCR/extraction results?
4. Which structured fields should MVP search index without copying raw OCR content?
5. Which deployment manifests in addition to Docker Compose must receive the Tesseract image/runtime limits?

## Definition of done

Use the acceptance criteria and verification requirements in the design document as the release checklist. A result is only complete when a labelled fake/scanned fixture traverses the real containerized Tesseract path, persists structured OCR words and a non-fabricated invoice extraction, displays it in Media/OCR, and all relevant tests/checks pass.
