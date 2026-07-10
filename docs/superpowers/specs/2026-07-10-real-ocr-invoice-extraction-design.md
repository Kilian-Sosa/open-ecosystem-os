# Real OCR and invoice extraction design

**Status:** Approved design; implementation pending Sol Ultra review  
**Date:** 2026-07-10  
**Scope:** MVP invoice-automation vertical slice

## Goal

Replace the runtime fake invoice result with a self-hosted, production-capable OCR and invoice-extraction flow:

```txt
Drive upload
  -> FileUploaded
  -> OcrRequested
  -> worker reads and decrypts the file
  -> native PDF text extraction per page, or Tesseract fallback per scanned/image page
  -> persisted structured OCR result
  -> OcrCompleted
  -> workflow InvoiceExtractionPort
  -> persisted structured invoice extraction
  -> notification, audit, and search
```

The fixture-based flow remains a testing strategy only. It must not be a runtime production path.

## Confirmed requirements

- Tesseract is the first self-hosted production OCR provider.
- The worker must use a native PDF text-layer path before invoking Tesseract. For mixed PDFs, process each page independently and use Tesseract only for pages without meaningful native text.
- Preserve structured OCR output for every page. Tesseract-originated output must retain TSV words, confidence, page/block/paragraph/line identity, and bounding boxes. Native text-layer words may omit confidence and boxes when unavailable.
- `InvoiceExtractionPort` consumes the structured OCR result, not a flattened string. The same port processes fixture OCR and real OCR output.
- Initial invoice extraction is heuristic and label-based, augmented with spatial proximity, field validation, and arithmetic consistency checks. It is not a general invoice-understanding engine.
- Missing, conflicting, or low-confidence fields must generate warnings and a `review_required` result. The extractor must never fabricate a value.
- OCR output and structured invoice extraction must persist independently.
- A retry or duplicate delivery must not create duplicate OCR results, extraction results, workflow executions, outbox events, notifications, or search documents.
- The Media/OCR UI must display the provider, structured fields, confidence/provenance when present, and warnings/review state on desktop and mobile.
- The production profile must not start with a mock OCR provider or a fixed invoice result stub.
- Raw document content, raw OCR text, field values, and Tesseract diagnostics must not enter events, logs, notification bodies, or audit attributes.

## Non-goals

- General invoice understanding, line-item extraction, vendor-specific templates, ML training, or automatic posting to accounting systems.
- An external/cloud OCR provider, AI-based extraction, or user-configured OCR providers.
- OCR of content types outside the existing PDF, PNG, and JPEG support.
- New P2/P3 screens. The existing Media/OCR job inspector is the first result surface.
- Changes to the `OcrCompleted` event payload beyond its existing metadata-only contract.

## Architecture and boundaries

### Worker: source access and OCR

The worker becomes responsible for obtaining a transient, plaintext processing input from the encrypted Drive object. It must add a worker-local source-reader boundary that:

1. looks up only the source file metadata required for the job;
2. downloads the encrypted object from the private S3/MinIO bucket;
3. decrypts it in memory or a permission-restricted temporary directory;
4. deletes temporary plaintext input and rendered page images in `finally` blocks; and
5. never exposes a signed URL, filename, OCR content, or encryption key in logs/events.

This requires the worker to receive the same runtime storage and encryption configuration as the API, but no secret belongs in source control. The worker must use the existing S3 endpoint/credentials and Drive encryption key variables from its runtime configuration.

Introduce a structured worker result, for example `OcrDocumentResult` with `OcrPageResult` and `OcrWord`. Its stable information is:

- provider and provider version;
- page number and source kind (`pdf_text_layer` or `tesseract_tsv`);
- reconstructed page/document text;
- word text and reading order;
- nullable confidence; and
- nullable bounding box (`left`, `top`, `width`, `height`) plus TSV block/paragraph/line identifiers.

