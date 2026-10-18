# MVP Core Drive Trash and 30-Day Retention Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move files and nested folders to a recoverable Trash for exactly 30 days, support secure restore or immediate permanent deletion, and reliably purge expired content.

**Architecture:** Use transactional logical tombstones and explicit deletion groups for folder subtrees, retaining encrypted objects and OCR/invoice results until purge. All read/search/worker paths fail closed on trashed items; restoration keeps original IDs and re-establishes only permitted indexes. A scheduled idempotent purge separates irreversible database state from retryable S3/search cleanup.

**Tech Stack:** Java 25, Spring Boot 4, JDBC, PostgreSQL 16, H2 for tests, Flyway, AWS SDK S3, Spring Scheduling, JUnit 5/Testcontainers, Next.js 16, TanStack Query, TypeScript 6, Vitest 4.

**Spec:** [Drive Trash and retention design](../specs/2026-10-18-mvp-core-drive-trash-design.md), [Drive folder design](../specs/2026-10-18-mvp-core-drive-design.md), [Search design](../specs/2026-10-18-mvp-core-search-design.md). **Prerequisite plan:** [Drive organization](2026-10-18-mvp-core-drive-organization.md), whose V10 folder model and read services must already be implemented and verified.

**Status:** Proposed implementation plan for user review; not authorized for execution.

## Global Constraints

- The user-approved rule is **Move to Trash → 30 days recoverable → automatic permanent purge**, plus immediate **Delete permanently** from Trash with explicit confirmation. Thirty days means 30 × 24 hours measured in UTC from the original deletion instant, not calendar dates or daily-job execution time.
- From the instant of Trash, deny normal file/folder listing, preview/download, ZIP, OCR/invoice view, all search results and workflow source access. Retain encrypted content and related OCR/invoice state for restore. A canceled/restored item must not be erased by a stale purge worker.
- Restore preserves original IDs, bytes, folder structure, OCR provenance and invoice review state; only **confirmed** structured invoice fields may be reindexed. Independently previously-trashed descendants preserve their own deadlines and must not be resurrected.
- Permanent delete makes an item non-restorable immediately and starts physical cleanup; report physical erasure only when S3 and derived-data cleanup completes. Retry failures without extending access or touching unrelated objects.
- Add forward-only `V11__drive_trash_lifecycle.sql`; do not rewrite V1–V10. Avoid plaintext filenames, OCR text and invoice values in audit, logs, events or retention diagnostics. Support workspace ownership/membership checks, no new public authentication.
- Retention concerns active workspace storage, not external operator backup archives (MVP Complete).
- Use injected `Clock` and deterministic test time. Reuse existing Spring `@EnableScheduling`, audit, outbox, Drive storage and authorization boundaries. No new standalone cleanup service/deployment.
- Each task begins with a failing test, verifies red/green, and ends in a scoped conventional commit. Never start implementation or push until the plan and execution approach are approved.

## File and interface map

- Lifecycle state: create `apps/api/src/main/resources/db/migration/V11__drive_trash_lifecycle.sql`, `drive/DriveTrashGroup.java`, `DriveTrashRepository.java`, `DriveTrashService.java`. Modify `DriveFolderRepository.java`, `DriveFileRepository.java` and their metadata DTOs; model original folder/location and deletion-group membership.
- HTTP: create `drive/DriveTrashController.java`, `DriveTrashResponse.java`. Modify `DriveFileController.java`, `DriveFolderController.java` for Move to Trash actions.
- Read/access exclusion: modify `drive/FileViewAuthorizationRepository.java`, `common/security/DefaultResourceAuthorizationService.java` as necessary, `drive/DriveFileContentService.java`, folder listings, `media/OcrJobQueryService.java`, `search/SearchService.java` and search projection/indexing helpers.
- Worker race fence: modify `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/DriveOcrMetadataRepository.java`, `ocr/OcrJobProcessor.java` and relevant search-indexing repository/processor, plus focused tests. Confirm actual worker filenames/signatures before editing.
- Durable cleanup: create `drive/DriveTrashPurgeService.java` and scheduler `DriveTrashPurgeScheduler.java`; modify `drive/storage/FileObjectStorage.java`, `S3FileObjectStorage.java` to add **strict** object deletion (existing best-effort upload rollback stays separate). Reuse OCR/invoice repositories and their FK deletion rules.
- UI: modify `apps/web/src/lib/drive-api.ts`, `features/drive/drive-file-actions.tsx`, `drive-folder-actions.tsx` and `drive-screen.tsx`; create `features/drive/drive-trash-screen.tsx`, `use-drive-trash.ts` and `apps/web/src/app/app/drive/trash/page.tsx`, plus Vitest tests.

