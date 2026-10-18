# Real OCR and Invoice Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the runtime fake invoice path with a self-hosted Tesseract OCR pipeline, structured persisted OCR and invoice extraction, authorized API detail, safe search indexing, and responsive Media/OCR review UI.

**Architecture:** The worker reads only workspace-bound Drive source metadata, streams the private encrypted S3 object through authenticated AES-256-GCM decryption into an owned temporary directory, and produces structured page/word output through PDFBox and Tesseract TSV. The worker commits that result with the OCR lifecycle/outbox state; the API owns heuristic extraction and persists fields separately when the existing `OcrCompleted` workflow runs. The API returns extraction content only after source-file access validation, and Search receives a whitelisted projection of persisted fields.

**Tech Stack:** Java 25, Spring Boot 4, JDBC, Flyway, PostgreSQL/H2 tests, AWS SDK v2, Apache PDFBox, Tesseract CLI, Docker Compose/Kubernetes, Next.js 16, React 19, TanStack Query, Vitest/Testing Library.

## Global Constraints

- Stay on the existing branch `feat/real-ocr-extraction`; do not create or switch branches.
- Do not modify Flyway migrations V1 through V5.
- Production and Compose use `OCR_PROVIDER=tesseract`; `mock` is not a valid runtime provider.
- Process PDF pages sequentially: meaningful native text first, Tesseract only for pages without meaningful native text.
- Preserve Tesseract word text, confidence, page/block/paragraph/line identity, reading order, and bounding boxes.
- Default document OCR concurrency is `1`; bound input bytes, pages, rendered pixels, per-page duration, process output, and temporary storage.
- Launch Tesseract through `ProcessBuilder(List<String>)` without a shell and delete all temporary plaintext in guaranteed cleanup paths.
- Keep OCR text, TSV, field values, filenames, and Tesseract diagnostics out of events, logs, notifications, and audit attributes.
- Missing, ambiguous, invalid, inconsistent, or low-confidence data creates warnings and `review_required`; never synthesize a field.
- Duplicate deliveries must not duplicate OCR results, workflow executions, invoice extractions, fields, notifications, audit rows, outbox records, or search documents.
- Search may project invoice number, supplier name, total/currency, issue date, and due date; exclude raw OCR/TSV, IBAN, and tax identifiers.
- Fake invoice examples may exist only in labelled test fixtures.

---

### Task 1: Structured schema and production-safe seeded workflow

**Files:**

- Create: `apps/api/src/main/resources/db/migration/V7__structured_ocr_invoice_extraction.sql`
- Create: `apps/api/src/main/resources/db/migration/V8__activate_real_invoice_workflow.sql`
- Test: `apps/api/src/test/java/com/openecosystem/os/media/OcrResultRepositoryTest.java`
- Test: `apps/api/src/test/java/com/openecosystem/os/invoice/InvoiceExtractionRepositoryTest.java`

**Interfaces:**

- Produces one workspace-bound `ocr_results` row per OCR job, ordered `ocr_result_pages`, and ordered `ocr_result_words`.
- Produces one workspace-bound `invoice_extractions` row per workflow execution and one `invoice_extraction_fields` row per field key.
- Adds composite uniqueness needed for `(job_id, workspace_id)`, `(file_id, workspace_id)`, `(result_id, workspace_id)`, `(extraction_id, workspace_id)`, and `(execution_id, workspace_id)` foreign keys.

- [ ] Write repository tests that fail because the new tables/repositories do not exist, covering one-result-per-job, one-extraction-per-execution, one-field-key-per-extraction, and foreign-workspace reads returning empty.
- [ ] Run `apps/api/mvnw.cmd -q -Dtest=OcrResultRepositoryTest,InvoiceExtractionRepositoryTest test` and confirm the missing-schema/type failure.
- [ ] Add V7 with forward-only tables, status/confidence/bounds checks, composite workspace foreign keys, uniqueness, and indexes for workspace/job/reading-order lookups; preserve the existing V6 lifecycle-index migration.
- [ ] Add V8 workflow version `wfv_invoice_automation_v3` using generic extraction/review/notification/audit/search actions and update only the seeded `flow_invoice_automation`; do not edit V1-V6.
- [ ] Implement the repositories in Task 3, rerun the two tests, and retain V7/V8 as the authoritative schema contract.

