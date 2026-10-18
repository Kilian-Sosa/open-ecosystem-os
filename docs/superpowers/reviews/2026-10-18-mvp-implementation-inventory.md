# MVP implementation inventory — 2026-10-18

**Type:** Source-code evidence audit, not a runtime acceptance test
**Baseline:** `origin/develop` commit `9d3b856`
**Milestones:** [MVP design](../specs/2026-10-18-mvp-milestones-design.md)

## How to read this inventory

- **Implemented in code:** concrete routes/services/storage/tests exist;
  production-like runtime success has **not** been independently established
  by this document.
- **Partial:** useful components exist but the complete user journey does not.
- **Absent:** no matching supported user-facing/controller/worker operation
  was found in the inspected baseline.
- **Placeholder/mock:** UI or docs exist but do not reflect live feature
  behavior.

The original roadmap and design catalog are **desired capabilities**, not
verification evidence. The merged historical OCR reviews retain their own
scope, dates and proof. A test file present in the tree does not mean that
test was run during this audit.

## Core capability inventory

| Capability                   | Baseline status      | Evidence                                                                                        | Missing for Core                                                               |
| ---------------------------- | -------------------- | ----------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ |
| Drive upload                 | Implemented in code  | `DriveFileController`, `DriveUploadService`, `DriveFileRepository`, `DriveScreen`               | Full journey test and production-like verification                             |
| Drive list/metadata          | Implemented in code  | `GET /api/drive/files`, `GET /api/drive/files/{fileId}`                                         | End-to-end permission/error coverage                                           |
| Drive download               | Absent               | No file-content GET controller; metadata GET is not bytes                                       | Authorized plaintext streaming                                                 |
| Drive preview                | Absent               | Metadata inspector only                                                                         | Private PDF/image/text viewers and safe fallback                               |
| Drive move to Trash          | Absent               | No user deletion/Trash controller; internal object rollback is not user deletion                | Logical tombstone, subtree hiding, retained encrypted bytes, index suppression |
| Trash restore and purge      | Absent               | No trash lifecycle metadata, restore endpoint or 30-day retention job                           | Restore, explicit permanent deletion, scheduled purge, retry/race safety       |
| Drive nested folders         | Absent               | V1 `drive_files` has no folder relationship; no `drive_folders` table or folder API             | Create/navigate nested folders, breadcrumbs, durable scoped placement          |
| File/folder rename and move  | Absent               | No folder model or rename/move controllers; object keys are stable file-based IDs               | Permission-checked rename/move, cycle/conflict detection, search updates       |
| Folder Trash / ZIP export    | Absent               | No recursive folder Trash operation or downloadable ZIP endpoint                                | Recoverable folder-tree trash, restore and bounded archive generation          |
| OCR worker                   | Implemented in code  | `TesseractOcrProvider`, `OcrJobProcessor`, `DriveOcrSourceReader`, OCR DB result tables         | End-to-end verification for supported inputs, selectable OCR-only behavior     |
| OCR lifecycle/results UI     | Implemented in code  | `OcrJobController`, `OcrJobQueryService`, `MediaScreen`                                         | Drive action and completed-to-search integration                               |
| Image set → PDF              | Absent               | No PDF assembly processor/action; `WorkflowDefinitionValidator` has no merge action             | Ordered selection, async assembly, generated file, tests                       |
| Invoice heuristic extraction | Implemented in code  | `HeuristicInvoiceExtractionAdapter`, `InvoiceExtractionPort`, `JdbcInvoiceExtractionRepository` | Explicit Drive action; independent from OCR-only                               |
| Invoice review/edits/confirm | Absent               | Extraction statuses `completed` / `review_required`; no review-confirm endpoint                 | Draft lifecycle, editable UI, confirmation gate                                |
| Filename search              | Not evidenced        | `WorkflowRunner` currently creates invoice-extraction search documents                          | Independently index file names on successful upload                            |
| Raw OCR search               | Not evidenced        | OCR text persisted, but no separate OCR-source SearchDocument producer was found                | Raw OCR indexing after completion                                              |
| Confirmed structured search  | Partial / wrong gate | `WorkflowRunner.requestSearchIndexing` on seeded V8 OCR flow                                    | Block draft indexing and index latest confirmed edits only                     |
| Search API and UI            | Implemented in code  | `SearchService`, `SearchController`, `SearchScreen`                                             | New source types, confirmed-only gate, deletion/reindex handling               |

## Existing cross-cutting components