## Review Focus

1. A file was trashed independently before its parent folder was trashed → restoring or purging parent must not reset or erase the independent item (**Tasks 1, 3, 4**).
2. Expiry boundary exactly 30 × 24 hours, including DST changes and paused scheduler → restore rejected from the deadline regardless of actual S3 cleanup (**Tasks 1, 3, 4**).
3. OCR/indexing event begins before deletion and finishes afterward → no leaked preview, raw OCR or invoice field in search (**Tasks 2, 5**).
4. Concurrent restore and purge claims → exactly one wins and object bytes cannot disappear after a successful restore (**Tasks 3, 4**).
5. Cleanup S3 transient failure, or restoration into a folder with missing parent/colliding name → durable retries and meaningful conflict without lost data (**Tasks 3, 4**).

---

### Task 1: Persist deletion groups and time-based lifecycle state

**Files:** Create `V11__drive_trash_lifecycle.sql`, `DriveTrashGroup.java`, `DriveTrashRepository.java`; modify `DriveFileRepository.java`, `DriveFolderRepository.java` and metadata projections; test `DriveTrashRepositoryTest.java` and PostgreSQL migration tests.

**Interfaces:**

- `DriveTrashGroup(String groupId, String workspaceId, String rootItemType, String rootItemId, String originalParentFolderId, String actorId, Instant trashedAt, Instant purgeAfter, String state)`; state `TRASHED`, `PURGING`, `PURGED`, `RESTORED` (model in a typed enum).
- `DriveTrashRepository.createGroup(DriveTrashGroup group)`, `findVisibleGroups(String workspaceId)`, `findByIdForWorkspace(String groupId, String workspaceId)`, `lockGroup(String groupId)`, `claimExpiredGroups(Instant now, int maxGroups)`; file/folder rows gain nullable deletion-group ID and original placement metadata. Record **only active descendants** as group members.
- `DriveTrashClock` or injectable `Clock` returns UTC instants. `purgeAfter = trashedAt.plus(Duration.ofDays(30))`, with restore eligibility `now.isBefore(purgeAfter)`.

- [ ] **Step 1: Write RED tests** `fileAndFolderGroupPersistWithoutPlaintext`, `retentionIs720HoursAcrossDst`, `independentlyTrashedChildKeepsOwnDeadline`, `V10FilesMigrateActiveWithoutGroups`. Assert H2 and PostgreSQL schema consistency, group uniqueness and no mixing workspaces.
- [ ] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveTrashRepositoryTest test`, expected missing schema/type.
- [ ] **Step 3: Implement:** add group+member records, workspace-scoped FK/indexes and lifecycle flags; lock rows consistently in group/item ID order. Avoid broad ON DELETE CASCADE that would erase separate previously trashed descendants. Use the existing workspace mutation lock.
- [ ] **Step 4: Verify GREEN:** same tests and targeted existing DB/migration tests, including Testcontainers PostgreSQL.
- [ ] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test && git commit -m "feat(drive): persist Trash groups and expiry metadata"`.

### Task 2: Move files and folder trees to Trash, immediately hiding descendants

