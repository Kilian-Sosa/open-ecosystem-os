# MVP Core Drive Organization and File Access Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a durable private Drive with nested folders, file/folder rename and move, secure file preview/download, and bounded whole-folder ZIP exports.

**Architecture:** Extend the existing Spring Boot Drive module and Flyway database, preserving encrypted names, encrypted bytes, stable file IDs and S3 object keys. Use an ID-based folder tree and transactional directory mutation guards; a Next.js folder explorer provides file operations. Recoverable deletion is covered by the dependent Trash plan, never hard deletion here.

**Tech Stack:** Java 25, Spring Boot 4, JDBC, PostgreSQL 16, H2 tests, Flyway, AWS SDK S3, JUnit 5, AssertJ, Next.js 16, React 19, TanStack Query, TypeScript 6, Vitest 4.

**Spec:** [Drive lifecycle](../specs/2026-10-18-mvp-core-drive-design.md) and [MVP milestones](../specs/2026-10-18-mvp-milestones-design.md). Dependent plan: [Drive Trash](2026-10-18-mvp-core-drive-trash.md).

**Status:** Proposed implementation plan for user review; not authorized for execution.

**Spec impact:** No spec change. The review revisions below implement the approved Drive requirements for MIME/signature consistency, corrupt-format rejection and safe preview; product scope and acceptance criteria remain unchanged.

## Global Constraints

- Keep existing GET/POST `/api/drive/files` routes and old file IDs/keys working; omitted folder ID denotes root, including pre-migration files.
- Add forward-only `V10__drive_folders.sql`, never rewrite V1–V9. Verify H2 and PostgreSQL behavior.
- All file and folder names and bytes remain AES-256-GCM encrypted at rest. No plaintext names, ZIP entries, extracted text or object keys in events, logs, audit or errors.
- Existing `FILE + VIEW` policy determines content read access; **active owner** determines mutation permissions. Keep denied and foreign-workspace file reads non-enumerating (404). Folder read/mutation requires active owner in Core, no implicit recursive sharing.
- Rename and move change only metadata, never file IDs, S3 object keys, OCR associations or invoice provenance. Metadata-only audit events identify mutations; the separately scoped Search plan will add source update/reindexing when its consumer exists.
- Existing FileUploaded/OCR flows, seeded-private identity, and sharing tests must keep working. Do not implement configurable Flows, public links, PDF editing, Trash, or cloud OCR in this plan.
- Reuse the Java ZIP library and AWS SDK; impose explicit server-side limits. Use English code comments only.
- Validate upload bytes against the configured supported formats and declared MIME type before storing an object or publishing `FileUploaded`. A MIME allowlist alone is insufficient. Apply the preview safety check to legacy files too; never execute active document content.
- Follow RED → GREEN with a scoped test per task and commit after verification. No push, merge, implementation or plan execution until user approves the plan and execution approach.

## File and interface map

- Schema: create `apps/api/src/main/resources/db/migration/V10__drive_folders.sql`; modify `drive/DriveFileMetadata.java` and `DriveFileRepository.java`; create `drive/DriveFolderMetadata.java` and `DriveFolderRepository.java`.
- Commands: create `drive/DriveDirectoryNamePolicy.java`, `DriveUploadContentValidator.java`, `DriveFolderService.java`, `DriveFolderController.java`, `DriveFolderResponse.java` and `DriveFileMutationService.java`. Modify `DriveUploadService.java`, `DriveFileController.java`, `DriveFileResponse.java`.
- Content: modify `drive/crypto/FileEncryptionService.java`, `drive/storage/FileObjectStorage.java`, `drive/storage/S3FileObjectStorage.java`; add `drive/DriveFileContentService.java` and `DriveFileContent.java`.
- ZIP: add `drive/DriveArchiveService.java`, `DriveArchiveProperties.java` and `DriveZipArchive.java`. Configure in `apps/api/src/main/resources/application.yml` and `.env.example`.
- Web: modify `apps/web/src/lib/drive-api.ts`, `features/drive/use-drive-files.ts`, `features/drive/drive-screen.tsx`; add focused `features/drive/drive-folder-explorer.tsx`, `drive-file-actions.tsx`, `drive-folder-actions.tsx`, `drive-preview.tsx` and Vitest tests.

