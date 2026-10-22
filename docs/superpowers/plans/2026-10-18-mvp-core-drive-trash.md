# MVP Core Drive Trash and 30-Day Retention Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move files and nested folders to a recoverable Trash for exactly 30 days, support secure restore or immediate permanent deletion, and reliably purge expired content.

**Architecture:** Use transactional logical tombstones and explicit deletion groups for folder subtrees, retaining encrypted objects and OCR/invoice results until purge. All read/search/worker paths fail closed on trashed items; restoration keeps original IDs and re-establishes only permitted indexes. A scheduled idempotent purge separates irreversible database state from retryable S3/search cleanup.

**Tech Stack:** Java 25, Spring Boot 4, JDBC, PostgreSQL 16, H2 for tests, Flyway, AWS SDK S3, Spring Scheduling, JUnit 5/Testcontainers, Next.js 16, TanStack Query, TypeScript 6, Vitest 4.

**Spec:** [Drive Trash and retention design](../specs/2026-10-18-mvp-core-drive-trash-design.md), [Drive folder design](../specs/2026-10-18-mvp-core-drive-design.md), [Search design](../specs/2026-10-18-mvp-core-search-design.md). **Prerequisite plan:** [Drive organization](2026-10-18-mvp-core-drive-organization.md), whose V10 folder model and read services must already be implemented and verified.

**Status:** Execution approved 2026-10-22; inline execution on isolated `feat/drive-trash-retention` from `develop`. Tasks 1–7 completed and verified. Tasks 5–7 continued on `feat/drive-trash-completion` from Task 4 commit `b5b077a`.

**Spec impact:** No spec change. The review revisions below make the approved workflow invisibility, durable purge, search cleanup, explicit restore destination and recursive Trash confirmation requirements executable and testable.

## Global Constraints

- The user-approved rule is **Move to Trash → 30 days recoverable → automatic permanent purge**, plus immediate **Delete permanently** from Trash with explicit confirmation. Thirty days means 30 × 24 hours measured in UTC from the original deletion instant, not calendar dates or daily-job execution time.
- From the instant of Trash, deny normal file/folder listing, preview/download, ZIP, OCR/invoice view, all search results and workflow source access. Retain encrypted content and related OCR/invoice state for restore. A canceled/restored item must not be erased by a stale purge worker.
- Restore preserves original IDs, bytes, folder structure, OCR provenance and invoice review state; only **confirmed** structured invoice fields may be reindexed. Independently previously-trashed descendants preserve their own deadlines and must not be resurrected.
- Permanent delete makes an item non-restorable immediately and starts physical cleanup; report physical erasure only when S3 and derived-data cleanup completes. Retry failures without extending access or touching unrelated objects.
- Recover `PURGING` work after failures and process restarts even when its original 30-day deadline is still in the future. Database lifecycle checks must share a transactional fence with Trash/purge; remote index writes require separate durable ordering/reconciliation before cleanup can be declared complete.
- Add forward-only `V11__drive_trash_lifecycle.sql`; do not rewrite V1–V10. Avoid plaintext filenames, OCR text and invoice values in audit, logs, events or retention diagnostics. Support workspace ownership/membership checks, no new public authentication.
- Retention concerns active workspace storage, not external operator backup archives (MVP Complete).
- Use injected `Clock` and deterministic test time. Reuse existing Spring `@EnableScheduling`, audit, outbox, Drive storage and authorization boundaries. No new standalone cleanup service/deployment.
- Each task begins with a failing test, verifies red/green, and ends in a scoped conventional commit. Never start implementation or push until the plan and execution approach are approved.

## File and interface map

- Lifecycle state: create `apps/api/src/main/resources/db/migration/V11__drive_trash_lifecycle.sql`, `drive/DriveTrashGroup.java`, `DriveTrashRepository.java`, `DriveTrashService.java`, `DriveRestoreDestination.java`. Modify `DriveFolderRepository.java`, `DriveFileRepository.java` and their metadata DTOs; model original folder/location, deletion-group membership, cleanup checkpoints, retry scheduling and recoverable claims.
- HTTP: create `drive/DriveTrashController.java`, `DriveTrashResponse.java`. Modify `DriveFileController.java`, `DriveFolderController.java` for Move to Trash actions.
- Read/access exclusion: modify `drive/FileViewAuthorizationRepository.java`, `common/security/DefaultResourceAuthorizationService.java` as necessary, `drive/DriveFileContentService.java`, folder listings, `media/OcrJobQueryService.java`, `search/SearchService.java` and search projection/indexing helpers.
- Worker race fence: modify `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/DriveOcrMetadataRepository.java`, `ocr/OcrJobProcessor.java` and relevant search-indexing repository/processor, plus focused tests. Confirm actual worker filenames/signatures before editing.
- API event/source fence: modify `media/FileUploadedOcrJobService.java`, `flows/OcrCompletedWorkflowTriggerService.java`, `flows/WorkflowRunner.java` and their focused tests. These consumers create jobs and read retained OCR data independently of the OCR/index worker.
- Remote search lifecycle: extend worker `search/SearchIndexClient.java`, `MeilisearchIndexClient.java`, indexing/lifecycle consumers and durable projection state for ordered or reconciled upsert/removal. Persist metadata-only lifecycle revisions and outstanding work; define how remote cleanup completion is established.
- Durable cleanup: create `drive/DriveTrashPurgeService.java` and scheduler `DriveTrashPurgeScheduler.java`; modify `drive/storage/FileObjectStorage.java`, `S3FileObjectStorage.java` to add **strict** object deletion (existing best-effort upload rollback stays separate). Reuse OCR/invoice repositories and their FK deletion rules.
- UI: modify `apps/web/src/lib/drive-api.ts`, `features/drive/drive-file-actions.tsx`, `drive-folder-actions.tsx` and `drive-screen.tsx`; create `features/drive/drive-trash-screen.tsx`, `use-drive-trash.ts` and `apps/web/src/app/app/drive/trash/page.tsx`, plus Vitest tests.