### Task 2: Secure worker source reader and structured Tesseract provider

**Files:**

- Modify: `apps/worker/pom.xml`
- Modify: `apps/worker/src/main/resources/application.yml`
- Modify: `apps/worker/src/test/resources/application.yml`
- Delete: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/MockOcrProvider.java`
- Replace: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/OcrProviderResult.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/OcrDocumentResult.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/OcrPageResult.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/OcrWord.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/OcrSourceKind.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/TesseractOcrProvider.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/TesseractTsvParser.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/BoundedProcessRunner.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/TemporaryOcrWorkspace.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/source/OcrSourceReader.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/source/DriveOcrSourceReader.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/source/DriveSourceMetadataRepository.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/source/WorkerFileDecryptor.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/source/WorkerS3Configuration.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/source/WorkerS3Properties.java`
- Modify: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/WorkerOcrProperties.java`
- Test: `apps/worker/src/test/java/com/openecosystem/os/worker/ocr/TesseractTsvParserTest.java`
- Test: `apps/worker/src/test/java/com/openecosystem/os/worker/ocr/TesseractOcrProviderTest.java`
- Test: `apps/worker/src/test/java/com/openecosystem/os/worker/ocr/BoundedProcessRunnerTest.java`
- Test: `apps/worker/src/test/java/com/openecosystem/os/worker/ocr/source/DriveOcrSourceReaderTest.java`

**Interfaces:**

- `OcrProvider.extract(OcrJob): OcrDocumentResult`
- `OcrDocumentResult(provider, providerVersion, documentText, pages)` validates non-empty ordered pages and derives word count.
- `OcrPageResult(pageNumber, sourceKind, text, words)` keeps one page source and ordered words.
- `OcrWord(readingOrder, pageNumber, blockNumber, paragraphNumber, lineNumber, wordNumber, text, confidence, left, top, width, height)` uses nullable confidence/bounds for native PDF text.
- `OcrSourceReader.open(OcrJob): OcrSource` returns an `AutoCloseable` owned plaintext path.

- [ ] Write failing TSV tests for header validation, tab-preserving text, confidence, grouping IDs, actual document page mapping, boxes, and maximum output/word limits.
- [ ] Write failing provider tests for PDF native-text bypass, mixed-page fallback, sequential page order, max pages/rendered pixels, PNG/JPEG dispatch, timeout/error-code mapping, concurrency permit release, and temporary cleanup.
- [ ] Write failing source-reader tests for workspace/file/storage/content-type/key binding, ciphertext/plaintext byte caps, authenticated decryption, checksum/size verification, and cleanup after every failure.
- [ ] Run the narrow worker tests and confirm failures are caused by missing production types.
- [ ] Add AWS SDK S3 and PDFBox dependencies, keeping the existing URL-connection client and Spring-managed dependency versions where available.
- [ ] Implement bounded stream decryption and private temp directories without original filenames; validate algorithm/key ID and persisted metadata before S3 access.
- [ ] Implement PDFBox one-page-at-a-time extraction/rendering and Tesseract `stdout ... tsv` invocation with bounded concurrent stream draining and fixed sanitized failure codes/messages.
- [ ] Make `WorkerOcrProperties` default to and accept only `tesseract`; keep test providers as test-source `@Primary` beans.
- [ ] Rerun the narrow worker tests until green, then run `apps/worker/mvnw.cmd -q test` and `apps/worker/mvnw.cmd spotless:check`.

### Task 3: Transactional structured OCR persistence and recovery

**Files:**

- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/OcrResultRepository.java`
- Modify: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/OcrJobProcessor.java`
- Modify: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/OcrJobRepository.java`
- Modify: `apps/worker/src/test/java/com/openecosystem/os/worker/ocr/OcrJobProcessorTest.java`

