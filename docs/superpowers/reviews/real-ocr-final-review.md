# Final review: real OCR extraction

- **Branch:** `feat/real-ocr-extraction`
- **Merge base:** `993b3479cb2401b8a9d831695a4244b8fa947bfe` (`develop`)
- **Reviewed head:** `28d5de382813faa48a5bf222d765d29bf557079d`
- **Date:** 2026-07-13
- **Mode:** Read-only review; no implementation files changed and no full test suite rerun

## Recommendation

**Do not merge.** There are no Critical findings, but the Important findings include claim-expiry races, unbounded processing stages, native-PDF extraction defects, ambiguous/fabricated field values, privacy-expensive list queries, and polling behavior that can both stop too soon and run indefinitely. Resolve all Important findings and repeat focused review before merge.

| Classification | Count |
| -------------- | ----: |
| Critical       |     0 |
| Important      |    14 |
| Minor          |     4 |
| Cannot verify  |     4 |

## Review basis

The review covered the approved design and handoff, the full implementation plan, `docs/superpowers/reviews/real-ocr-implementation-history.md`, the integration verification, all task reports, required project architecture/product/design/security/development documents, and the complete feature diff from the merge base through the reviewed head. Focused inspection also covered the affected worker, API, web, migration, runtime, test, smoke, and documentation paths. Existing fresh integration evidence was relied on; full suites were not rerun.

## Critical

None.

## Important

### I-01 â€” A valid OCR job can outlive its claim and be reclaimed while still processing

- **Exact location:** `apps/worker/src/main/resources/application.yml:49-55`; `infra/docker/docker-compose.yml:167-172`; `infra/k8s/base/configmap.yaml:36-41`; `apps/worker/src/test/java/com/openecosystem/os/worker/config/WorkerOcrPropertiesTest.java:11-17`.
- **Requirement violated:** Concurrency permits and stale-claim fencing must prevent overlapping work and false lifecycle transitions for every legal configured job.
- **Concrete failure scenario:** The deployed limits permit 50 pages at 60 seconds per Tesseract page, a 3,000-second subprocess budget before PDF loading/rendering and persistence. The stale-processing timeout is 15 minutes. A legitimate all-scanned PDF can therefore be reclaimed after 900 seconds while the original worker still owns a permit and is processing. The original completion is fenced, but a second worker can duplicate expensive plaintext processing and retries can be consumed without an OCR defect. The properties test constructs an in-memory one-hour default and does not bind or validate the deployed configuration, so it does not prove this invariant.
- **Smallest safe correction:** Add a startup invariant that the stale timeout exceeds the maximum whole-document execution budget plus source/decryption/render/persistence margin, or implement a renewable lease/heartbeat that is fenced by claim token. Align Compose and Kubernetes values.
- **Focused proof:** Add a configuration-binding test using `application.yml` and deployment-equivalent overrides that rejects `staleProcessingTimeout <= maxPages * pageTimeout + overhead`; add a repository/processor test with a controllable clock proving a live maximum-duration claim cannot be reclaimed.

### I-02 â€” The per-page timeout does not cover PDF parsing, native-text extraction, or rendering

- **Exact location:** `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/TesseractOcrProvider.java:71-100`, `:104-149`, and `:162-177`.
- **Requirement violated:** Per-page execution must be bounded, termination must cover failure paths, and plaintext/permits must not be held indefinitely.
- **Concrete failure scenario:** `pageTimeout` is passed only to the external Tesseract process. `Loader.loadPDF`, `PDFTextStripper.getText`, `PDFRenderer.renderImageWithDPI`, and `ImageIO.write` execute in-process without a deadline or hard cancellation boundary. A malformed or computationally hostile PDF can hold the document permit and decrypted source indefinitely; the stale-claim mismatch can then cause parallel reprocessing.
- **Smallest safe correction:** Put the complete per-document/per-page PDF operation behind a hard, enforceable boundary. Prefer a bounded helper process for hostile PDF operations; if an executor is used, ensure cancellation actually stops work and add a whole-document deadline in addition to the Tesseract timeout.
- **Focused proof:** Introduce a blocking PDF-operation seam and a malformed/hostile fixture test proving the whole operation terminates within the configured budget, releases its permit, leaves no completed result, records only a sanitized failure, and deletes temporary plaintext.