## Review Focus

1. A file was trashed independently before its parent folder was trashed → restoring or purging parent must not reset or erase the independent item (**Tasks 1, 3, 5**).
2. Expiry boundary exactly 30 × 24 hours, including DST changes and paused scheduler → restore rejected from the deadline regardless of actual S3 cleanup (**Tasks 1, 3, 5**).
3. OCR/indexing event begins before deletion and finishes afterward → no leaked preview, raw OCR or invoice field in search (**Tasks 2, 4**).
4. Concurrent restore and purge claims → exactly one wins and object bytes cannot disappear after a successful restore (**Tasks 3, 5**).
5. Cleanup S3 transient failure, or restoration into a folder with missing parent/colliding name → durable retries and meaningful conflict without lost data (**Tasks 3, 5**).
6. A delayed `FileUploaded`/`OcrCompleted` reaches API consumers after Trash or purge → no new job, retained-source extraction or search projection; **Task 4**.
7. Manual purge fails or the process exits before expiry → scheduled recovery resumes `PURGING` without a new user request; **Task 5**.
8. An in-flight remote index upsert completes after removal → reconcile it before declaring physical cleanup complete; **Tasks 4, 5**.
9. Original parent is unavailable → an explicit root destination is distinct from an omitted destination; non-empty folder Trash requires confirmation; **Tasks 3, 6**.

---

### Task 1: Persist deletion groups and time-based lifecycle state

**Files:** Create `V11__drive_trash_lifecycle.sql`, `DriveTrashGroup.java`, `DriveTrashRepository.java`; modify `DriveFileRepository.java`, `DriveFolderRepository.java` and metadata projections; test `DriveTrashRepositoryTest.java` and PostgreSQL migration tests.

**Interfaces:**

- `DriveTrashGroup(String groupId, String workspaceId, String rootItemType, String rootItemId, String originalParentFolderId, String actorId, Instant trashedAt, Instant purgeAfter, String state)`; state `TRASHED`, `PURGING`, `PURGED`, `RESTORED` (model in a typed enum).
- `DriveTrashRepository.createGroup(DriveTrashGroup group)`, `findVisibleGroups(String workspaceId)`, `findByIdForWorkspace(String groupId, String workspaceId)`, `lockGroup(String groupId)`, `claimCleanupWork(Instant now, int maxGroups)`; file/folder rows gain nullable deletion-group ID and original placement metadata. Record **only active descendants** as group members. Cleanup claims include newly expired `TRASHED` groups and due or abandoned `PURGING` groups regardless of their original expiry. Persist per-phase progress, next retry time and a bounded lease/claim token; reject stale claim completion.
- `DriveTrashClock` or injectable `Clock` returns UTC instants. `purgeAfter = trashedAt.plus(Duration.ofDays(30))`, with restore eligibility `now.isBefore(purgeAfter)`.

- [x] **Step 1: Write RED tests** `fileAndFolderGroupPersistWithoutPlaintext`, `retentionIs720HoursAcrossDst`, `independentlyTrashedChildKeepsOwnDeadline`, `V10FilesMigrateActiveWithoutGroups`, `claimsDuePurgingGroupBeforeOriginalExpiry`, `expiredClaimCanBeRecoveredButStaleOwnerCannotComplete`. Assert H2 and PostgreSQL schema consistency, group uniqueness and no mixing workspaces.
- [x] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveTrashRepositoryTest test`, expected missing schema/type.
- [x] **Step 3: Implement:** add group+member records, workspace-scoped FK/indexes and lifecycle flags; lock rows consistently in group/item ID order. Avoid broad ON DELETE CASCADE that would erase separate previously trashed descendants. Use the existing workspace mutation lock.
- [x] **Step 4: Verify GREEN:** same tests and targeted existing DB/migration tests, including Testcontainers PostgreSQL.
- [x] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test && git commit -m "feat(drive): persist Trash groups and expiry metadata"`.