**Files:** Create `drive/DriveTrashService.java`, `DriveTrashController.java`; modify `DriveFileController.java`, `DriveFolderController.java`, `FileViewAuthorizationRepository.java`, `DriveFileContentService.java`, folder listing repositories; test `DriveTrashControllerTest.java`, `FileViewAuthorizationRepositoryTest.java` and file download tests.

**Interfaces:**

- `DriveTrashService.trashFile(String fileId): DriveTrashGroup`; `trashFolder(String folderId): DriveTrashGroup`. HTTP `POST /api/drive/files/{fileId}/trash` and `POST /api/drive/folders/{folderId}/trash`; return `groupId, trashedAt, purgeAfter`, never names in audit/event payloads.
- Shared `DriveTrashRepository.isActiveFile(String workspaceId, String fileId, Instant now): boolean` and `isActiveFolder(String workspaceId, String folderId, Instant now): boolean` account for **any trashed ancestor**, not just the item's own flag. All file/view authorization SQL and directory-list reads must apply this active predicate.

- [ ] **Step 1: Write RED tests** `trashFileHidesDownloadAndMetadataButRetainsCiphertext`, `trashFolderHidesWholeSubtreeAndZip`, `oldSearchHitFilteredAfterTrash`, `viewerCannotTrashOwnedByAnother`, `concurrentTrashIsIdempotent`.
- [ ] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveTrashControllerTest,FileViewAuthorizationRepositoryTest test`.
- [ ] **Step 3: Implement:** one database transaction creates a group, snapshots active descendants and marks them inaccessible. Check owner/mutation permission for the selected item and every affected descendant; reject the whole folder operation if any fails. Extend current file-view single and batch SQL to exclude trashed/expired ancestors **before** response serialization; do not delete encrypted bytes or OCR/invoice rows.
- [ ] **Step 4: Verify GREEN:** same tests plus `DriveFileControllerTest` and `SearchServiceTest`; assert denial occurs even if Meilisearch retains old hit.
- [ ] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test && git commit -m "feat(drive): move files and folders into recoverable Trash"`.

### Task 3: List Trash and restore a file or original folder tree

**Files:** Modify `DriveTrashService.java`, `DriveTrashRepository.java`, `DriveTrashController.java`; create `DriveTrashResponse.java`; test `DriveTrashControllerTest.java` and `DriveTrashRepositoryTest.java`.

**Interfaces:** `DriveTrashService.listTrash(): List<DriveTrashResponse>`; `restore(String groupId, String destinationFolderId): DriveTrashResponse`. HTTP `GET /api/drive/trash`, `POST /api/drive/trash/{groupId}/restore` with optional body `{destinationFolderId}`; default original parent only if it is still active.

- [ ] **Step 1: Write RED tests** `restoreBeforeDeadlinePreservesBytesAndIds`, `restoreRejectsAtExactDeadline`, `restoreMissingParentRequiresExplicitDestination`, `restoreDuplicateSiblingConflictsNotOverwritten`, `folderRestoreDoesNotRestoreIndependentlyTrashedChild`, `unauthorizedTrashGroupNotEnumerated`.
- [ ] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveTrashControllerTest,DriveTrashRepositoryTest test`.
- [ ] **Step 3: Implement:** row-lock group, recheck lifecycle and destination ownership, enforce normalized sibling-name conflict policy, atomically clear group membership only for its snapshot; retain original file IDs and encrypted object keys. Emit a metadata-only restored event for current/later search reindexing; never auto-confirm invoice drafts.
- [ ] **Step 4: Verify GREEN:** same tests; add an idempotent duplicate-restore test and ensure no index duplication.
- [ ] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test && git commit -m "feat(drive): restore Trash files and folder trees"`.

### Task 4: Immediate permanent deletion and automatic 30-day purge

**Files:** Create `DriveTrashPurgeService.java`, `DriveTrashPurgeScheduler.java`; modify `DriveTrashController.java`, `DriveTrashRepository.java`, `FileObjectStorage.java`, `S3FileObjectStorage.java`, `application.yml`, `.env.example`; test `DriveTrashPurgeServiceTest.java`, `DriveTrashControllerTest.java`.

