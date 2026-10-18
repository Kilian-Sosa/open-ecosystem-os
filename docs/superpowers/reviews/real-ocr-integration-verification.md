# Task 8 real OCR integrated verification

Date: 2026-07-13

## Scope and isolation

- Source branch: `feat/real-ocr-extraction` at `28d5de382813faa48a5bf222d765d29bf557079d`.
- Verification worktree: `C:\Users\kilia\AppData\Local\Temp\open-ecosystem-os-task8-verification` (fresh detached HEAD at the same commit).
- No production, test, migration, runtime, configuration, or documentation change was required. No commit was created and no cherry-pick was necessary.
- The old API, API-contract, worker, web, and Task 7 worktrees were checked before testing. All intended code is integrated in the source branch; only the old API-contract worktree retained historical untracked reports/plans.
- Diff base: `993b3479cb2401b8a9d831695a4244b8fa947bfe` (`origin/develop` merge base). The feature has the six expected integration commits. `git diff --name-status <base> <head> -- apps/api/src/main/resources/db/migration/V[1-6]*` returned no files, confirming V1-V6 were not rewritten.
- Main-source search found no runtime mock/fixed OCR or invoice-extraction path. Worker, Compose, and Kubernetes default `OCR_PROVIDER` to `tesseract`. The only `MOCK_OCR_FAILED` occurrence is a legacy sanitized failure-code constant, not a provider registration or runtime path.

## Verification results

### Worker

Passed:

- `apps/worker/.\mvnw.cmd -q "-Dtest=TesseractTsvParserTest,BoundedProcessRunnerTest,DriveOcrSourceReaderTest,TesseractOcrProviderTest,OcrJobProcessorTest" test`
- `apps/worker/.\mvnw.cmd -q test`
- `apps/worker/.\mvnw.cmd -q spotless:check`

The Maven runs emitted non-failing Java dynamic-agent/PDF-related warnings only.

### API

Passed:

- `apps/api/.\mvnw.cmd -q "-Dtest=InvoiceAutomationSeedMigrationTest,JdbcOcrResultRepositoryTest,JdbcInvoiceExtractionRepositoryTest" test`
- `apps/api/.\mvnw.cmd -q "-Dtest=HeuristicInvoiceExtractionAdapterTest,WorkflowExecutionServiceTest,SearchServiceTest,OcrJobControllerTest,FileUploadedOcrJobServiceTest" test`
- `apps/api/.\mvnw.cmd -q test`
- `apps/api/.\mvnw.cmd -q spotless:check`

All eight migrations applied in the test database. The existing RabbitMQ connection-refused messages in isolated unit tests were non-failing test-environment noise.

### Web

The detached worktree needed a clean, frozen-lockfile `corepack pnpm install --frozen-lockfile` because `node_modules` is not tracked.

Passed:

- `apps/web/.\node_modules\.bin\vitest.CMD run src\lib\media-api.test.ts src\features\media\media-screen.test.tsx` — 2 files, 10 tests.
- `apps/web/corepack pnpm format:check`
- `apps/web/corepack pnpm lint`
- `apps/web/corepack pnpm typecheck`
- `apps/web/corepack pnpm test` — 13 files, 43 tests.
- `apps/web/corepack pnpm build`

`corepack pnpm exec vitest ...` could not resolve `vitest` despite the local `.CMD` shim being installed. Direct invocation of that shim succeeded, and package scripts resolve it normally; this is a local pnpm/Windows execution limitation, not a source failure.

### Runtime

Passed:

- `docker compose -f infra\docker\docker-compose.yml config --quiet`
- `docker compose -f infra\docker\docker-compose.yml build worker`
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\k8s-validate.ps1 -KubeconformImage ghcr.io/yannh/kubeconform:latest` — base 11/11, dev 24/24, prod 11/11 valid.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-real-ocr.ps1 -TimeoutSeconds 240` — Tesseract 5.5.1 and the labelled synthetic invoice completed the real OCR smoke.

`make k8s-validate` is not valid in this Windows environment because its Git-Bash process cannot create a signal pipe. The equivalent repository PowerShell validator passed.

### Security and quality

Passed:

- `apps/web/corepack pnpm audit --audit-level high` — no known vulnerabilities.
- `apps/api/.\mvnw.cmd -q verify -P security-scan`
- `apps/worker/.\mvnw.cmd -q verify -P security-scan`
- `git diff --check <base> <head>`

Unavailable:

- Trivy filesystem and IaC scans: `trivy` is not installed.
- Container CVE scan: Docker Scout is installed but requires Docker authentication; no login was attempted.

## Privacy, idempotency, and isolation review

- Static review of changed event/outbox/audit/notification paths found no new use of OCR text, TSV, field values, filenames, temporary paths, or diagnostics in OCR completion events, audit attributes, notifications, or search side effects.
- `OcrJobProcessorTest.keepsOcrEventsAndAuditsMetadataOnly` verifies that `OcrCompleted` payloads exclude OCR text and storage-key data and that audit attributes exclude extracted-text length.
- `WorkflowExecutionServiceTest.duplicateOcrDeliveryPersistsOneRealExtractionAndOnlySafeDownstreamData` verifies a single extraction, notification, audit entry, indexing outbox event, and metadata-only downstream text after duplicate delivery.
- The worker and API repository/controller focused checks cover workspace-scoped OCR/extraction reads and source-file authorization.
- Residual risk: the pre-existing `FileUploaded`/`OcrRequested` transport contract carries `storageKey`. It was present before this feature and remains necessary to the existing event lineage check; the new OCR completion contract does not propagate it. This conflicts with the stricter approved-design preference to keep storage keys out of events and warrants a separately scoped event-contract migration.

## Review conclusion

Task-specific architecture, Spring/backend, migration, event-contract, frontend/accessibility, and security/privacy checks were considered against the approved design and handoff. No evidence-backed integration, compilation, migration, test, configuration, smoke, or new privacy failure required a code fix.

## Remaining risks

- PostgreSQL-specific migration/locking behavior is still not exercised by the H2-backed API tests.
- Trivy and authenticated container CVE scans were unavailable locally.
- The legacy storage-key event payload should be removed through a compatible, separately planned event-contract change.