### Task 2: Move files and folder trees to Trash, immediately hiding descendants

**Files:** Create `drive/DriveTrashService.java`, `DriveTrashController.java`; modify `DriveFileController.java`, `DriveFolderController.java`, `FileViewAuthorizationRepository.java`, `DriveFileContentService.java`, folder listing repositories; test `DriveTrashControllerTest.java`, `FileViewAuthorizationRepositoryTest.java` and file download tests.

**Interfaces:**

- `DriveTrashService.trashFile(String fileId): DriveTrashGroup`; `trashFolder(String folderId): DriveTrashGroup`. HTTP `POST /api/drive/files/{fileId}/trash` and `POST /api/drive/folders/{folderId}/trash`; return `groupId, trashedAt, purgeAfter`, never names in audit/event payloads.
- Shared `DriveTrashRepository.isActiveFile(String workspaceId, String fileId, Instant now): boolean` and `isActiveFolder(String workspaceId, String folderId, Instant now): boolean` account for **any trashed ancestor**, not just the item's own flag. All file/view authorization SQL and directory-list reads must apply this active predicate.

- [x] **Step 1: Write RED tests** `trashFileHidesDownloadAndMetadataButRetainsCiphertext`, `trashFolderHidesWholeSubtreeAndZip`, `oldSearchHitFilteredAfterTrash`, `viewerCannotTrashOwnedByAnother`, `concurrentTrashIsIdempotent`.
- [x] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveTrashControllerTest,FileViewAuthorizationRepositoryTest test`.
- [x] **Step 3: Implement:** one database transaction creates a group, snapshots active descendants and marks them inaccessible. Check owner/mutation permission for the selected item and every affected descendant; reject the whole folder operation if any fails. Extend current file-view single and batch SQL to exclude trashed/expired ancestors **before** response serialization; do not delete encrypted bytes or OCR/invoice rows.
- [x] **Step 4: Verify GREEN:** same tests plus `DriveFileControllerTest` and `SearchServiceTest`; assert denial occurs even if Meilisearch retains old hit.
- [x] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test && git commit -m "feat(drive): move files and folders into recoverable Trash"`.

### Task 3: List Trash and restore a file or original folder tree

**Files:** Modify `DriveTrashService.java`, `DriveTrashRepository.java`, `DriveTrashController.java`; create `DriveTrashResponse.java`; test `DriveTrashControllerTest.java` and `DriveTrashRepositoryTest.java`.

**Interfaces:** `DriveTrashService.listTrash(): List<DriveTrashResponse>`; `restore(String groupId, DriveRestoreDestination destination): DriveTrashResponse`. `DriveRestoreDestination(boolean supplied, String folderId)` preserves request-field presence: omitted body/field means original parent, explicit `{destinationFolderId: null}` means root, and a supplied string means that folder. Default to the original parent only if it is still active; when unavailable, omission returns a destination-required error and explicit root remains valid if authorized. HTTP `GET /api/drive/trash`, `POST /api/drive/trash/{groupId}/restore`; do not collapse omitted and explicit null during request binding.

- [x] **Step 1: Write RED tests** `restoreBeforeDeadlinePreservesBytesAndIds`, `restoreRejectsAtExactDeadline`, `restoreMissingParentRequiresExplicitDestination`, `restoreExplicitNullToRootWhenOriginalParentUnavailable`, `restoreOmittedDestinationUsesActiveOriginalParent`, `restoreDuplicateSiblingConflictsNotOverwritten`, `folderRestoreDoesNotRestoreIndependentlyTrashedChild`, `unauthorizedTrashGroupNotEnumerated`.
- [x] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveTrashControllerTest,DriveTrashRepositoryTest test`.
- [x] **Step 3: Implement:** row-lock group, preserve explicit destination intent, recheck lifecycle and destination ownership, enforce normalized sibling-name conflict policy, atomically clear group membership only for its snapshot; retain original file IDs and encrypted object keys. Emit a metadata-only restored event for current/later search reindexing; never auto-confirm invoice drafts.
- [x] **Step 4: Verify GREEN:** same tests; add an idempotent duplicate-restore test and ensure no index duplication.
- [x] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test && git commit -m "feat(drive): restore Trash files and folder trees"`.

### Task 4: Fence worker and search paths against stale events

