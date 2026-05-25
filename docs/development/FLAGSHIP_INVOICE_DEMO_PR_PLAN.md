# Seeded Flagship Invoice Demo PR Plan

## Branch

```bash
codex/seeded-flagship-invoice-demo
```

## Commit Plan

### 1. `feat(api): add seeded invoice automation demo APIs`

Purpose: add the demo data model, demo run/reset APIs, query APIs used by linked pages, and local search fallback behavior.

Files:

- `.env.example`
- `apps/api/src/main/java/com/openecosystem/os/audit/AuditController.java`
- `apps/api/src/main/java/com/openecosystem/os/audit/AuditQueryService.java`
- `apps/api/src/main/java/com/openecosystem/os/audit/AuditRecordListResponse.java`
- `apps/api/src/main/java/com/openecosystem/os/audit/AuditRecordResponse.java`
- `apps/api/src/main/java/com/openecosystem/os/audit/JdbcAuditRecordRepository.java`
- `apps/api/src/main/java/com/openecosystem/os/demo/DemoInvoiceController.java`
- `apps/api/src/main/java/com/openecosystem/os/demo/DemoInvoiceExtraction.java`
- `apps/api/src/main/java/com/openecosystem/os/demo/DemoInvoiceExtractionResponse.java`
- `apps/api/src/main/java/com/openecosystem/os/demo/DemoInvoiceLinksResponse.java`
- `apps/api/src/main/java/com/openecosystem/os/demo/DemoInvoiceResetResponse.java`
- `apps/api/src/main/java/com/openecosystem/os/demo/DemoInvoiceRun.java`
- `apps/api/src/main/java/com/openecosystem/os/demo/DemoInvoiceRunResponse.java`
- `apps/api/src/main/java/com/openecosystem/os/demo/DemoInvoiceService.java`
- `apps/api/src/main/java/com/openecosystem/os/demo/DemoTimelineStepResponse.java`
- `apps/api/src/main/java/com/openecosystem/os/demo/JdbcDemoInvoiceRepository.java`
- `apps/api/src/main/java/com/openecosystem/os/flows/JdbcWorkflowExecutionRepository.java`
- `apps/api/src/main/java/com/openecosystem/os/media/OcrJobRepository.java`
- `apps/api/src/main/java/com/openecosystem/os/notifications/JdbcNotificationRepository.java`
- `apps/api/src/main/java/com/openecosystem/os/notifications/NotificationController.java`
- `apps/api/src/main/java/com/openecosystem/os/notifications/NotificationListResponse.java`
- `apps/api/src/main/java/com/openecosystem/os/notifications/NotificationQueryService.java`
- `apps/api/src/main/java/com/openecosystem/os/notifications/NotificationResponse.java`
- `apps/api/src/main/java/com/openecosystem/os/search/IndexingRequestedPayload.java`
- `apps/api/src/main/java/com/openecosystem/os/search/JdbcSearchDocumentRepository.java`
- `apps/api/src/main/java/com/openecosystem/os/search/MeilisearchSearchClient.java`
- `apps/api/src/main/java/com/openecosystem/os/search/SearchController.java`
- `apps/api/src/main/java/com/openecosystem/os/search/SearchDocument.java`
- `apps/api/src/main/java/com/openecosystem/os/search/SearchDocumentStatus.java`
- `apps/api/src/main/java/com/openecosystem/os/search/SearchProperties.java`
- `apps/api/src/main/java/com/openecosystem/os/search/SearchResponse.java`
- `apps/api/src/main/java/com/openecosystem/os/search/SearchResultResponse.java`
- `apps/api/src/main/java/com/openecosystem/os/search/SearchService.java`
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/main/resources/db/migration/V4__flagship_invoice_demo.sql`
- `apps/api/src/test/java/com/openecosystem/os/demo/DemoInvoiceControllerTest.java`
- `apps/api/src/test/java/com/openecosystem/os/search/SearchServiceTest.java`

### 2. `feat(flows): extract demo invoice fields and request indexing`

Purpose: extend workflow validation and execution with deterministic fake/test invoice extraction and metadata-only indexing events.

Files:

- `apps/api/src/main/java/com/openecosystem/os/common/events/EventMessagingProperties.java`
- `apps/api/src/main/java/com/openecosystem/os/common/events/EventRabbitConfiguration.java`
- `apps/api/src/main/java/com/openecosystem/os/flows/WorkflowDefinitionValidator.java`
- `apps/api/src/main/java/com/openecosystem/os/flows/WorkflowRunner.java`
- `apps/api/src/test/java/com/openecosystem/os/flows/WorkflowExecutionServiceTest.java`

### 3. `feat(worker): index search documents with Meilisearch`

Purpose: add the worker-backed indexing consumer, retry/DLQ routing, idempotent processing, and richer fake/test OCR content for the invoice demo.

Files:

- `apps/worker/src/main/java/com/openecosystem/os/worker/common/events/EventMessagingProperties.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/common/events/EventRabbitConfiguration.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/MockOcrProvider.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/search/IndexingCompletedPayload.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/search/IndexingFailedPayload.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/search/IndexingRequestedEvent.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/search/IndexingRequestedEventConsumer.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/search/IndexingRequestedEventParser.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/search/MeilisearchIndexClient.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/search/SearchDocument.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/search/SearchDocumentRepository.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/search/SearchDocumentStatus.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/search/SearchIndexClient.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/search/SearchIndexingException.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/search/SearchIndexingOutcome.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/search/SearchIndexingProcessor.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/search/SearchIndexingResult.java`
- `apps/worker/src/main/java/com/openecosystem/os/worker/search/SearchProperties.java`
- `apps/worker/src/main/resources/application.yml`
- `apps/worker/src/test/java/com/openecosystem/os/worker/search/SearchIndexingProcessorTest.java`

### 4. `feat(web): add flagship invoice demo and linked result screens`

Purpose: add the demo entry point, real notification/audit/search screens, and deep-link support for Drive, OCR, and Flows.

Files:

- `apps/web/src/app/admin/audit/page.tsx`
- `apps/web/src/app/app/demo/invoice-automation/page.tsx`
- `apps/web/src/app/app/drive/page.tsx`
- `apps/web/src/app/app/flows/page.tsx`
- `apps/web/src/app/app/media/page.tsx`
- `apps/web/src/app/app/notifications/page.tsx`
- `apps/web/src/app/app/search/page.tsx`
- `apps/web/src/components/layout/app-shell.tsx`
- `apps/web/src/features/audit/audit-log-screen.test.tsx`
- `apps/web/src/features/audit/audit-log-screen.tsx`
- `apps/web/src/features/demo/invoice-automation-screen.test.tsx`
- `apps/web/src/features/demo/invoice-automation-screen.tsx`
- `apps/web/src/features/drive/drive-screen.tsx`
- `apps/web/src/features/flows/flows-screen.tsx`
- `apps/web/src/features/flows/flows-view-helpers.ts`
- `apps/web/src/features/media/media-screen.test.tsx`
- `apps/web/src/features/media/media-screen.tsx`
- `apps/web/src/features/notifications/notification-center-screen.test.tsx`
- `apps/web/src/features/notifications/notification-center-screen.tsx`
- `apps/web/src/features/search/search-screen.test.tsx`
- `apps/web/src/features/search/search-screen.tsx`
- `apps/web/src/lib/audit-api.ts`
- `apps/web/src/lib/demo-invoice-api.ts`
- `apps/web/src/lib/flows-api.ts`
- `apps/web/src/lib/flows-mock-data.ts`
- `apps/web/src/lib/media-mock-data.ts`
- `apps/web/src/lib/notifications-api.ts`
- `apps/web/src/lib/search-api.ts`

### 5. `chore(demo): document and script the invoice automation demo`

Purpose: add run/reset scripts, Compose search config, route/event/permission docs, and README instructions.

Files:

- `Makefile`
- `README.md`
- `docs/architecture/EVENTS.md`
- `docs/architecture/PERMISSIONS.md`
- `docs/development/ROUTES.md`
- `infra/docker/docker-compose.yml`
- `scripts/reset-demo-data.ps1`
- `scripts/reset-demo-data.sh`
- `scripts/seed-demo-data.ps1`
- `scripts/seed-demo-data.sh`

### Optional artifact commit

Commit message:

```txt
docs(demo): add invoice automation screenshot
```

File:

- `artifacts/screenshots/invoice-automation-demo.png`

Recommendation: do not commit this artifact unless the repository intentionally keeps generated screenshots. Prefer attaching it to the PR description or leaving it as a local verification artifact.

## Pull Request

### Title

```txt
Build seeded flagship invoice automation demo
```

### Description

```md
## Summary