| Component             | Verified code/docs                                                                                          | Gap or scope interpretation                                                                           |
| --------------------- | ----------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- |
| Workflow engine       | `WorkflowController`, `WorkflowService`, `WorkflowRunner`, V3/V8 migrations, execution records and Flows UI | Useful foundation; no configurable builder requirement for Core; current invoice indexing must change |
| Messaging/idempotency | Outbox and consumer repositories in API/worker, RabbitMQ events, job retries/DLQ                            | Reuse for new conversion/search/delete behaviors; verify race coverage                                |
| Notifications         | `NotificationController`, `NotificationQueryService`, `NotificationCenterScreen`                            | Basic read-only list; not equivalent to email/push delivery or public-user notification service       |
| Audit                 | `AuditController`, `AuditQueryService`, `AuditLogScreen`                                                    | Metadata-only events exist; add safe records for new actions                                          |
| Identity/workspace    | `SessionBootstrapController`, `PlaceholderAuthenticationContext`, seeded V5 membership                      | Private-only seeded mode, not real sign-in                                                            |
| Authorization         | `DefaultResourceAuthorizationService`, `FileViewAuthorizationRepository`, V9 user grants                    | Verify every new derivative, download, draft and search operation                                     |
| Dashboard             | `DashboardPage` feeds `dashboardMockData`                                                                   | Placeholder/mock, not live aggregate metrics                                                          |
| Settings              | `/app/settings/page.tsx`                                                                                    | Placeholder navigation screen                                                                         |
| System status         | `/admin/system-status/page.tsx`                                                                             | Placeholder screen; API/worker health and metrics endpoints exist                                     |
| Public site           | `/page.tsx` redirects to `/app/dashboard`                                                                   | Not a public portfolio, no real landing or public demo                                                |
| Legacy demo path      | `/app/demo/invoice-automation/page.tsx` redirects to Media                                                  | Redirect, not separate demo product                                                                   |
| Open Pages            | No `/app/pages` route or API pages bounded context                                                          | Post-MVP product                                                                                      |
| General PDF editor    | No `/app/pdf` route or PDF editing bounded context                                                          | Post-MVP; not to be confused with narrow image-to-PDF Core                                            |
| Compose               | `infra/docker/docker-compose.yml`, `Makefile`, `.env.example`                                               | Local stack scaffold and runnable commands; do not infer successful production deploy                 |
| Kubernetes            | `infra/k8s/base`, `infra/k8s/overlays`, `scripts/k8s-validate.*`                                            | Manifests/validation exist; production install and recovery unproven                                  |
| Observability         | Compose optional profile; Grafana, Prometheus, Loki/OTel files                                              | Infra capability, not a complete user-facing system-status screen                                     |
| CI/test foundations   | `.github/workflows/`, API/worker Java tests, web Vitest tests                                               | Existing unit/component coverage; `make test-e2e` is a deferred placeholder                           |

## Evidence pointers and previous approved decisions

- `docs/superpowers/specs/2026-07-10-real-ocr-invoice-extraction-design.md`:
  real Tesseract, native-PDF-text-first fallback, provenance, warnings,
  idempotency, metadata-only events and no fabricated values.
- `docs/superpowers/specs/2026-07-29-drive-file-view-authorization-design.md`:
  source-file authorization and related read boundaries.
- `apps/api/src/main/resources/db/migration/V8__invoice_automation_production_workflow.sql`:
  OCR completion currently triggers extraction **and** indexing without
  explicit confirmation.
- `apps/api/src/main/java/com/openecosystem/os/search/SearchService.java`:
  Meilisearch + PostgreSQL fallback with `file:view` result filtering.
- `docs/architecture/EVENTS.md`, `docs/architecture/PERMISSIONS.md`,
  `docs/architecture/OBSERVABILITY.md` and `docs/architecture/DEPLOYMENT.md`
  provide longer-lived architectural contracts.

## Core critical path and review evidence needed

1. Drive full organization: nested folders, durable file placement,
   rename/move, original content preview/download, bounded recursive ZIP
   export, 30-day recoverable Trash, immediate permanent deletion and
   scheduled purge with index suppression and worker-race safety.
2. Independent file-name and raw OCR search projections.
3. Explicit OCR-only and invoice actions from Drive (reuse existing OCR).
4. Invoice draft-edit-confirm lifecycle and confirmed-only search.
5. Ordered image-to-PDF assembly producing a regular encrypted Drive file.
6. Private-stack end-to-end smoke, tests and operating instructions.

Ordering is a **dependency suggestion** for later planning, not authorization
to implement. Reverify inventory against the current branch and fresh test
evidence before marking anything complete.

## MVP Complete, not implemented in Core

Real external-user authentication, onboarding, public-demo isolation/reset,
packaged distribution, backup/restore verification, production exposure
hardening and public product/architecture pages are future Complete work.
New subsystems require their own approved SDD specs. Open Pages, Open Ledger,
advanced PDF editing, plugins and other app expansion remain post-MVP.