**Execution checkpoint (2026-10-22):** Database/event fences are in commit
`3494674`. The remaining slice adds forward-only V12 lifecycle/operation state,
metadata-only `DriveFileLifecycleChanged` events with a registered worker consumer,
asynchronous Meilisearch task tracking, paginated recovery of lost acknowledgements,
ordered removal/restoration, exact-revision cleanup proof, and automatic pipeline
resumption in the restore transaction. Invoice drafts are excluded in both search
backends. Tests cover HTTP recovery, real PostgreSQL concurrency/restart ordering
and actual Meilisearch task lookup/removal. Unknown submissions never count as
cleanup and are not blindly resubmitted; an undiscoverable request requires
operator investigation as documented in `DEPLOYMENT.md`. New filename/raw-OCR
source creation and invoice confirmation remain the separate Search/Invoice plans.
**Files:** Modify API `media/FileUploadedOcrJobService.java`, `flows/OcrCompletedWorkflowTriggerService.java`, `flows/WorkflowRunner.java`, `search/SearchService.java` and Search indexing-producing code as appropriate; worker `ocr/DriveOcrMetadataRepository.java`, its JDBC implementation, `ocr/OcrJobProcessor.java`, `search/SearchDocumentRepository.java`, `search/SearchIndexingProcessor.java`, `search/SearchIndexClient.java`, `search/MeilisearchIndexClient.java` and lifecycle consumers. Update `FileViewAuthorizationRepository.java`, durable lifecycle/projection state and focused API/worker tests. Create API `flows/OcrCompletedWorkflowTriggerServiceTest.java`, `flows/WorkflowRunnerTest.java`, `drive/DriveLifecycleFencePostgresTest.java` and a worker lifecycle-consumer test alongside its new consumer; retain the existing `FileUploadedOcrJobServiceTest`. Locate exact worker types before edits. Implement the database/event fence and remote-index reconciliation as sequential verified slices under one owner.

**Interfaces:** Reuse `DriveTrashRepository.isActiveFile` semantics through equivalent worker database query, with source file ID/workspace; user-facing Search always invokes active-aware `file:view` authorization. Metadata-only events for trash/restore/purge drive idempotent remote-index removal/restoration; never index candidate invoice fields before explicit confirmation.

Extend the search client with removal and cleanup-outcome handling rather than reusing its current upsert-only contract. Define durable per-source desired state and lifecycle revisions, event version/idempotency keys, consumers and retry/DLQ behavior. Event payloads contain IDs/revision/status only. Specify how stale or ambiguously completed remote requests are ordered or reconciled; a revision stored only in PostgreSQL cannot fence a remote write by itself. Expose durable cleanup completion for a specific file/workspace/lifecycle revision to the purge service. Test this reconciliation contract independently using persisted tombstones and a controlled search client before integrating permanent deletion.

- [x] **Step 1: Write RED tests** `ocrJobQueuedBeforeTrashCannotExposeCompletedResult`, `delayedFileUploadedAfterTrashOrPurgeCannotCreateJob`, `delayedOcrCompletedAfterTrashCannotReadRetainedSourceOrRunWorkflow`, `workflowCommitRacingWithTrashIsFenced`, `searchIndexingRequestedBeforeTrashCannotResurrectHit`, `lateRemoteUpsertAfterRemovalIsReconciledBeforeCleanupCompletes`, `staleLifecycleRemovalCannotEraseRestoredProjection`, `folderTrashFiltersRawOcrAndConfirmedInvoiceResults`, `restoreReindexesOnlyAllowedSources`; fake delayed queues and Meilisearch operations with deterministic barriers. Assert denied API consumers never load rich OCR content or create extraction/search side effects; use PostgreSQL for transaction-fence races. Seed lifecycle states directly for this task; the permanent-deletion integration belongs to the later purge task.
- [x] **Step 2: Verify RED:** `cd apps/worker && ./mvnw -Dtest=OcrJobProcessorTest,SearchIndexingProcessorTest test` and `cd apps/api && ./mvnw -Dtest=FileUploadedOcrJobServiceTest,OcrCompletedWorkflowTriggerServiceTest,WorkflowRunnerTest,SearchServiceTest,OcrJobControllerTest test`; add focused PostgreSQL fence and lifecycle-consumer test classes to the selectors when created.
- [x] **Step 3: Implement database/event fencing:** revalidate active-file predicates before work claim and after expensive processing/before persistence. Make the final check and derived writes atomic against the same source/workspace mutation fence used by Trash/purge; do not leave a check-then-write race. Apply this to API job creation, `OcrCompleted` triggering/source reads and `WorkflowRunner` persistence, as well as worker completion/index claims. Define idempotent skipped-event handling for trashed/missing/purged sources and how any intentionally deferred work resumes after restoration. API remains the final authorization gate.
- [x] **Step 4: Implement remote lifecycle reconciliation:** remove/reindex **existing** projections with metadata-only lifecycle events that have a registered consumer; do not invent unconsumed events. Coordinate index writes/removal and recover outstanding work after restart so an old upsert cannot survive a completed purge, while stale removal cannot delete a restored projection. Prove remote absence before Task 5 advertises physical cleanup. Restore filename/raw OCR/confirmed invoice fields only if those source projections already exist; existing pre-confirmation invoice projections are not proof of confirmation and must not become searchable on restore. Otherwise explicitly record the dependency on the separately scoped Search plan.
- [x] **Step 5: Verify GREEN:** repeat both API/worker scoped suites, PostgreSQL concurrency tests and lifecycle-consumer/replay/restart tests; delayed outbox cannot make trashed data visible or recreate a remote copy after cleanup is reported complete.
- [x] **Step 6: Commit:** `git add apps/api/src apps/worker/src && git commit -m "fix(drive): fence trashed content from workers and search"`.