**Interfaces:**

- `OcrResultRepository.save(job, result, now)` inserts result/pages/words in reading order in the same transaction as job completion/outbox/audit/consumption.
- `OcrJobRepository.claimForProcessing(..., staleBefore)` can reclaim only an expired processing claim; a fresh concurrent claim is retried through the existing broker retry path.
- Lifecycle compatibility columns in `ocr_jobs` remain populated, but new reads prefer `ocr_results`.

- [ ] Change processor tests first to assert structured row preservation, duplicate delivery counts, fixed failure summaries, no sensitive event/audit values, and stale processing recovery.
- [ ] Run `apps/worker/mvnw.cmd -q -Dtest=OcrJobProcessorTest test` and confirm the new assertions fail.
- [ ] Persist structured output transactionally and retain metadata-only `OcrStarted`/`OcrCompleted`/`OcrFailed` contracts.
- [ ] Replace arbitrary exception truncation with code-to-fixed-summary mapping; never use provider stderr, paths, or exception messages in persisted/event failure text.
- [ ] Add stale claim recovery without allowing simultaneous active processing, then rerun the processor and consumer tests.

### Task 4: API-owned heuristic invoice extraction and workflow/search integration

**Files:**

- Create: `apps/api/src/main/java/com/openecosystem/os/media/OcrDocumentResult.java`
- Create: `apps/api/src/main/java/com/openecosystem/os/media/OcrPageResult.java`
- Create: `apps/api/src/main/java/com/openecosystem/os/media/OcrWord.java`
- Create: `apps/api/src/main/java/com/openecosystem/os/media/JdbcOcrResultRepository.java`
- Create: `apps/api/src/main/java/com/openecosystem/os/invoice/InvoiceExtractionPort.java`
- Create: `apps/api/src/main/java/com/openecosystem/os/invoice/InvoiceExtractionRequest.java`
- Create: `apps/api/src/main/java/com/openecosystem/os/invoice/InvoiceExtraction.java`
- Create: `apps/api/src/main/java/com/openecosystem/os/invoice/InvoiceExtractionField.java`
- Create: `apps/api/src/main/java/com/openecosystem/os/invoice/InvoiceExtractionWarning.java`
- Create: `apps/api/src/main/java/com/openecosystem/os/invoice/HeuristicInvoiceExtractionAdapter.java`
- Create: `apps/api/src/main/java/com/openecosystem/os/invoice/JdbcInvoiceExtractionRepository.java`
- Modify: `apps/api/src/main/java/com/openecosystem/os/flows/WorkflowRunner.java`
- Modify: `apps/api/src/main/java/com/openecosystem/os/search/JdbcSearchDocumentRepository.java`
- Modify: `apps/api/src/test/java/com/openecosystem/os/flows/WorkflowExecutionServiceTest.java`
- Create: `apps/api/src/test/java/com/openecosystem/os/invoice/HeuristicInvoiceExtractionAdapterTest.java`

**Interfaces:**

- `InvoiceExtractionPort.extract(InvoiceExtractionRequest, OcrDocumentResult): InvoiceExtraction`
- Heuristics use label tokens plus same-line/nearest-line spatial scoring; validate invoice identifiers, supplier text, ISO currency, amounts, dates, tax IDs, and IBAN checksum; reconcile subtotal + tax to total.
- Only observed, valid values become fields. Ambiguous/invalid/missing/low-confidence/inconsistent evidence becomes a value-free warning and `review_required`.
- Workflow step output contains extraction ID/status/counts only.