Builds the seeded flagship invoice automation demo across the existing vertical slice:

- seeds a fake/test invoice placeholder document through Drive
- queues and completes deterministic mock OCR
- emits and consumes `OcrCompleted`
- extracts fake/test invoice fields through Flows
- creates notification and audit records
- requests indexing and indexes the result into Meilisearch
- exposes linked demo, notifications, audit, and search screens

All seeded invoice values are fake/test data. Test NIF and Test IBAN examples are explicitly labelled as test data in backend fields and UI labels.

## Backend

- Added `demo_invoice_runs`, `demo_invoice_extractions`, and `search_documents`.
- Added demo run/status/reset APIs under `/api/demo/invoice-automation`.
- Added notification, audit, and search query APIs.
- Added workflow action types `extract_invoice_fields` and `request_search_indexing`.
- Added metadata-only `IndexingRequested`, `IndexingCompleted`, and `IndexingFailed` event flow.
- Search now merges Meilisearch results with local `search_documents` rows so demo results appear even while Meilisearch indexing settles.

## Worker

- Added idempotent Meilisearch indexing worker with retry and DLQ routing.
- Added local search document status updates for indexed and failed outcomes.
- Updated mock OCR to return fake/test invoice text for PDF jobs.

## Frontend

- Added `/app/demo/invoice-automation`.
- Replaced notification and audit shells with real data-driven pages.
- Added `/app/search`.
- Added deep-link query params for Drive, Media/OCR, and Flows.
- Added responsive timeline, extracted field summary, and app links.