### Task 5: Immediate permanent deletion and automatic 30-day purge

**Files:** Create `DriveTrashPurgeService.java`, `DriveTrashPurgeScheduler.java`; modify `DriveTrashController.java`, `DriveTrashRepository.java`, `FileObjectStorage.java`, `S3FileObjectStorage.java`, `application.yml`, `.env.example`; test `DriveTrashPurgeServiceTest.java`, `DriveTrashControllerTest.java`.

**Interfaces:** `DriveTrashPurgeService.purgeNow(String groupId): PurgeResult` and `runCleanup(Instant now, int batchSize): int`, using Task 1's `claimCleanupWork` for both expiry and recovery of in-progress manual purges. `FileObjectStorage.deleteEncryptedObject(String storageKey): void` throws on failure (unlike existing `deleteObjectIfExists` rollback method). HTTP `POST /api/drive/trash/{groupId}/purge`, returning requested/started vs fully completed state accurately. `DriveTrashPurgeScheduler.runPurge()` is a Spring scheduled method using `openecosystem.drive.trash.purge-poll-ms` (default **3600000**, hourly), batch size default 25; due retries use persisted backoff and never wait for the original retention deadline.

**Prerequisite:** Task 4's database fences and remote cleanup/reconciliation contract must be verified before implementing permanent deletion. `purgeNow` initiates its first cleanup attempt immediately after the durable claim, without waiting for the next scheduled poll.

- [x] **Step 1: Write RED tests** `manualPurgeImmediatelyPreventsRestore`, `expiryBlocksRestoreWhileSchedulerPaused`, `failedS3DeleteRetriedWithoutLosingTombstone`, `failedManualPurgeRetriesBeforeThirtyDayDeadline`, `restartAfterPurgeClaimResumesCleanup`, `restartAfterObjectDeleteResumesRemainingPhases`, `purgeClaimRacesWithRestoreAndOnlyOneWins`, `purgingFolderDoesNotErasePriorSeparateTrashGroup`, `duplicatePurgeNeverDeletesUnrelatedObject`, `purgeWaitsForLateIndexWriteReconciliation`.
- [x] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveTrashPurgeServiceTest,DriveTrashControllerTest test`.
- [x] **Step 3: Implement:** claim group under transaction before irreversible work, persist cleanup progress for idempotent retries, strictly delete encrypted objects and associated OCR/invoice/search projections in FK-safe order, then mark purged. Recover due `PURGING` groups and abandoned claims after restart independently of `purgeAfter`; checkpoint each cleanup phase and use bounded batches/backoff. Coordinate remote search cleanup with Task 4: a delete request being sent, or a database active check passing, is not proof that an older in-flight upsert cannot recreate a remote copy. Keep `PURGING` until outstanding writes are drained or durably reconciled and remote removal is confirmed; retain the identifiers needed to retry. If independent previously-trashed children reference a purged parent, detach their placement safely without resetting their expiry. Ensure cleanup errors do not advertise physical erasure; never delete a restored object after race resolution.
- [x] **Step 4: Verify GREEN:** same scoped tests, then scheduled-clock integration test with 30-day deadline; audit contains IDs/status only.
- [x] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test .env.example && git commit -m "feat(drive): purge Trash explicitly or after 30 days"`.

### Task 6: Accessible Trash view and recovery controls

**Files:** Create `apps/web/src/features/drive/drive-trash-screen.tsx`, `use-drive-trash.ts`, `apps/web/src/app/app/drive/trash/page.tsx`, matching `*.test.tsx`; modify `apps/web/src/lib/drive-api.ts`, `features/drive/drive-file-actions.tsx`, `drive-folder-actions.tsx`, `drive-screen.tsx`.

**Interfaces:** `fetchDriveTrash(): Promise<DriveTrashResponse[]>`, `trashDriveFile(fileId: string)`, `trashDriveFolder(folderId: string)`, `restoreDriveTrashItem(groupId: string, destinationFolderId?: string | null)`, `permanentlyDeleteDriveTrashItem(groupId: string)`. Serialize `undefined` as an omitted destination and `null` as an explicit root destination. TanStack key `["drive-trash"]` and invalidation of affected folder contents.