## Review Focus

1. Two same-parent file/folder names (case or Unicode variants, including concurrent uploads) → conflict without overwrite; cover in **Task 2**.
2. Unauthorized/mismatched workspace or view-only actor tries a mutation or ZIP → denial with no name leakage; **Tasks 2, 4, 5**.
3. Folder moved into itself/descendant → cycle rejected and tree preserved even under concurrency; **Task 4**.
4. Corrupt AES-GCM tag or ZIP name containing ../ / absolute traversal → fail safely with no plaintext/unsafe archive; **Tasks 3, 5**.
5. Too many ZIP files/bytes, or a denied descendant → no incomplete/partially leaked ZIP response; **Task 5**.
6. A declared allowed MIME type contains mismatched or corrupt bytes, including a legacy upload → reject new uploads before persistence and fail preview safely; **Tasks 2, 3**.

---

### Task 1: Folder storage and existing-file migration

**Files:** Create `V10__drive_folders.sql`, `DriveFolderMetadata.java`, `DriveFolderRepository.java`; modify `DriveFileMetadata.java`, `DriveFileRepository.java`; test `DriveFolderRepositoryTest.java`, `DriveFileRepositoryTest.java` and PostgreSQL migration tests.

**Interfaces:** `DriveFolderMetadata(String folderId, String workspaceId, String ownerId, String parentFolderId, String encryptedName, String nameIv, Instant createdAt, Instant updatedAt)`. `DriveFolderRepository.save(DriveFolderMetadata)`, `findByIdForWorkspace(String folderId, String workspaceId)`, `listChildren(String workspaceId, String parentId)`, `lockWorkspaceForMutation(String workspaceId)`. Extend `DriveFileMetadata` with nullable `folderId`, and `DriveFileRepository.listByFolder(String workspaceId, String folderId)`, `updateFolder(String workspaceId, String fileId, String folderId, Instant when)`.

- [ ] **Step 1: Write failing tests** `existingFilesMigrateIntoRoot`, `nestedEmptyFolderSurvivesReload`, `crossWorkspaceFolderReferenceRejected`; assert original files' folder ID null, encrypted names persisted and wrong-workspace relationships refused. Update all affected file-record test constructors.
- [ ] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveFolderRepositoryTest,DriveFileRepositoryTest test`; expected missing table/type.
- [ ] **Step 3: Implement schema/repositories:** `drive_folders` has ID, scoped parent FK, owner, encrypted name+IV, timestamps; nullable `drive_files.folder_id`. Add composite workspace+folder foreign keys so neither files nor folders can reference a folder in another workspace. Use `SELECT ... FOR UPDATE` on the workspace row to serialize folder mutations (also works for root); add child indexes.
- [ ] **Step 4: Verify GREEN:** same tests, plus the existing PostgreSQL Testcontainers migration suite; expect zero failures.
- [ ] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test && git commit -m "feat(drive): persist nested folders and file placement"`.

### Task 2: Folder creation/navigation and upload placement

**Files:** Create `DriveDirectoryNamePolicy.java`, `DriveUploadContentValidator.java`, `DriveFolderService.java`, `DriveFolderController.java`, `DriveFolderResponse.java`; modify `DriveUploadService.java`, `DriveFileController.java`, `DriveFileResponse.java`; test `DriveUploadContentValidatorTest.java`, `DriveFolderControllerTest.java` and `DriveFileControllerTest.java`.

**Interfaces:** `DriveDirectoryNamePolicy.normalize(String name): String` uses Unicode NFC and Locale.ROOT case folding for comparisons (never persist plaintext normalizations); rejects blank, path separators, dot/dot-dot, control characters and >255 chars. `DriveFolderService.createFolder(String name, String parentFolderId): DriveFolderResponse`; `listFolderContents(String folderId): DriveDirectoryListResponse` (folders + files). `DriveUploadService.upload(MultipartFile file, String folderId): DriveFileResponse` with existing one-argument overload delegating to root. Add nullable `folderId` to file DTO.