## Scripts and Docs

- Added Bash and PowerShell seed/reset scripts.
- Wired `make seed` and `make reset`.
- Documented routes, events, permissions, Compose search settings, and README usage.

## Screenshots

Demo screen captured locally:

`artifacts/screenshots/invoice-automation-demo.png`

If the screenshot artifact is not committed, attach it manually to the PR.

## Tests

- `cd apps/api && .\mvnw.cmd test`
- `cd apps/api && .\mvnw.cmd -Dtest=SearchServiceTest test`
- `cd apps/worker && .\mvnw.cmd test`
- `cd apps/web && corepack pnpm test`
- `cd apps/web && corepack pnpm typecheck`
- `cd apps/web && corepack pnpm build`
- `docker compose -f infra/docker/docker-compose.yml config`

## Notes

- No external OCR or AI provider is used.
- Open Ledger and real accounting integrations remain out of scope.
- Full `make docker-up` smoke was not run in this workspace because port `3000` was already occupied by the local dev server used for screenshot verification.
```

## Suggested Staging Commands

Review each group before staging; paths are intentionally explicit.

```bash
git switch -c codex/seeded-flagship-invoice-demo
```

Commit 1:

```bash
git add .env.example \
  apps/api/src/main/java/com/openecosystem/os/audit/AuditController.java \
  apps/api/src/main/java/com/openecosystem/os/audit/AuditQueryService.java \
  apps/api/src/main/java/com/openecosystem/os/audit/AuditRecordListResponse.java \
  apps/api/src/main/java/com/openecosystem/os/audit/AuditRecordResponse.java \
  apps/api/src/main/java/com/openecosystem/os/audit/JdbcAuditRecordRepository.java \
  apps/api/src/main/java/com/openecosystem/os/demo \
  apps/api/src/main/java/com/openecosystem/os/flows/JdbcWorkflowExecutionRepository.java \
  apps/api/src/main/java/com/openecosystem/os/media/OcrJobRepository.java \
  apps/api/src/main/java/com/openecosystem/os/notifications/JdbcNotificationRepository.java \
  apps/api/src/main/java/com/openecosystem/os/notifications/NotificationController.java \
  apps/api/src/main/java/com/openecosystem/os/notifications/NotificationListResponse.java \
  apps/api/src/main/java/com/openecosystem/os/notifications/NotificationQueryService.java \
  apps/api/src/main/java/com/openecosystem/os/notifications/NotificationResponse.java \
  apps/api/src/main/java/com/openecosystem/os/search \
  apps/api/src/main/resources/application.yml \
  apps/api/src/main/resources/db/migration/V4__flagship_invoice_demo.sql \
  apps/api/src/test/java/com/openecosystem/os/demo \
  apps/api/src/test/java/com/openecosystem/os/search