**Interfaces:** `DriveTrashPurgeService.purgeNow(String groupId): PurgeResult` and `purgeExpired(Instant now, int batchSize): int`. `FileObjectStorage.deleteEncryptedObject(String storageKey): void` throws on failure (unlike existing `deleteObjectIfExists` rollback method). HTTP `POST /api/drive/trash/{groupId}/purge`, returning requested/started vs fully completed state accurately. `DriveTrashPurgeScheduler.runPurge()` is a Spring scheduled method using `openecosystem.drive.trash.purge-poll-ms` (default **3600000**, hourly), batch size default 25.

- [ ] **Step 1: Write RED tests** `manualPurgeImmediatelyPreventsRestore`, `expiryBlocksRestoreWhileSchedulerPaused`, `failedS3DeleteRetriedWithoutLosingTombstone`, `purgeClaimRacesWithRestoreAndOnlyOneWins`, `purgingFolderDoesNotErasePriorSeparateTrashGroup`, `duplicatePurgeNeverDeletesUnrelatedObject`.
- [ ] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveTrashPurgeServiceTest,DriveTrashControllerTest test`.
- [ ] **Step 3: Implement:** claim group under transaction before irreversible work, persist cleanup progress for idempotent retries, strictly delete encrypted objects and associated OCR/invoice/search projections in FK-safe order, then mark purged. If independent previously-trashed children reference a purged parent, detach their placement safely without resetting their expiry. Ensure cleanup errors do not advertise physical erasure; never delete a restored object after race resolution.
- [ ] **Step 4: Verify GREEN:** same scoped tests, then scheduled-clock integration test with 30-day deadline; audit contains IDs/status only.
- [ ] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test .env.example && git commit -m "feat(drive): purge Trash explicitly or after 30 days"`.

### Task 5: Fence worker and search paths against stale events

**Files:** Modify API `search/SearchService.java` and Search indexing-producing code as appropriate; worker `ocr/DriveOcrMetadataRepository.java`, `ocr/OcrJobProcessor.java`, `search/SearchDocumentRepository.java`, `search/SearchIndexingProcessor.java`; update `FileViewAuthorizationRepository.java` and focused API/worker tests. Locate exact worker types before edits.

**Interfaces:** Reuse `DriveTrashRepository.isActiveFile` semantics through equivalent worker database query, with source file ID/workspace; user-facing Search always invokes active-aware `file:view` authorization. Metadata-only events for trash/restore/purge drive idempotent remote-index removal/restoration; never index candidate invoice fields before explicit confirmation.

- [ ] **Step 1: Write RED tests** `ocrJobQueuedBeforeTrashCannotExposeCompletedResult`, `searchIndexingRequestedBeforeTrashCannotResurrectHit`, `folderTrashFiltersRawOcrAndConfirmedInvoiceResults`, `restoreReindexesOnlyAllowedSources`; fake delayed queues and Meilisearch results.
- [ ] **Step 2: Verify RED:** `cd apps/worker && ./mvnw -Dtest=OcrJobProcessorTest,SearchIndexingProcessorTest test` and `cd apps/api && ./mvnw -Dtest=SearchServiceTest,OcrJobControllerTest test`.
- [ ] **Step 3: Implement:** revalidate active-file predicates before work claim and after expensive processing/before persistence; enforce active check on worker index claims; API remains final authorization gate. Remove/reindex **existing** projections with metadata-only lifecycle events that have a registered consumer; do not invent unconsumed events. Restore filename/raw OCR/confirmed invoice fields only if those source projections already exist. Otherwise explicitly record the dependency on the separately scoped Search plan.
- [ ] **Step 4: Verify GREEN:** repeat both API/worker scoped suites and event replay tests; delayed outbox cannot make trashed data visible.
- [ ] **Step 5: Commit:** `git add apps/api/src apps/worker/src && git commit -m "fix(drive): fence trashed content from workers and search"`.