`DriveUploadContentValidator.validate(byte[] bytes, String declaredContentType): void` applies bounded format-specific validation for every configured allowed type, including PDF, PNG, JPEG, plain text and allowed Office containers. Define the signature/structure checks and safe text policy explicitly; do not classify solely by extension or trust a matching magic prefix on a truncated file. Reuse existing parsers where suitable and justify any new dependency.

- [ ] **Step 1: Write failing API tests** `createsProjectsInvoices2026AndListsImmediateChildren`, `uploadIntoChildNotRoot`, `conflictingFileAndFolderSiblingNamesReject`, `twoConcurrentCreatesCannotDuplicateName`, `foreignFolderReadForbiddenWithoutNameLeak`, `rejectsDeclaredMimeAndSignatureMismatch`, `rejectsCorruptAllowedFormatsBeforeStorage`, `validSupportedFormatsStillUpload`. Use clearly labelled synthetic fixtures and assert rejected uploads create no object, metadata, audit-success record or outbox event. Assert 201, 200, 409 and 404 statuses plus decrypted responses only on allowed reads.
- [ ] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveUploadContentValidatorTest,DriveFolderControllerTest,DriveFileControllerTest test`.
- [ ] **Step 3: Implement:** HTTP `POST /api/drive/folders` body `{name,parentFolderId}`, `GET /api/drive/folders/contents?folderId=...`, `GET /api/drive/folders/{folderId}`, upload multipart optional `folderId`. Validate bounded source bytes before object storage, metadata writes or outbox publication. Under workspace row lock compare decrypted file and folder sibling names before persist. Keep old flat-list API compatible; UI uses new folder-scoped endpoint.
- [ ] **Step 4: Verify GREEN:** same scoped tests and `./mvnw test`; ensure seeded and sharing behavior remain intact.
- [ ] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test && git commit -m "feat(drive): create folders and browse directory contents"`.

### Task 3: Authenticated original file download and preview

**Files:** Modify `FileEncryptionService.java`, `FileObjectStorage.java`, `S3FileObjectStorage.java` and `DriveFileController.java`; create `DriveFileContentService.java` and `DriveFileContent.java`; test `DriveFileContentServiceTest.java` and `DriveFileControllerTest.java` (update its fake object store).

**Interfaces:** `FileObjectStorage.getEncryptedObject(String storageKey, long maxCiphertextBytes): byte[]`, `FileEncryptionService.decryptBytes(byte[] ciphertext, String ivBase64): byte[]` (authenticated AES-GCM), `DriveFileContentService.read(String fileId): DriveFileContent(byte[] bytes, String name, String contentType)`. HTTP `GET /api/drive/files/{fileId}/download` and `/preview`; preview only PDF, PNG, JPEG and text.

- [ ] **Step 1: Write failing tests** `originalDownloadBytesMatchUpload`, `previewDeniesUnsupportedOfficeType`, `legacyMislabeledOrCorruptContentCannotPreview`, `previewCannotExecuteActiveContent`, `viewerWithoutFileViewCannotFetchBytes`, `tamperedCiphertextFailsWithoutLeak`, `oversizedObjectRejectedBeforeBuffering`; assert no-store, nosniff and safe Content-Disposition.
- [ ] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveFileContentServiceTest,DriveFileControllerTest test`.
- [ ] **Step 3: Implement:** authorize file VIEW before loading source metadata/bytes; add bounded S3 GetObject and AES-GCM tag verification, attachment vs inline safe headers and sanitized failures. Reuse Task 2 format validation before previewing legacy bytes, and define a viewer isolation/rendering policy that prevents active document content from executing; headers alone are not that policy. Preserve authenticated original attachment downloads without rewriting bytes. No plaintext object URL or cache.
- [ ] **Step 4: Verify GREEN:** same scoped tests; check byte-for-byte comparison against originals.
- [ ] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test && git commit -m "feat(drive): serve protected original downloads and previews"`.

### Task 4: Metadata-only rename and move