### I-03 â€” A 20-character text layer can suppress OCR for an otherwise scanned invoice

- **Exact location:** `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/TesseractOcrProvider.java:22`, `:80-90`, and `:104-125`.
- **Requirement violated:** Native PDF text should be preferred only when it is meaningful for the page; scanned-image invoice content must not be silently skipped.
- **Concrete failure scenario:** Any 20 non-whitespace characters make a page native-text-only. A scanned invoice with a short header/footer, watermark, partial accessibility layer, or hidden text overlay can meet that threshold while all invoice fields remain in the raster image. Tesseract is skipped and the workflow produces an incomplete extraction with a falsely successful OCR lifecycle.
- **Smallest safe correction:** Replace the character-count heuristic with a quality/coverage decision that considers token count, usable lines, page coverage, and suspicious text layers, or use a hybrid fallback when native text cannot support extraction.
- **Focused proof:** Add a PDF fixture containing a scanned invoice plus a 20+ character footer/partial text layer and assert that OCR/hybrid extraction still recovers the labelled invoice fields; retain a genuine born-digital fixture that proves Tesseract is bypassed.

### I-04 â€” Born-digital PDF words are emitted with incompatible line structure

- **Exact location:** `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/TesseractOcrProvider.java:115-125`; `apps/api/src/main/java/com/openecosystem/os/invoice/HeuristicInvoiceExtractionAdapter.java:227-251`.
- **Requirement violated:** Structured reading order must be schema-compatible across Tesseract and native-text paths, and spatial/label extraction must work for born-digital PDFs.
- **Concrete failure scenario:** Every native-text token is assigned its own block number while paragraph and line are always zero. The API groups words by page/block/paragraph/line, so every word becomes a separate line. Multi-word labels such as `Invoice Number` and `Issue Date` cannot match, and next-line candidates can be associated with unrelated tokens. Current worker tests prove source kind/order only, not end-to-end extraction compatibility.
- **Smallest safe correction:** Preserve native text line boundaries and assign stable block/paragraph/line/word identifiers compatible with the TSV model; retain deterministic document order.
- **Focused proof:** Add an API/worker contract fixture for a born-digital multi-line invoice and assert the persisted word grouping plus extracted invoice number, dates, amounts, and source word IDs.

### I-05 â€” Direct image inputs have no pre-decode pixel bound

- **Exact location:** `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/TesseractOcrProvider.java:45-55` and `:162-177`; `apps/worker/src/main/resources/application.yml:52-54`.
- **Requirement violated:** Image/rendered-pixel work must be bounded before allocation or decoding.
- **Concrete failure scenario:** The PDF path checks estimated rendered pixels before allocating a `BufferedImage`, but PNG/JPEG inputs are handed directly to Tesseract with only a compressed-byte cap. A small compressed image with extreme declared dimensions can force very large decode allocations in the child process and exhaust the worker container, causing restart/retry loops instead of a controlled OCR failure.
- **Smallest safe correction:** Read image metadata without full decode, validate positive width/height and checked-multiply against `maxRenderedPixels`, and reject unsupported/bomb-like images before invoking Tesseract.
- **Focused proof:** Add tiny-byte PNG/JPEG fixtures with dimensions over the pixel limit and assert that the process runner is never invoked, the failure is sanitized, and source cleanup occurs.

### I-06 â€” TSV bytes are capped, but the number of persisted words is not