### Task 6: Accessible Trash view and recovery controls

**Files:** Create `apps/web/src/features/drive/drive-trash-screen.tsx`, `use-drive-trash.ts`, `apps/web/src/app/app/drive/trash/page.tsx`, matching `*.test.tsx`; modify `apps/web/src/lib/drive-api.ts`, `features/drive/drive-file-actions.tsx`, `drive-folder-actions.tsx`, `drive-screen.tsx`.

**Interfaces:** `fetchDriveTrash(): Promise<DriveTrashResponse[]>`, `trashDriveFile(fileId: string)`, `trashDriveFolder(folderId: string)`, `restoreDriveTrashItem(groupId: string, destinationFolderId?: string)`, `permanentlyDeleteDriveTrashItem(groupId: string)`. TanStack key `["drive-trash"]` and invalidation of affected folder contents.

- [ ] **Step 1: Write RED UI tests** `trashActionHidesFileWithUndoNotPermanentWarning`, `trashShowsDeletionAndPurgeDates`, `restoreFolderIntoChosenDestination`, `permanentDeleteRequiresExplicitConfirmation`, `failedPurgeShowsStillProcessing`, `responsiveKeyboardAccessibleTrashActions`.
- [ ] **Step 2: Verify RED:** `cd apps/web && pnpm test -- drive-trash`, expected missing screen/actions.
- [ ] **Step 3: Implement:** separate Drive Trash route, normal-item Move to Trash action, restore and permanent-delete dialogs; show UTC-derived remaining retention, expiry and appropriate error/purging states; no client-only deletion timer. Revalidate/refresh after every mutation, keep existing uploader/folder browsing accessible.
- [ ] **Step 4: Verify GREEN:** `cd apps/web && pnpm test -- drive && pnpm lint && pnpm typecheck && pnpm build`.
- [ ] **Step 5: Commit:** `git add apps/web/src && git commit -m "feat(drive): restore and purge items from Trash UI"`.

### Task 7: Acceptance tests and retention operations guide

**Files:** Create `apps/api/src/test/java/com/openecosystem/os/drive/DriveTrashJourneyTest.java`; modify `docs/development/TEST_COMMANDS.md` and `docs/architecture/DEPLOYMENT.md` for backup/expiry clarification.

**Interfaces:** No new public methods; validate the task contracts end to end.

- [ ] **Step 1: Write RED scenario** `nestedFolderTrashRestoreExpiryAndManualPurgeJourney`: upload folder tree, run OCR, trash it, verify denial from Drive/Search/API, restore before deadline with stable bytes/IDs, trash again, purge immediately, verify non-restorable + safe cleanup. Add same-file separate trash and parent purge case.
- [ ] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveTrashJourneyTest test`.
- [ ] **Step 3: Address discovered integration issues** and document how to inspect cleanup retry/error state without seeing filenames or extracted content.
- [ ] **Step 4: Verify GREEN:** `cd apps/api && ./mvnw test && ./mvnw verify`; `cd apps/worker && ./mvnw test && ./mvnw verify`; `cd apps/web && pnpm test && pnpm lint && pnpm typecheck && pnpm build`. Run a private Compose end-to-end smoke; report any not-run external gates honestly.
- [ ] **Step 5: Commit:** `git add apps/api/src/test docs && git commit -m "test(drive): verify Trash restoration and expiry guarantees"`.

## Self-review and handoff

Reviewers must confirm state-machine correctness at the 30-day boundary, folder group separation, ACL authorization, search/worker races, strict S3 deletion and restoration idempotency. The independent Search workstream must still implement filename/raw-OCR/confirmed-invoice source projections to complete the entire MVP Core; this plan only enforces Trash gating and metadata-only lifecycle hooks.

After approval and execution-method selection, execute on an isolated worktree/branch under Superpowers with task-level tests, review and commits. No automatic production changes are implied by this document.