**Files:** Create `DriveFileMutationService.java`; modify `DriveFolderService.java`, `DriveFolderRepository.java`, `DriveFileRepository.java`, folder/file controllers; test `DriveFileMutationServiceTest.java` and `DriveFolderControllerTest.java`.

**Interfaces:** `DriveFileMutationService.renameFile(String fileId, String name): DriveFileResponse`, `moveFile(String fileId, String destinationFolderId): DriveFileResponse`; `DriveFolderService.renameFolder(String folderId, String name): DriveFolderResponse`, `moveFolder(String folderId, String destinationFolderId): DriveFolderResponse`. HTTP PATCH endpoints `/api/drive/files/{fileId}/name|location` and `/api/drive/folders/{folderId}/name|location`.

- [ ] **Step 1: Write failing tests** `renamePreservesObjectKeyFileIdAndOcrLink`, `moveFolderPreservesSubtreeReferences`, `rejectCycleSelfOrDescendant`, `rejectConcurrentCollision`, `viewOnlyUserCannotRenameOrMove`.
- [ ] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveFileMutationServiceTest,DriveFolderControllerTest test`.
- [ ] **Step 3: Implement:** serialize workspace mutations via Task 1 lock; compare normalized encrypted sibling names under transaction; active owner permission required for source/destination; update parent IDs/name ciphertext only, not blobs or child IDs. Record metadata-only audit entries. Do not add an unconsumed event merely for future search reindexing; the separate Search plan will introduce and consume its source-update event atomically.
- [ ] **Step 4: Verify GREEN:** same tests plus `DriveFileRepositoryTest`.
- [ ] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test && git commit -m "feat(drive): rename and move files and folder trees"`.

### Task 5: Bounded recursive folder ZIP download

**Files:** Create `DriveArchiveService.java`, `DriveArchiveProperties.java`, `DriveZipArchive.java`; modify `DriveFolderController.java`, `application.yml`, `.env.example`; test `DriveArchiveServiceTest.java`, `DriveFolderControllerTest.java`.

**Interfaces:** `DriveArchiveService.prepareZip(String folderId): DriveZipArchive`; result is an owned temporary ZIP path and a safe filename with `close()` deleting it. HTTP `GET /api/drive/folders/{folderId}/download`. Defaults: max **1,000 entries, 512 MiB uncompressed, 2-minute preparation**; configurable and enforced server-side.

- [ ] **Step 1: Write failing tests** `zipPreservesSubfoldersEmptyDirsAndExactSourceBytes`, `rejectDotDotAndAbsoluteArchiveEntries`, `rejectAnyDeniedDescendant`, `tooManyFilesOrBytesFailsBeforeResponse`, `failedOrCancelledArchiveCleansTemporaryFiles`.
- [ ] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveArchiveServiceTest,DriveFolderControllerTest test`.
- [ ] **Step 3: Implement:** snapshot/authorize complete subtree including each file VIEW, preflight limits and normalized relative paths before returning a response. Build with `ZipOutputStream` to permission-restricted temp; clean on all paths; stream already-complete ZIP with `finally` close. Never publish partial or plaintext temp files.
- [ ] **Step 4: Verify GREEN:** same tests; verify the ZIP is readable and every entry's decompressed bytes match its source.
- [ ] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test .env.example && git commit -m "feat(drive): export authorized folder trees as ZIP"`.

### Task 6: Drive folder explorer and file actions

**Execution checkpoint (2026-10-21):** Task 6 authorized in chat and implemented on `feat/drive-folder-explorer`, based on `origin/develop` as requested. Tasks 1–5 are available on `feat/drive-folder-zip` and must be integrated before the real API journey can run on this base. Spec impact: **no spec change**; this implements the approved contract. Browser verification rejected a native sandboxed PDF frame, so the UI uses pinned PDF.js canvas rendering with a local worker. Integration acceptance remains Task 7.