- **Exact location:** `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/TesseractTsvParser.java:18-24` and `:27-80`; `apps/worker/src/main/java/com/openecosystem/os/worker/config/WorkerOcrProperties.java:1-95`; `docs/superpowers/plans/2026-07-10-real-ocr-invoice-extraction.md:82`.
- **Requirement violated:** Tesseract output and word counts must both be bounded before allocation/persistence.
- **Concrete failure scenario:** A TSV under the 10 MiB byte limit can contain hundreds of thousands of short word rows. The parser grows an unbounded `ArrayList`, then the worker attempts a very large result transaction, producing memory pressure, long locks, or repeated failures. The implemented oversized-output test proves only the byte cap; the plan's explicit maximum-word-limit criterion is not implemented.
- **Smallest safe correction:** Add a positive `maxWordsPerDocument`/`maxWordsPerPage` property, stop before appending beyond it, and expose consistent runtime configuration and sanitized failure codes.
- **Focused proof:** Add a syntactically valid TSV that stays below the byte cap but exceeds the word cap; assert early rejection, no result rows, bounded memory behavior, and cleanup.

### I-07 â€” Conflicting labelled values are considered ambiguous only when their scores tie

- **Exact location:** `apps/api/src/main/java/com/openecosystem/os/invoice/HeuristicInvoiceExtractionAdapter.java:124-145`; `apps/api/src/test/java/com/openecosystem/os/invoice/HeuristicInvoiceExtractionAdapterTest.java:165-179`.
- **Requirement violated:** Conflicting invoice values must not be silently selected; ambiguity must produce a warning and `review_required` without fabrication.
- **Concrete failure scenario:** If an invoice contains two explicit `Total` labels with different valid values but one candidate is geometrically closer, the lower score wins and the distinct second total is ignored. The ambiguity test places candidates at an equal score, so it passes without proving the real requirement.
- **Smallest safe correction:** Compare normalized values from all valid explicitly labelled candidates. If more than one distinct value remains, mark the field ambiguous regardless of distance; use score only to select among equivalent evidence.
- **Focused proof:** Add a fixture with two valid, differently spaced `Total` labels and different amounts; assert no total field is saved, a sanitized `ambiguous_total` warning is emitted, and the extraction is `review_required`.

### I-08 â€” A bare dollar sign is fabricated as USD

- **Exact location:** `apps/api/src/main/java/com/openecosystem/os/invoice/HeuristicInvoiceExtractionAdapter.java:363-376`.
- **Requirement violated:** Extraction must not fabricate values and currency must be validated from unambiguous evidence.
- **Concrete failure scenario:** `$` is used for USD, CAD, AUD, NZD, and other currencies. The adapter converts any bare `$` to `USD` while oddly excluding `US$`, so a Canadian or Australian invoice is stored and indexed with a false currency and can drive incorrect workflow decisions.
- **Smallest safe correction:** Accept an explicit ISO code or an explicitly unambiguous currency marker; treat a bare `$` as value-free ambiguity that requires review.
- **Focused proof:** Add table-driven tests for `$`, `US$`, `USD`, `CAD`, and `AUD`; assert only unambiguous evidence produces a currency and that bare `$` produces a sanitized warning/review state.

### I-09 â€” The metadata-only list endpoint loads every OCR word and extracted field value

- **Exact location:** `apps/api/src/main/java/com/openecosystem/os/media/OcrJobQueryService.java:48-54` and `:75-102`; `apps/api/src/main/java/com/openecosystem/os/media/JdbcOcrResultRepository.java:54-62` and `:130-182`; `apps/api/src/main/java/com/openecosystem/os/invoice/JdbcInvoiceExtractionRepository.java:52-94`.
- **Requirement violated:** List responses and their query paths must minimize sensitive content and remain reliable at scale; list views must not process OCR text or extracted field values.
- **Concrete failure scenario:** For each list row, `toSummaryResponse` calls repositories that materialize the complete document, pages, words, extraction fields, values, and sources only to compute two booleans/statuses. Serialization does not leak field values, but the server unnecessarily reads private document content into memory and creates an N+1 query pattern. A workspace with many large OCR results can exhaust heap or time out a summary request and broadens the in-process privacy exposure.
- **Smallest safe correction:** Add workspace-scoped metadata projections or `exists`/status queries joined or batched with the job list; do not map document text, words, field values, or source words on the list path.
- **Focused proof:** Add a repository/service test with query counting or spies that calls the list endpoint and proves no page/word/field-value mapper or content query executes, while the JSON still contains no field values.