- [ ] Write failing unit tests for labels, spatial proximity, normalized shapes, IBAN checksum, arithmetic consistency, confidence/provenance, ambiguity, missing required fields, and no fabrication.
- [ ] Write failing workflow integration tests proving one extraction/field set/search document/notification/audit/outbox chain across duplicate `OcrCompleted` delivery and proving all event/audit/notification JSON excludes raw OCR, field values, TSV, and filenames.
- [ ] Run the narrow API tests and confirm the expected missing-type/behavior failures.
- [ ] Implement result loading, extraction, transactional persistence, and generic workflow output; delete all `DemoInvoiceExtraction` dependencies and hard-coded invoice values from `WorkflowRunner`.
- [ ] Build Search content only from the persisted whitelist and link to `/app/media?jobId={jobId}`; keep `IndexingRequested` metadata-only.
- [ ] Rerun extraction/workflow/search tests until green.

### Task 5: Authorized OCR/extraction API and retired runtime demo path

**Files:**

- Modify: `apps/api/src/main/java/com/openecosystem/os/media/OcrJobDetailResponse.java`
- Modify: `apps/api/src/main/java/com/openecosystem/os/media/OcrJobSummaryResponse.java`
- Modify: `apps/api/src/main/java/com/openecosystem/os/media/OcrJobQueryService.java`
- Modify: `apps/api/src/test/java/com/openecosystem/os/media/OcrJobControllerTest.java`
- Delete production files under: `apps/api/src/main/java/com/openecosystem/os/demo/`
- Delete: `apps/web/src/lib/demo-invoice-api.ts`
- Replace route: `apps/web/src/app/app/demo/invoice-automation/page.tsx` with a server redirect to `/app/media`
- Delete production feature files under: `apps/web/src/features/demo/`
- Modify: `apps/web/src/components/layout/app-shell.tsx`
- Modify/remove: `scripts/seed-demo-data.ps1`, `scripts/seed-demo-data.sh`, `scripts/reset-demo-data.ps1`, `scripts/reset-demo-data.sh`, `Makefile`

**Interfaces:**

- List response exposes provider plus extraction presence/status/review flag, never field values.
- Detail response exposes persisted OCR text (with legacy fallback), extraction identity/status/warnings, structured fields, confidence, and page/line/word provenance.
- The query service must resolve the workspace-scoped source `drive_files` row before returning any OCR or extraction content.

- [ ] Extend controller tests first for authorized detail, extraction shape, legacy fallback, foreign workspace, missing source row, metadata-only list, and no field values in list JSON.
- [ ] Run `apps/api/mvnw.cmd -q -Dtest=OcrJobControllerTest test` and confirm the new response/access assertions fail.
- [ ] Implement DTO mapping and source-file gating; return normal API authorization/not-found semantics without revealing cross-workspace existence.
- [ ] Remove production demo repositories/endpoints and redirect the old web route for bookmark compatibility; remove its navigation and scripts.
- [ ] Rerun controller/context tests and verify no main-source reference to `demo_invoice_extractions`, fixed invoice identifiers, or fake-data metadata remains outside immutable V1-V5 history.

### Task 6: Media/OCR extraction review UI

**Files:**

- Modify: `apps/web/src/lib/media-api.ts`
- Modify: `apps/web/src/features/media/use-ocr-jobs.ts`
- Modify: `apps/web/src/features/media/media-screen.tsx`
- Create: `apps/web/src/features/media/media-job-details.tsx`
- Modify: `apps/web/src/app/app/media/page.tsx`
- Delete: `apps/web/src/lib/media-mock-data.ts`
- Modify: `apps/web/src/components/ui/status-chip.tsx`
- Modify: `apps/web/src/features/media/media-screen.test.tsx`
- Modify stale runtime wording in: `apps/web/src/features/search/search-screen.tsx`, `apps/web/src/features/notifications/notification-center-screen.tsx`, `apps/web/src/lib/flows-mock-data.ts`

**Interfaces:**

- `OcrJobSummary` receives `extractionPresent`, `extractionStatus`, and `reviewRequired` only.
- `OcrJobDetail` receives extraction warnings/fields/provenance and a status-bearing API error differentiates permission denial.
- Detail polling continues after OCR completion only while extraction is absent/non-terminal, then stops.