- [x] **Step 1: Write RED UI tests** `trashActionHidesFileWithUndoNotPermanentWarning`, `nonEmptyFolderTrashExplainsRecursiveImpact`, `cancelFolderTrashMakesNoRequest`, `confirmFolderTrashSubmitsOnce`, `trashShowsDeletionAndPurgeDates`, `restoreFolderIntoChosenDestination`, `restoreToRootSendsExplicitNull`, `permanentDeleteRequiresExplicitConfirmation`, `failedPurgeShowsStillProcessing`, `responsiveKeyboardAccessibleTrashActions`.
- [x] **Step 2: Verify RED:** `cd apps/web && pnpm test -- drive-trash`, expected missing screen/actions.
- [x] **Step 3: Implement:** separate Drive Trash route, normal-item Move to Trash action, restore and permanent-delete dialogs. Before trashing a non-empty folder, require a keyboard-accessible confirmation explaining that all active nested contents will be hidden together and recoverable for 30 days; Cancel sends no mutation, and confirm prevents duplicate submissions. This recursive warning is separate from irreversible permanent-delete confirmation. Provide an explicit root option in the restore picker using Task 3's presence-aware contract. Show UTC-derived remaining retention, expiry and appropriate error/purging states; no client-only deletion timer. Revalidate/refresh after every mutation, keep existing uploader/folder browsing accessible.
- [x] **Step 4: Verify GREEN:** `cd apps/web && pnpm test -- drive && pnpm lint && pnpm typecheck && pnpm build`.
- [x] **Step 5: Commit:** `git add apps/web/src && git commit -m "feat(drive): restore and purge items from Trash UI"`.

### Task 7: Acceptance tests and retention operations guide

**Files:** Create `apps/api/src/test/java/com/openecosystem/os/drive/DriveTrashJourneyTest.java`; modify `docs/development/TEST_COMMANDS.md` and `docs/architecture/DEPLOYMENT.md` for backup/expiry clarification.

**Interfaces:** No new public methods; validate the task contracts end to end.