`OcrProvider` must return this structure. It must not discard TSV details before persistence. Tesseract TSV supports word confidence and bounding-box information; Tesseract does not read PDFs directly, so scanned PDF pages need image rendering before invocation. [Tesseract command-line output](https://tesseract-ocr.github.io/tessdoc/Command-Line-Usage.html) and [input-format documentation](https://tesseract-ocr.github.io/tessdoc/InputFormats.html) describe those constraints.

### PDF and image processing

- For PDFs, open one page at a time with a Java PDF library. Try text-layer extraction first. A page with meaningful native text becomes `pdf_text_layer` output and does not go through Tesseract.
- Render only textless/scanned pages at a configured DPI, submit one image at a time to Tesseract, and parse TSV directly.
- PNG/JPEG inputs go directly to the Tesseract path.
- Use `ProcessBuilder` with fixed arguments rather than a shell. Bound per-page execution time, process output, maximum pages, and document-level concurrency. Begin with a concurrency default of `1`.
- Containerize the Tesseract binary and required language data in the worker image. Local Docker Compose must default to Tesseract, not mock.
- Map unavailable binaries, missing language data, corrupt/encrypted PDFs, page limits, timeouts, and non-zero Tesseract exits to sanitized provider error codes so existing retry/DLQ behavior remains effective.

### API: extraction and persistence

Create an API-owned `InvoiceExtractionPort`, accepting the persisted structured OCR result and a request that carries the workspace, OCR job, file, workflow execution, and correlation identifiers. `HeuristicInvoiceExtractionAdapter` is the initial implementation.

The adapter should:

1. locate explicit invoice labels and candidate values;
2. use page/line proximity to associate labels and values;
3. validate value shapes (currency, ISO-like dates, totals, identifiers);
4. compare subtotal/tax/total candidates when those values are available;
5. attach word references, page/line provenance, and confidence where source words provide it; and
6. produce warnings and `review_required` when required fields are absent, ambiguous, invalid, or inconsistent.

`WorkflowRunner` should call the port and persist the returned result. It must contain no hard-coded invoice values, fixture identifiers, or fake-data metadata.

### Data model

Add a forward-only Flyway migration rather than altering historic migrations.

`ocr_jobs` remains the lifecycle, retry, and audit anchor. Add a separately persisted OCR-result model, with a unique result per job:

- `ocr_results`: job/workspace linkage, provider/provider version, document text, page count, source metadata, and timestamps;
- `ocr_result_words`: one row per ordered word with page, TSV grouping identifiers, nullable confidence, nullable bounding box, source kind, and a foreign key to the OCR result.

The legacy `ocr_jobs.extracted_text` fields may remain for migration compatibility, but the new result model becomes the source of truth for new jobs. Existing completed jobs can be read through a clearly documented compatibility fallback until a deliberate cleanup migration is approved.

Replace runtime use of `demo_invoice_extractions` with production-safe records:

- `invoice_extractions`: unique workflow-execution linkage, job/file/workspace linkage, extractor identity, status (`completed` or `review_required`), warnings, and timestamps;
- `invoice_extraction_fields`: unique field key per extraction, normalized/display value, field status, nullable confidence, and provenance references to the OCR result/words.

The current `demo_invoice_extractions` test-data constraint cannot store real user results. Retire its runtime repository/seeded workflow use; keep fake invoice examples only under test sources and explicitly labelled test fixtures.

Uniqueness constraints must protect at least one OCR result per OCR job, one invoice extraction per workflow execution, and one field key per extraction. Repository methods must scope reads by workspace.

### Events, workflow, search, and audit

- Keep `OcrStarted`, `OcrCompleted`, and `OcrFailed` metadata-only. They must continue to exclude OCR text, Tesseract TSV, extracted values, and source coordinates.
- `OcrCompleted` triggers the existing workflow path. The workflow action reads the local persisted result after validating workspace/job ownership.
- Retain transactional persistence plus outbox use for all workflow, notification, audit, and search side effects.
- Reuse existing event-consumption and workflow trigger idempotency. Add persistence constraints and duplicate-delivery tests at the new result boundaries.
- Search must consume the persisted extraction. It may index the approved structured invoice fields required by the MVP, but never raw OCR text or TSV. Its outbox event remains metadata only.
- Audit entries record IDs, provider, status, attempts, and review state; they do not record document text or values.

### Profiles and configuration

- Production and Compose default: `OCR_PROVIDER=tesseract`.
- Mock provider: move to test sources or strictly test-only configuration. It may generate structured fixture words/text, but must not be a production bean or a valid production configuration.
- Fail startup for unsupported provider configuration and explicitly fail a production profile configured with mock.
- Document the required environment variable names in `.env.example`, without real secrets. Expected new configuration includes the Tesseract command, languages, page timeout, maximum concurrency, PDF render DPI, and maximum pages.
- Apply worker container resource limits in the deployment configuration where the project already defines them; at minimum document and enforce application-level concurrency, page, timeout, and upload limits.

### UI and API surface

Extend the authenticated OCR-job detail response with an extraction summary and fields, only after checking the same workspace/file access used for OCR text. The list endpoint should expose safe summary state (`review_required`, provider, extraction presence) without field values unless that is already appropriate for the list view.

Update `apps/web/src/features/media/media-screen.tsx` and its API types/hooks:

- remove static “Mock provider” labelling;
- show the actual provider and extraction status;
- show a structured fields card with value, confidence when available, source page/line provenance, and warnings;
- make review-required explicit in text and status chips, not color alone; and
- keep the existing desktop right inspector and mobile bottom sheet behavior.

## Test and verification requirements

Start with narrow tests, then run broader checks:

1. Worker unit tests for native PDF text detection, Tesseract TSV parsing, structured word preservation, provider failure mapping, limits, and temporary-file cleanup. Use a process runner/source-reader seam; do not require a real binary in every unit test.
2. API unit tests for label/proximity extraction, validation, arithmetic checks, field provenance, warnings, and no-fabrication cases.
3. API integration tests for migration-backed persistence, workspace scoping, duplicate OCR delivery, duplicate workflow triggering, extraction idempotency, review-required state, and metadata-only event payloads.
4. Controller/API tests for authorized OCR detail retrieval and field visibility.
5. Web render tests for completed extraction, review-required warnings, empty fields, and mobile/desktop detail layout.
6. A Docker Compose smoke test using a labelled fake/scanned invoice image through real Tesseract. It must assert persisted structured OCR words and a reviewable extraction; no real personal invoice belongs in the fixture.
7. Run the narrow Maven tests first, then `apps/worker` and `apps/api` test/Spotless checks, web tests/lint/typecheck, relevant Docker build/smoke checks, and dependency/container scans when dependencies or images change.

## Documentation updates required during implementation

- `docs/architecture/EVENTS.md`: document internal structured OCR persistence and reaffirm metadata-only event payloads.
- `docs/architecture/PERMISSIONS.md`: document access to structured OCR/extraction details as derived from source-file access.
- `.env.example`, worker configuration, Compose, and deployment docs: document non-secret OCR runtime settings and Tesseract availability.
- Update/delete any runtime fake invoice/demo documentation that claims production extraction is fake/test data.

## Risks and trade-offs

- Tesseract quality varies with document language and scan quality; `review_required` is the safety boundary, not a failure of the design.
- Storing all OCR words increases database volume. The MVP accepts that cost for provenance, with page and document limits plus indexes on result/reading order. Retention/compaction is a follow-up.
- The worker receives decryption capability. That is necessary for self-hosted OCR but makes worker secrets and runtime isolation security-critical.
- Tesseract and PDF rendering increase image size and startup/build time. One-page processing, bounded concurrency, and timeouts protect the MVP deployment.

## Implementation review gates

Before editing production code, the implementation agent must:

1. reread this design and the repository’s required architecture, event, permission, development, test, and security documents;
2. inspect the current API/worker storage and OCR code with CodeGraph;
3. produce a file-level implementation plan and flag any conflict with existing active work;
4. perform architecture, security/privacy, Spring, migration, event-contract, frontend, and accessibility review as each affected area becomes concrete; and
5. obtain/maintain a clean baseline before claiming a check passed.