### I-10 â€” Polling can miss a newly queued job and can also continue forever

- **Exact location:** `apps/web/src/features/media/media-screen.tsx:79-90` and `:211-226`; `apps/web/src/lib/media-api.ts:198-223`; `apps/web/src/features/media/media-screen.test.tsx:206-246`.
- **Requirement violated:** Upload-to-OCR tracking must use bounded polling and must reliably cover the asynchronous event-to-job creation delay.
- **Concrete failure scenario:** After upload, the list is invalidated once. If the outbox/event consumer has not created a job yet and the returned list has no active jobs, `shouldPollOcrJobs` is false, so polling stops even though `pendingUpload` is set. After 20 seconds only the banner changes and pending tracking is cleared; the eventual job never appears without manual reload. Conversely, any queued/processing job causes list/detail polling forever because active jobs have no elapsed-time or attempt budget. The existing 30-second test covers only a completed job awaiting extraction.
- **Smallest safe correction:** Track an explicit bounded polling window/attempt budget for both pending uploads and active jobs. Poll while the uploaded file is awaiting its job, stop after the budget with a stale/manual-refresh state, and apply a bounded policy to active job polling as well.
- **Focused proof:** With fake timers, test (1) repeated empty list responses after upload continue polling until the matching job appears, (2) the pending window stops at its bound with a recoverable state, and (3) an indefinitely active job stops automatic polling at the documented bound.

### I-11 â€” Supported demo commands still call endpoints removed by the feature

- **Exact location:** `scripts/seed-demo-data.ps1:18-25`; `scripts/seed-demo-data.sh:8-13`; `scripts/reset-demo-data.ps1:18-25`; `scripts/reset-demo-data.sh:8-13`; `Makefile:10-17` and `:125-129`.
- **Requirement violated:** Runtime tooling and documentation must be consistent with implemented behavior; runtime mock/fixed-value lifecycle paths must be removed.
- **Concrete failure scenario:** The mock demo controller was deleted, but `make seed-demo` and `make reset-demo` still call `/api/demo/invoice-automation/runs`. The documented developer commands now fail with 404 and suggest a lifecycle path that no longer exists. The integration source search did not include these runtime scripts.
- **Smallest safe correction:** Remove the obsolete targets/scripts or retarget them to a clearly synthetic real upload/OCR smoke flow that uses existing public contracts.
- **Focused proof:** Use `Select-String -Path Makefile,scripts/* -Pattern '/api/demo/invoice-automation'` and require no active references; if replacement targets remain, run only those focused commands against the test stack and assert real OCR-originated state.

### I-12 â€” Persisted provider version is a label, not the actual Tesseract version

- **Exact location:** `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/TesseractOcrProvider.java:45-60`; `scripts/ocr-smoke.ps1:93-111`; `scripts/ocr-smoke.sh:83-101`.
- **Requirement violated:** OCR provenance must record the real provider and provider version, and the real-Tesseract smoke must prove it.
- **Concrete failure scenario:** Every result stores `providerVersion = "tesseract-cli"`, while the built image reportedly contains Tesseract 5.5.1. Operators and users cannot identify which OCR engine version produced a result, and smoke assertions on provider/word count can pass while the version field is false.
- **Smallest safe correction:** Probe a bounded `tesseract --version` once at startup (or inject an image-build version label), normalize the semantic version, and persist it without command output or paths.
- **Focused proof:** Add a bounded version-probe unit test and make both real smoke scripts assert that the returned provider version matches the version reported inside the running worker image.

### I-13 â€” Extraction audit records omit extraction status and review state