- [x] **Step 1: Write RED scenario** `nestedFolderTrashRestoreExpiryAndManualPurgeJourney`: upload folder tree, run OCR, trash it, verify denial from Drive/Search/API, restore before deadline with stable bytes/IDs, trash again, purge immediately, verify non-restorable + safe cleanup. Add same-file separate trash and parent purge case, explicit restore-to-root after parent loss, delayed API event delivery, recovery of a failed manual purge across restart before expiry, and late remote upsert/removal interleavings.
- [x] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveTrashJourneyTest test`.
- [x] **Step 3: Address discovered integration issues** and document how to inspect cleanup retry/error state, claim leases, next attempts and remote reconciliation completion without seeing filenames or extracted content. Document that both API and worker lifecycle fences must be deployed together; an older binary that ignores tombstones is not a safe ordinary rollback after Trash is used.
- [x] **Step 4: Verify GREEN:** `cd apps/api && ./mvnw test && ./mvnw verify`; `cd apps/worker && ./mvnw test && ./mvnw verify`; `cd apps/web && pnpm test && pnpm lint && pnpm typecheck && pnpm build`. Run a private Compose end-to-end smoke; report any not-run external gates honestly.
- [x] **Step 5: Commit:** `git add apps/api/src/test docs && git commit -m "test(drive): verify Trash restoration and expiry guarantees"`.

## Self-review and handoff

Reviewers must confirm state-machine correctness at the 30-day boundary, folder group separation, ACL authorization, API-consumer and worker transaction fences, recovery of unexpired manual purges after restart, ordered/reconciled remote cleanup, strict S3 deletion, explicit root restoration, recursive Trash confirmation and restoration idempotency. Check these approved requirements against the named tests and implementation in both directions. The independent Search workstream must still implement filename/raw-OCR/confirmed-invoice source projections to complete the entire MVP Core; this plan owns Trash gating and cleanup/recovery of existing projections, without introducing those new source classes or treating draft invoice fields as confirmed.

After approval and execution-method selection, execute on an isolated worktree/branch under Superpowers with task-level tests, review and commits. No automatic production changes are implied by this document.

## Execution record — 2026-10-22

Task 1 adds forward-only V11, typed Trash state, nullable file/folder membership
and original placement, exact 720-hour expiry, workspace-scoped group queries,
cleanup checkpoints, bounded leases, retry scheduling and token-fenced completion.
Legacy metadata constructors remain available for active uploads and existing
consumers; lifecycle projections are populated on reads. No public Trash endpoint,
worker fence, physical cleanup or frontend action is enabled by this task.

The eight repository contract tests run on both H2 and PostgreSQL 16; PostgreSQL
also verifies concurrent cleanup claims using independent connections. RED runs
showed missing types, the calendar-day/DST constraint defect, and late membership
acceptance after a purge claim. GREEN: 32 focused tests and full API offline
`verify` (236 tests, zero failures/errors/skips, Spotless and packaging).

Spec impact: **no spec change**; this implements the approved persistence contract.
Ruling: adapt the skill's Bash bookkeeping scripts to PowerShell with the same
plan-scoped ignored ledger because this repository requires Windows commands.
The new schema's duration constraint uses `interval '720' hour`, since PostgreSQL
calendar-day arithmetic depends on the session timezone across DST.

Task 2 implements `POST /api/drive/files/{fileId}/trash` and
`POST /api/drive/folders/{folderId}/trash`, returning group ID and UTC dates.
The existing active workspace member must own every affected item; a denied
subtree rolls back completely. Concurrent repeats return the same group without
resetting retention. Independent prior groups are excluded from the snapshot.
Audit attributes contain only group ID and status. Ciphertext and derived rows
are retained. No unconsumed lifecycle event is introduced.

A shared recursive active-folder SQL predicate gates normal folder/file listing,
single/batch source-file authorization and ZIP root access. Trashed ancestors
fail closed even when a descendant's own marker is absent; Drive mutation/upload
into hidden folders is rejected. Search inherits this gate for both remote hits
and PostgreSQL fallback; HTTP tests use a controlled local search server and
persisted synthetic projections. H2 and PostgreSQL test the ancestor predicate.

Task 2 RED: six HTTP regressions failed on missing Trash operations; existing
folder tests caught a root-listing regression during implementation. GREEN:
API offline `verify` passed 246 tests, zero failures/errors/skips, including
PostgreSQL migrations and concurrent claims, Spotless and packaging. Existing
archive unit fixtures now explicitly model their valid folders as active.

Spec impact: **no spec change**. API-only slice; no UI/components, frontend route,
worker behavior or event contract changed. Tasks 3–7 still own restore, worker
and workflow fences, durable remote search cleanup, purge, UI and acceptance.
Do not deploy this partial phase as completed Trash/retention functionality.
The referenced `instructions/backend-endpoint-naming.md` is absent in this
checkout; approved plan routes and existing controller conventions were used.

Task 3 implements `GET /api/drive/trash` and
`POST /api/drive/trash/{groupId}/restore`. Listing authorizes group roots before
name decryption; restoration rechecks every member and the destination under the
workspace/group locks. An omitted destination selects the active original parent;
explicit `{destinationFolderId: null}` selects root. An inaccessible original
parent requires an explicit destination. Invalid destination types return 400,
missing/unauthorized groups return 404, and expiry or name collisions return 409.
Restoration clears only the selected group's snapshot and preserves nested IDs,
bytes and independent child groups; duplicate requests do not repeat mutations.

Task 3 RED: six restore/list HTTP scenarios failed on missing endpoints. GREEN:
full API offline `verify` passed 255 tests with zero failures/errors/skips,
Spotless and packaging. After import cleanup, 36 focused HTTP/repository tests
passed, including PostgreSQL restore-versus-purge serialization. Tests cover
one second before expiry, the exact expiry instant, omitted/null/explicit folder
destinations, collision, malformed destination, authorization, duplicate restore,
stable bytes/IDs and independent child Trash groups.

Ruling: introduce restore lifecycle events atomically with Task 4's registered
consumers instead of publishing an unconsumed event in Task 3. This resolves the
plan's conflicting instructions; remote reconciliation and confirmation-aware
reindexing remain explicitly unimplemented until Task 4. Existing source indexes
are still filtered by source-file authorization. This is not a completed Phase 2
or a safe independent production rollout of Trash/retention.

Spec impact: **no spec change**; the approved restore contract is unchanged.
No UI/components, responsive behavior, worker code or event contract changed.
Next execution starts at Task 4's database/event fence, then its separate durable
remote-search reconciliation slice, before any Task 5 physical purge work.

Task 5: implemented strict S3 deletion, immediate purge requests, hourly bounded cleanup, durable retries, lease-fenced checkpoints and FK-safe derived cleanup. Remote lifecycle absence is required before metadata removal; tombstones remain for delayed events. Independent Trash descendants detach from removed parents and retain their deadlines. API offline verify: 283 tests, zero failures/errors/skips; Spotless and package passed. No spec change; approved retention contract. Tasks 6–7 remain.

Task 6: implemented accessible Trash route, UTC dates, original/explicit destination restore, pending purge state, native permanent-delete and recursive-folder confirmations, normal Drive Trash actions and Undo. Reused existing responsive shell/dialogs and design tokens. Web: 146 tests, lint, typecheck and standard production build passed. No spec change; Task 7 acceptance/operations remains.

Task 7 completion — 2026-10-22:

- Added three HTTP acceptance scenarios for nested restore/retained OCR and invoice lineage,
  exact expiry with a paused scheduler, FK-safe cleanup, and outstanding remote operations.
  The composed scenarios initially passed against Tasks 1–6; no artificial RED was introduced.
  The private real-service smoke then found a missing-index removal failure. Its worker regression
  failed RED before the fix and passed GREEN afterward. A failed removal is accepted only after
  the tracked task is terminal and remote absence is independently verified; existing copies
  and uncertain/in-flight operations still block cleanup. This satisfies the existing approved
  absence contract; **no spec change**.
- Forwarded the three existing cleanup settings through Compose. Completed the required
  approximate remaining-retention label from UTC deadline and last query refresh; the API
  remains authoritative and no client deletion timer was introduced. Its UI regression
  failed RED before implementation, then passed.
- Final verification: API offline `spotless:apply verify` **286 tests**; worker offline
  `spotless:apply verify` **92 tests**; web **146 tests**, lint, typecheck and standard production
  build. Zero test failures/errors/skips. Maven verify includes the test lifecycle, so separate
  identical full `test` runs were not repeated.
- Private Compose smoke passed actual PostgreSQL/MinIO/Tesseract/Meilisearch execution,
  nested Trash denial, stable bytes/IDs/OCR and extraction lineage, independent child recovery
  after parent purge, explicit root restore, and eventual permanent cleanup. Database inspection
  confirmed zero remaining OCR/invoice rows or unreconciled remote revisions for the synthetic
  uploads. Only the disposable acceptance project's resources were used.
- Browser checks passed at desktop and 375px mobile breakpoints: recursive warning, Cancel/Escape
  and focus return, Undo, UTC/retention display, explicit-root restore, irreversible warning and
  cancellation, no horizontal overflow, and no captured console errors. Native dialogs and
  existing design tokens/shell were reused; no new visual system or public authentication.
- Self-review traced approved requirements to repository/controller/purge/journey tests and
  PostgreSQL/worker lifecycle tests, and traced changed behavior back to the approved Trash spec.
  No new source event or public contract beyond the planned purge endpoint was introduced.
  Filename/raw-OCR source creation and invoice confirmation remain separate Search/review plans.
  No additional review agent, push, PR, merge or production deployment was performed.

Rulings: continue inline under existing approval; adapt Bash bookkeeping to PowerShell; use an
isolated continuation branch because the original worktree belongs to another chat; accept
already-green composed acceptance scenarios while requiring RED→GREEN for newly found defects;
use Maven verify's included test phase instead of duplicate full test runs. These choices change
execution/bookkeeping only; no approved product requirement was relaxed. No deferred code findings.

### Review remediation — cleanup fairness and renewable leases (2026-10-22)

User requested fixes for the two review findings on `feat/drive-trash-completion`.
This bounded API-only change preserves the approved Trash contract. **Spec impact:
no spec change**; reliable expiry cleanup and retry recovery were already required.

- Regressions first reproduced a retrying 25-group batch starving the 26th expired
  group, a folder exceeding its five-minute object-cleanup lease, and subsequent
  groups losing their leases while waiting for earlier cleanup.
- Order eligible groups by their effective due time, with workspace/group IDs
  only breaking ties. Claim one group immediately before its cleanup begins,
  retaining the existing per-poll cap and backoff.
- Renew only the current unexpired token before each object delete and before
  derived cleanup. Bound strict S3 deletion to one minute including SDK retries,
  below the five-minute lease. Preserve phase checkpoints and stale-owner fences.
- Acceptance: a ten-file synthetic folder taking 45 seconds per object reaches
  `PURGED`; the next group receives a fresh claim; untouched expired work precedes
  retried groups; renewal cannot revive expired or replaced tokens. Repository
  regressions run against both H2 and real PostgreSQL.
- Verification: focused tests went RED before the fix and GREEN afterward
  (**50 tests**). Full offline API `verify` passed **298 tests**, zero
  failures/errors/skips, with Spotless and packaging. Self-review matched both
  fixes and tests to the approved durable-purge contract.
- Updated operational and test guidance. No schema, REST, event, permission,
  frontend or responsive-layout changes. Browser/Compose smoke was not repeated
  for this API scheduling change. No commit, push or PR was requested.

### Independent review remediation: derived cleanup and OCR recovery (2026-10-22)

The user approved fixing both independent review findings. **Spec impact: no
spec change**; durable purge retries and automatic restore recovery already
belong to the approved Trash contract.

- RED: slow derived cleanup rolled back after lease expiry; later failures
  rolled back earlier files; large file/folder groups did not yield; interrupted
  final-attempt OCR remained processing after restore.
- Commit one file or folder cleanup per fenced transaction, at most 100 items per
  claimed run. Keep the purge tombstone and remove folder roots last. Persisted
  member removal checkpoints progress without a schema migration; retries reuse
  the object/search phase checkpoints. Only a transaction holding the workspace
  and group locks continuously may extend its own token after elapsed lease time.
- Requeue interrupted OCR under the restore transaction, clear the old processing
  claim and add one replacement attempt. Repeated restores remain idempotent;
  completed and failed jobs retain their existing policies. Event schemas stay
  unchanged; replacement payloads use the updated persisted attempt budget.
- Regression tests cover restart progress, bounded work, PostgreSQL competing
  claims, stale tokens, final-attempt recovery, workspace/claim isolation,
  duplicate restore budgets and stale provider output rejection.
- No frontend, responsive-layout, REST, permission or schema changes. Verification
  completed: API offline `spotless:apply verify` passed **305 tests**; worker
  offline `verify` passed **93 tests**, both with zero failures/errors/skips,
  Spotless checks and packaging. The first worker run used an ERROR-only logging
  override and failed `OcrTemporaryWorkspaceCleanerTest`'s warning assertion;
  rerunning with normal logging passed without altering that test. Self-review
  checked both fixes against the approved contract and their regression tests.