**Verification:** 81 frontend tests pass, including 39 Drive tests. Lint, typecheck, formatting and production build pass. Production dependency audit reports no advisories. Edge checks with synthetic HTTP responses cover 1440×1000 desktop and 390×844 mobile navigation, rename, move, escaped-text preview, PDF canvas rendering, keyboard dismissal and focus restoration. No page errors or mobile horizontal overflow were observed. These checks do not prove real backend authorization or ZIP content; Task 7 retains that acceptance gate. PDF previews remain raster images; accessible original documents are available through download. No new API routes, permissions or events were introduced by Task 6.

**Files:** Modify `apps/web/src/lib/drive-api.ts`, `features/drive/use-drive-files.ts`, `features/drive/drive-screen.tsx`, `drive-screen.test.tsx`; create `drive-folder-explorer.tsx`, `drive-file-actions.tsx`, `drive-folder-actions.tsx`, `drive-preview.tsx` and focused Vitest tests in `apps/web/src/features/drive/`.

**Interfaces:** API functions `fetchDriveFolderContents(folderId: string | null)`, `createDriveFolder(name, parentFolderId)`, `renameDriveFile`, `renameDriveFolder`, `moveDriveFile`, `moveDriveFolder`, `downloadDriveFile`, `downloadDriveFolderZip`. TanStack query key `["drive-folder-contents", folderId ?? "root"]`; upload mutation receives `{file: File, folderId: string | null}`.

- [x] **Step 1: Write failing UI tests** `navigatesNestedFoldersWithBreadcrumbs`, `uploadsIntoCurrentFolder`, `renamesAndMovesFileWithoutLosingSelection`, `previewsPdfImageTextByProtectedFetch`, `downloadsFolderZip`, `mobileFolderPickerAndEmptyState`; stub actual network responses, not static state overrides.
- [x] **Step 2: Verify RED:** `cd apps/web && pnpm test -- drive-folder-explorer` then `pnpm test -- drive-screen`.
- [x] **Step 3: Implement:** reuse AppShell/Query and existing design components; folder contents not flat workspace list, blob URLs revoked after preview/download, error/loading/denied states and keyboard-accessible controls. Implement Task 3's safe viewer policy, render plain text as text, and test active-content isolation in the preview component. Scope query invalidation to affected folder and retain existing snapshot test overrides.
- [x] **Step 4: Verify GREEN:** `cd apps/web && pnpm test -- drive && pnpm lint && pnpm typecheck && pnpm build`.
- [x] **Step 5: Commit:** `git add apps/web/src && git commit -m "feat(drive): navigate folders and manage files in the UI"`.

### Task 7: Complete integration acceptance for this stage

**Files:** Create `apps/api/src/test/java/com/openecosystem/os/drive/DriveCoreJourneyTest.java`; modify `docs/development/TEST_COMMANDS.md` and existing API tests as needed.

**Interfaces:** No new APIs; verify Tasks 1–6 together.

- [ ] **Step 1: Write failing integration test** `nestedUploadRenameMoveDownloadZipPreservesBytesAndLineage` and `foreignUserCannotInspectOrMutateSubtree`; assert stable file ID, ciphertext key, ZIP bytes, no unauthorized labels.
- [ ] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveCoreJourneyTest test`.
- [ ] **Step 3: Fix only discovered integration gaps:** document reproducible Compose smoke steps and clearly state **Trash and Search indexing remain separate subsequent work**.
- [ ] **Step 4: Verify GREEN:** `cd apps/api && ./mvnw test && ./mvnw verify`; `cd apps/web && pnpm test && pnpm lint && pnpm typecheck && pnpm build`. Test real ZIP bytes on a private Compose stack or record blockers.
- [ ] **Step 5: Commit:** `git add apps/api/src/test docs/development/TEST_COMMANDS.md && git commit -m "test(drive): verify the encrypted file-management journey"`.

## Self-review and handoff

The seven tasks are deliberately scoped so every implementation/verification gate can be reviewed independently. Confirm approved upload validation and safe preview are covered in both implementation and tests, including legacy files. Full MVP Core still requires the [Trash plan](2026-10-18-mvp-core-drive-trash.md) and later OCR/Search/invoice/image-PDF plans. This plan does not authorize implementation; use an isolated worktree/branch after explicit plan review and execution method selection.