- **Exact location:** `apps/api/src/main/java/com/openecosystem/os/flows/WorkflowRunner.java:510-518` and `:521-546`; `apps/api/src/main/resources/db/migration/V8__real_invoice_extraction.sql:15-18`.
- **Requirement violated:** Metadata-only audit entries must record the extraction identity/status and whether human review is required, without recording values.
- **Concrete failure scenario:** Workflow audit attributes include workflow/execution/step IDs, OCR job/file IDs, and text length, but not extraction ID, extraction status, or `reviewRequired`. An ambiguous extraction and a fully completed extraction are indistinguishable in the audit trail, weakening incident investigation and lifecycle assertions.
- **Smallest safe correction:** Add extraction ID, normalized extraction status, and review-required boolean to the relevant audit action after persistence; keep values, OCR text, warnings, and source text out.
- **Focused proof:** Add focused workflow tests for completed and review-required extraction that assert the audit metadata contains those three fields and contains no extracted value, OCR text, warning detail, or source word text.

### I-14 â€” Asynchronous upload/OCR feedback is not announced to screen readers

- **Exact location:** `apps/web/src/features/media/media-screen.tsx:264`, `:523-527`, and `:544-555`.
- **Requirement violated:** Dynamic success, delay, and failure states must have correct semantics and screen-reader announcement behavior.
- **Concrete failure scenario:** The feedback banner and upload error are plain `div` elements with no `role`, `aria-live`, or atomic semantics. A keyboard/screen-reader user can upload a file and receive no announcement when the upload succeeds, OCR tracking is delayed/completes, or upload fails.
- **Smallest safe correction:** Use a polite atomic status region for informational/progress messages and an alert region for actionable failures/warnings, avoiding duplicate announcements when text changes.
- **Focused proof:** Add Testing Library assertions using `getByRole('status')`/`getByRole('alert')` for upload success, delayed-job warning, OCR completion, and upload failure; retain keyboard/focus tests for the mobile detail sheet.

## Minor

### M-01 â€” Plaintext deletion failures are silently swallowed

- **Exact location:** `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/OcrSource.java:28-43`; `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/DriveOcrSourceReader.java:159-174`.
- **Requirement violated:** Decrypted plaintext and rendered pages should be cleaned on every path with observable failure handling.
- **Concrete failure scenario:** Normal paths use `finally`/`AutoCloseable`, but cleanup catches and ignores `IOException`. A transient filesystem/handle error leaves decrypted content until tmpfs/container teardown with no sanitized metric or retry, so tests that only exercise successful deletion overstate the guarantee.
- **Smallest safe correction:** Keep cleanup best-effort but record a content-free cleanup failure metric/log, retry boundedly where safe, and sweep only worker-owned temporary directories on startup/shutdown.
- **Focused proof:** Inject a filesystem/deleter seam that fails once and assert a bounded retry or sanitized cleanup signal, no path/content disclosure, and eventual deletion when the failure clears.

### M-02 â€” Exact-limit plaintext is rejected because ciphertext uses the plaintext byte cap

- **Exact location:** `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/DriveOcrSourceReader.java:96-121`.
- **Requirement violated:** Authenticated decryption byte limits must enforce the documented plaintext limit accurately.
- **Concrete failure scenario:** AES-GCM ciphertext includes an authentication tag, but the encrypted stream is capped at `maxInputBytes`. A legitimate plaintext object exactly at the allowed size has ciphertext larger than the cap and is rejected before authentication/decryption.
- **Smallest safe correction:** Bound ciphertext using checked `maxInputBytes + expected GCM overhead` (or validated stored encrypted length) while retaining the independent post-decryption plaintext cap.
- **Focused proof:** Add boundary tests for plaintext at exactly the limit, one byte over it, truncated tag, and ciphertext beyond the calculated overhead; assert only the exact-limit valid object succeeds.

### M-03 â€” The product journey still promises an unimplemented P2 Kanban/approval action