git commit -m "feat(api): add seeded invoice automation demo APIs"
```

Commit 2:

```bash
git add apps/api/src/main/java/com/openecosystem/os/common/events/EventMessagingProperties.java \
  apps/api/src/main/java/com/openecosystem/os/common/events/EventRabbitConfiguration.java \
  apps/api/src/main/java/com/openecosystem/os/flows/WorkflowDefinitionValidator.java \
  apps/api/src/main/java/com/openecosystem/os/flows/WorkflowRunner.java \
  apps/api/src/test/java/com/openecosystem/os/flows/WorkflowExecutionServiceTest.java
git commit -m "feat(flows): extract demo invoice fields and request indexing"
```

Commit 3:

```bash
git add apps/worker/src/main/java/com/openecosystem/os/worker/common/events/EventMessagingProperties.java \
  apps/worker/src/main/java/com/openecosystem/os/worker/common/events/EventRabbitConfiguration.java \
  apps/worker/src/main/java/com/openecosystem/os/worker/ocr/MockOcrProvider.java \
  apps/worker/src/main/java/com/openecosystem/os/worker/search \
  apps/worker/src/main/resources/application.yml \
  apps/worker/src/test/java/com/openecosystem/os/worker/search
git commit -m "feat(worker): index search documents with Meilisearch"
```

Commit 4:

```bash
git add apps/web/src/app/admin/audit/page.tsx \
  apps/web/src/app/app/demo \
  apps/web/src/app/app/drive/page.tsx \
  apps/web/src/app/app/flows/page.tsx \
  apps/web/src/app/app/media/page.tsx \
  apps/web/src/app/app/notifications/page.tsx \
  apps/web/src/app/app/search \
  apps/web/src/components/layout/app-shell.tsx \
  apps/web/src/features/audit \
  apps/web/src/features/demo \
  apps/web/src/features/drive/drive-screen.tsx \
  apps/web/src/features/flows/flows-screen.tsx \
  apps/web/src/features/flows/flows-view-helpers.ts \
  apps/web/src/features/media/media-screen.test.tsx \
  apps/web/src/features/media/media-screen.tsx \
  apps/web/src/features/notifications \
  apps/web/src/features/search \
  apps/web/src/lib/audit-api.ts \
  apps/web/src/lib/demo-invoice-api.ts \
  apps/web/src/lib/flows-api.ts \
  apps/web/src/lib/flows-mock-data.ts \
  apps/web/src/lib/media-mock-data.ts \
  apps/web/src/lib/notifications-api.ts \
  apps/web/src/lib/search-api.ts
git commit -m "feat(web): add flagship invoice demo and linked result screens"
```

Commit 5:

```bash
git add Makefile README.md \
  docs/architecture/EVENTS.md \
  docs/architecture/PERMISSIONS.md \
  docs/development/ROUTES.md \
  infra/docker/docker-compose.yml \
  scripts/reset-demo-data.ps1 \
  scripts/reset-demo-data.sh \
  scripts/seed-demo-data.ps1 \
  scripts/seed-demo-data.sh
git commit -m "chore(demo): document and script the invoice automation demo"
```