- [ ] Put labelled fake completed/review-required/empty fixtures in `media-screen.test.tsx`, mock the API/hooks, and add failing tests for actual provider text, fields, confidence/provenance, warnings, empty extraction, permission denial, desktop inspector, and mobile sheet.
- [ ] Run `apps/web/node_modules/.bin/vitest.CMD run src/features/media/media-screen.test.tsx` and confirm the new assertions fail.
- [ ] Implement status-aware API errors/polling and remove production state-query fixture injection.
- [ ] Extract shared inspector/sheet details, use semantic warning lists and explicit review text, preserve focus behavior and token-backed styles, and add `review-required` to `StatusChip`.
- [ ] Rerun the focused test, then web format/lint/typecheck/test/build.

### Task 7: Container/runtime configuration, real OCR smoke, and documentation

**Files:**

- Modify: `apps/worker/Dockerfile`
- Modify: `infra/docker/docker-compose.yml`
- Modify: `infra/k8s/base/configmap.yaml`
- Modify: `infra/k8s/base/worker-deployment.yaml`
- Modify: `infra/k8s/overlays/prod/deployment-patch.yaml`
- Modify: `.env.example`
- Create: `apps/worker/src/test/resources/fixtures/fake-scanned-invoice.png`
- Create: `scripts/smoke-real-ocr.ps1`
- Create: `scripts/smoke-real-ocr.sh`
- Modify: `Makefile`
- Modify: `docs/architecture/EVENTS.md`
- Modify: `docs/architecture/PERMISSIONS.md`
- Modify: `docs/architecture/DATA_STRATEGY.md`
- Modify: `docs/architecture/DEPLOYMENT.md`
- Modify: `docs/product/USER_JOURNEYS.md`
- Modify: `docs/development/ROUTES.md`
- Modify: `docs/development/TESTING.md`
- Modify: `docs/development/TEST_COMMANDS.md`

**Interfaces:**

- Worker image contains Tesseract and `eng` language data and runs non-root.
- Compose defaults include provider/command/languages/concurrency/page timeout/max pages/render DPI/input/output/rendered-pixel bounds.
- Kubernetes keeps a read-only root filesystem, sizes the `/tmp` `emptyDir`, and carries the same safe non-secret config and resource limits.
- Smoke uploads a clearly labelled synthetic invoice image, waits for OCR/extraction, and asserts Tesseract provider, structured words, persisted non-fabricated fields, and review state.

- [ ] Add the deterministic labelled fake fixture and smoke assertions before runtime changes; run once to confirm the current mock/flat path fails them.
- [ ] Install Tesseract/language data in the worker image, default all runtime profiles to Tesseract, and add bounded temp/resource settings with fake values only in `.env.example`.
- [ ] Update event/privacy/permission/deployment/search/workflow/route/test documentation and remove active claims that invoice extraction is mock/fixed.
- [ ] Run Compose config, worker image build, Kubernetes render/validation, and the real-Tesseract smoke.

### Task 8: Integrated verification and review

**Files:**

- Review all changed production, test, migration, runtime, and documentation files.

- [ ] Run worker narrow tests, full `test`, and `spotless:check`.
- [ ] Run API extraction/persistence/workflow/controller tests, full `test`, and `spotless:check`.
- [ ] Run web focused tests, format check, lint, typecheck, full tests, and build.
- [ ] Run Docker Compose config/build/smoke and Kubernetes validation.
- [ ] Run frontend audit, Maven dependency/security profiles where vulnerability data is available, Trivy filesystem/IaC, and worker image scan; record any unavailable scanner as an explicit verification limitation.
- [ ] Run task-specific backend/database/event/frontend/accessibility/security reviews, resolve every blocking finding, then run one final integration reviewer over the complete diff.
- [ ] Re-read the approved design line by line and verify every acceptance criterion against code or fresh command output before claiming completion.