- **Exact location:** `docs/product/USER_JOURNEYS.md:22-32`; `docs/product/MVP_SCOPE.md:90-109`; `apps/api/src/main/resources/db/migration/V8__real_invoice_extraction.sql:1-87`.
- **Requirement violated:** Documentation must match implemented MVP behavior and must not present P2 capabilities as part of the completed invoice slice.
- **Concrete failure scenario:** The invoice journey says the flow creates a Kanban task or approval request, but the implementation ends with extraction/search/notification/audit and MVP scope keeps broader approval/board behavior deferred. Reviewers or users can infer a false acceptance/lifecycle assertion.
- **Smallest safe correction:** Mark the Kanban/approval step as future/P2 or remove it from the current implemented journey; document the actual review-required state.
- **Focused proof:** Add a documentation acceptance check or focused review command comparing the journey steps with the seeded workflow action allowlist and MVP scope.

### M-04 â€” Verification reports contain contradicted command claims

- **Exact location:** `docs/superpowers/reviews/task-runtime-ocr-report.md:55`, `:62`, and `:69-71`; `docs/superpowers/reviews/real-ocr-integration-verification.md:61` and `:70`; `docs/superpowers/specs/2026-07-10-real-ocr-invoice-extraction-design.md:3-4`.
- **Requirement violated:** Handoff and verification evidence must state exactly which commands ran and passed; stale evidence must not be used as merge proof.
- **Concrete failure scenario:** The runtime report says `make k8s-validate` passed, while the later integration report says that command is not valid in this Windows environment and only the PowerShell equivalent passed. Both reports say `git diff --check` passed, but a fresh read-only invocation against the recorded base/head exits non-zero; the added design file contains two trailing spaces on lines 3-4. The runtime report also acknowledges that the POSIX script could not be parsed. These inconsistencies make the evidence stronger than what was actually demonstrated.
- **Smallest safe correction:** Correct the reports to name the exact PowerShell validator command, record the unavailable POSIX check, and either remove/allow the intentional Markdown hard breaks before recording a successful diff check.
- **Focused proof:** Run `git diff --check 993b3479cb2401b8a9d831695a4244b8fa947bfe...28d5de382813faa48a5bf222d765d29bf557079d` and the exact PowerShell Kubernetes validator; capture their exit codes and commands verbatim in the refreshed report.

## Cannot verify

### CV-01 â€” PostgreSQL migration, composite lineage constraints, and concurrent claims were not exercised on PostgreSQL

- **Exact location:** `apps/api/src/main/resources/db/migration/V7__ocr_workspace_lineage_and_structured_results.sql:1-126`; `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/JdbcOcrJobRepository.java:52-102`; `docs/superpowers/reviews/real-ocr-integration-verification.md:91`.
- **Requirement not proven:** Composite workspace lineage/foreign keys, upgrade safety, locking, idempotency, and duplicate-delivery behavior must hold on the production database.
- **Concrete failure scenario:** H2 tests can pass while PostgreSQL rejects the V6-to-V7 upgrade, applies composite constraints differently, or behaves differently under concurrent `FOR UPDATE` claims and duplicate event deliveries. This could orphan lineage or duplicate work/results in production.
- **Smallest safe correction:** Add a PostgreSQL Testcontainers migration/upgrade and concurrency slice without broadening the production change.
- **Focused proof:** Run focused PostgreSQL tests that migrate a V6-shaped database with existing rows through V7/V8, assert all composite FK violations fail, and race duplicate events/stale claims while proving one result/extraction/side-effect set.

### CV-02 â€” Filesystem, IaC, and authenticated container vulnerability scans were unavailable

- **Exact location:** `docs/superpowers/reviews/real-ocr-integration-verification.md:74-75` and `:92`; `docs/superpowers/reviews/task-runtime-ocr-report.md:66-68`.
- **Requirement not proven:** The new PDFBox/AWS/runtime packages and Tesseract worker image must pass the repository's filesystem, IaC, and container vulnerability gates.
- **Concrete failure scenario:** A known vulnerability in an OS OCR/image library, Java dependency, Dockerfile, or manifest could ship despite unit/build/smoke success.
- **Smallest safe correction:** Run the repository-pinned Trivy filesystem/IaC checks and an authenticated scan of the exact built worker image digest; triage only actionable findings under the documented policy.
- **Focused proof:** Execute the documented scanner commands in CI or a scanner-enabled environment and attach exit codes, database freshness, image digest, and any allowlist justification.

### CV-03 â€” The POSIX smoke script was neither parsed nor executed

- **Exact location:** `scripts/ocr-smoke.sh:1-111`; `docs/superpowers/reviews/task-runtime-ocr-report.md:69-71`.
- **Requirement not proven:** The Linux/POSIX real-Tesseract smoke path must be syntactically valid and exercise the same genuine runtime path as PowerShell.
- **Concrete failure scenario:** CI or Linux developers can encounter a shell syntax/quoting, JSON parsing, cleanup, or endpoint error even though the PowerShell smoke passes.
- **Smallest safe correction:** Validate and run the script in a Linux CI job or known-good POSIX shell using the same synthetic fixture and exact built image.
- **Focused proof:** Run `sh -n scripts/ocr-smoke.sh`, then execute the script against the Compose stack and assert provider, real provider version, non-empty structured words, extraction state, metadata-only side effects, and cleanup.

### CV-04 â€” Source-file authorization is workspace existence, not a proven `file:view` decision

- **Exact location:** `apps/api/src/main/java/com/openecosystem/os/media/OcrJobQueryService.java:57-72`; `apps/api/src/main/java/com/openecosystem/os/common/security/PlaceholderAuthenticationContext.java:30-49`; `docs/architecture/PERMISSIONS.md:1-96`.
- **Requirement not proven:** OCR/extraction content must be released only after the caller is authorized to view the source file, not merely because the caller belongs to the workspace.
- **Concrete failure scenario:** The query service verifies that a Drive row with matching workspace exists, and the placeholder authentication context proves workspace membership. There is no resource-level `file:view` policy decision to distinguish a member who may view one file from a member who may not. This is acceptable only if the current MVP intentionally equates workspace membership with file view; it does not prove the stated production authorization requirement.
- **Smallest safe correction:** Route detail authorization through the resource-permission service/port before loading OCR result or extraction content; preserve not-found behavior where appropriate to avoid enumeration.
- **Focused proof:** Add focused authorization tests for owner/editor/viewer/guest/auditor or the implemented role matrix, including a same-workspace principal denied source-file view, and assert no OCR/extraction repository is called on denial.

## Acceptance criteria with no additional finding

Subject to the findings above and the explicitly unverified items, focused inspection found the following controls implemented consistently with the approved design:

- Worker source lookup validates job/file/object workspace lineage and object metadata before S3 access; AES-GCM authentication, checksum comparison, plaintext byte checks, and ordinary-path temporary cleanup are present.
- PDF rendering has a checked pre-allocation pixel estimate; the Tesseract subprocess uses literal arguments, bounded captured output, timeout termination, and permit release; claim-token fencing prevents a stale worker from committing terminal state.
- V7 adds composite workspace lineage constraints; result persistence and workflow/extraction paths use stable identities and idempotent lookups; emitted events remain metadata-only.
- The heuristic validates IBAN checksum, strict dates, decimal amounts, arithmetic consistency, and allowlisted workflow/search fields; warnings exposed by the current adapter are fixed/sanitized strings.
- OCR detail content is loaded only after the current workspace/source lookup, and list JSON does not serialize field values even though its internal query path is over-broad as described in I-09.
- The web implements loading, empty, error, permission-denied, review-required, and content-empty states; it uses the shared responsive shell/detail sheet and removes the previous runtime mock values.
- The worker image installs Tesseract and language data, runs non-root with a read-only root filesystem in runtime manifests, and receives bounded temporary storage; `.env.example` contains fake/example values only. The PowerShell smoke evidence demonstrates a genuine Tesseract-backed result, subject to I-12 and CV-03.
