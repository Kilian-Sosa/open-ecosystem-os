# MVP Core Drive Organization and File Access Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a durable private Drive with nested folders, file/folder rename and move, secure file preview/download, and bounded whole-folder ZIP exports.

**Architecture:** Extend the existing Spring Boot Drive module and Flyway database, preserving encrypted names, encrypted bytes, stable file IDs and S3 object keys. Use an ID-based folder tree and transactional directory mutation guards; a Next.js folder explorer provides file operations. Recoverable deletion is covered by the dependent Trash plan, never hard deletion here.

**Tech Stack:** Java 25, Spring Boot 4, JDBC, PostgreSQL 16, H2 tests, Flyway, AWS SDK S3, JUnit 5, AssertJ, Next.js 16, React 19, TanStack Query, TypeScript 6, Vitest 4.

**Spec:** [Drive lifecycle](../specs/2026-10-18-mvp-core-drive-design.md) and [MVP milestones](../specs/2026-10-18-mvp-milestones-design.md). Dependent plan: [Drive Trash](2026-10-18-mvp-core-drive-trash.md).

**Status:** Execution started 2026-10-20 following the user's instruction to start
this plan. Tasks 1–4 are complete; Tasks 5–7 remain
pending.

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
- Follow RED → GREEN with a scoped test per task and commit after verification.
  Execution is authorized by the user's 2026-10-20 instruction. Push and merge
  require separate authorization.

## Execution record — 2026-10-20

- Base: `develop` at `4175be4`; isolated app-managed worktree on
  `feat/drive-organization`. Execute inline with one production-code owner.
- Scope for this run: Task 1, matching the repository's bounded-slice rule.
  Folder APIs, validation, content serving, mutations, ZIP and UI follow in
  Tasks 2–7. No event, API, permission or UI contract changes in Task 1.
- Spec impact: **no spec change**. Implements the approved folder model and
  legacy-root acceptance criterion in the Drive lifecycle spec.
- Pre-flight: Task 1's folder IDs, nullable file placement and workspace lock
  support Tasks 2/4/5. Task 2's content validator supports Tasks 3/6; Task 3's
  protected reads support Tasks 5/6. No shared-interface conflict identified.
- Ruling: use the Windows Maven wrapper and record progress here rather than
  invoking the skill's POSIX bookkeeping scripts. The tracked plan retains the
  execution checkpoint; the cost is manual checkbox maintenance.
- Baseline: existing `DriveFileRepositoryTest` passed (2 tests). RED: new
  repository tests failed compilation on the absent folder types and methods.
- Test-fixture finding: H2 constraint comparisons retained a closed migration
  session with unpooled connections. Keep one connection per isolated H2 test
  and close it after the test; PostgreSQL uses the existing Testcontainers suite.
- Focused GREEN: `mvnw.cmd spotless:apply
-Dtest=DriveFolderRepositoryTest,DriveFileRepositoryTest,RealOcrPostgresMigrationTest
test` passed (13 tests; zero failures/errors/skips), including PostgreSQL 16
  upgrade and transaction-lock coverage.
- Task 1: complete. `mvnw.cmd verify` passed (142 tests; zero
  failures/errors/skips), packaged the API and passed Spotless. Owner self-review
  checked the migration, consumers and test coverage against the approved spec;
  no separate reviewer was launched. API CI exercises these Maven checks.
- Next checkpoint: Task 2. Keep upload placement, sibling-name validation and
  content validation together; Task 1 alone does not expose folder operations.

## Task 2 execution record — 2026-10-20

- User explicitly requested Task 2. Base: Task 1 commit `f8e847c`; isolated
  app-managed worktree on `feat/drive-folder-navigation`. Execute inline and
  retain this tracked execution record instead of POSIX bookkeeping scripts.
- Spec impact: **no spec change**. Folder APIs, upload placement, private
  access, sibling conflicts and content validation implement approved behavior.
- RED: policy/validator tests failed on missing types. GREEN: focused real
  HTTP/database/encryption/authorization tests passed with fake object storage.
  Additional RED/GREEN regressions cover central ZIP checksum disagreement,
  blank upload names and legacy names exceeding the new 255-character limit.
- Rulings: reuse PDFBox 3.0.4 from the worker helper for strict PDF structural
  validation; retain the workspace lock through S3 writes for collision safety;
  compare legacy names without applying new-name validation. The referenced
  endpoint-naming instruction file is absent, so use existing REST conventions.
- Verification: `mvnw.cmd spotless:apply verify` passed, including all 164 API
  tests with zero failures/errors/skips, PostgreSQL Testcontainers checks,
  Spotless and JAR packaging. Docker was already running and remains running.
- Owner self-review checked requirements against tests and implementation,
  including authorization before filename decryption and concurrent uploads.
  No independent reviewer was launched. No UI, schema or worker changes.
- FileUploaded v1 remains unchanged; folder creation writes metadata-only
  audit records without introducing an unconsumed event.
- See [validation policy](../../development/DRIVE_UPLOAD_VALIDATION.md) for the
  API contract, parser limits, dependency rationale and remaining parser
  isolation limitations. Preview safety and legacy preview validation remain
  Task 3; frontend and responsive changes remain Task 6.
- Next checkpoint: Task 3, authenticated original download and safe preview.

## Task 3 execution record — 2026-10-20

- User explicitly requested Task 3. Base: Task 2 commit `c3836a7`; branch
  `feat/drive-protected-content` in the primary checkout. The linked Task 2
  worktree was inaccessible in the sandbox, so continue from its committed
  branch snapshot without changing that worktree. One implementation owner,
  inline execution; retain this tracked checkpoint as in Tasks 1–2.
- Spec impact: **no spec change**. Implements approved original download,
  bounded safe preview and source-file authorization. No frontend, worker,
  migration, event, audit or upload-contract changes.
- Baseline: 11 controller tests passed. RED: the new service/storage tests
  failed compilation on the absent content types and bounded read method.
  GREEN: focused service/controller/storage tests passed. Corrected test
  fixtures to respect paired grant-revocation fields and inspect the PDF
  action dictionary directly (the PDFBox getter creates a wrapper).
- Ruling: rebuild PDF previews as raster-only PDFs with no copied source
  objects, instead of embedding source PDFs behind headers. The cost is loss
  of interactive/text/vector/accessibility features in previews; originals
  remain byte-exact attachments. Task 6 must implement the documented safe
  viewer contexts and fallback. No additional dependency was introduced.
- Ruling: keep a 25 MiB source cap, bounded S3 reads and explicit PDF
  page/pixel/output limits. The 30-second render budget is cooperative and
  PDFBox is not process-isolated; this is bounded private-Core functionality,
  not proof of public adversarial-parser resource isolation. Lowering the
  configured upload limit can also restrict legacy downloads.
- Verification: `mvnw.cmd spotless:apply verify` passed: **181 API tests,
  zero failures/errors/skips**, including PostgreSQL Testcontainers migration
  tests, Spotless and JAR packaging. HTTP tests ran outside the sandbox after
  a sandbox loopback denial. Existing H2/Flyway version and JVM-agent warnings
  remain. Docker was already running and remains running.
- Owner self-review applied the Spring/security checklists and checked
  requirements-to-tests and behavior-to-spec alignment. No independent
  reviewer was launched, per the user's bounded-slice policy. S3 adapter
  tests use controlled SDK streams; no live MinIO/browser proof is claimed.
- See [content and viewer contract](../../development/DRIVE_FILE_CONTENT.md)
  for endpoint statuses, authenticated read limits, preview transformation,
  tradeoffs and Task 6 integration requirements.
- Next checkpoint: Task 4, metadata-only rename and move.

## Task 4 execution record — 2026-10-20

- User explicitly requested Task 4. Base: Task 3 commit `a9432b1`; branch
  `feat/drive-rename-move` in the primary checkout, continuing Task 3's
  checkout arrangement. One production-code owner, inline execution; retain
  this tracked checkpoint instead of POSIX bookkeeping scripts. No push or
  merge is authorized by this task.
- Spec impact: **no spec change**. Implements approved metadata-only
  rename/move, active-owner authorization, normalized sibling conflicts and
  concurrent cycle prevention. Search projections remain explicitly deferred
  to the Search plan. No frontend, worker, schema or event contract changes.
- Baseline: 15 existing folder/controller and file-repository tests passed.
  RED: new mutation tests failed compilation on the missing service/update
  methods. GREEN: focused mutation/controller/repository checks passed.
- Ruling: moves use comparison-only name checks so existing legacy names can
  move without being renamed; new names still use full validation. A regression
  first reproduced the incorrect 400 response, then passed with preserved name
  ciphertext and cross-kind collision checks. This retains Task 2's legacy-name
  compatibility without accepting invalid new names.
- Owner integration review found that CORS blocked PATCH. A preflight regression
  reproduced 403 for the configured frontend origin. Ruling: permit PATCH in
  the existing method allowlist while retaining the origin allowlist; all four
  mutation routes now pass preflight and untrusted origins remain denied.
- Verification: `mvnw.cmd spotless:apply verify` passed: **196 API tests,
  zero failures/errors/skips**, PostgreSQL Testcontainers migration/locking
  checks, Spotless and JAR packaging. Tests requiring HTTP/Docker ran outside
  the sandbox after concrete loopback/pipe access denials. Docker was already
  running and remains running; no user-level service configuration changed.
- Owner self-review applied the Spring/security checklists and checked both
  requirements-to-tests and behavior-to-spec alignment. Tests cover stable IDs,
  keys, content encryption and OCR associations, byte-exact downloads after
  mutation, unchanged descendant rows, source/destination ownership, inactive
  owners, foreign workspaces, Unicode conflicts, concurrent collisions and
  concurrent cycle attempts. No separate reviewer was launched, per the user's
  bounded-slice policy. No live MinIO/browser proof is claimed.
- See [mutation contract](../../development/DRIVE_FILE_MUTATIONS.md) for request
  bodies, statuses, repeat-call behavior, safe audit records and test commands.
  The endpoint-naming instruction file remains absent; routes follow this
  approved plan. Task 4: complete. Next checkpoint: Task 5, bounded folder ZIP.

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

- [x] **Step 1: Write failing tests** `existingFilesMigrateIntoRoot`, `nestedEmptyFolderSurvivesReload`, `crossWorkspaceFolderReferenceRejected`; assert original files' folder ID null, encrypted names persisted and wrong-workspace relationships refused. Update all affected file-record test constructors.
- [x] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveFolderRepositoryTest,DriveFileRepositoryTest test`; expected missing table/type.
- [x] **Step 3: Implement schema/repositories:** `drive_folders` has ID, scoped parent FK, owner, encrypted name+IV, timestamps; nullable `drive_files.folder_id`. Add composite workspace+folder foreign keys so neither files nor folders can reference a folder in another workspace. Use `SELECT ... FOR UPDATE` on the workspace row to serialize folder mutations (also works for root); add child indexes.
- [x] **Step 4: Verify GREEN:** same tests, plus the existing PostgreSQL Testcontainers migration suite; expect zero failures.
- [x] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test && git commit -m "feat(drive): persist nested folders and file placement"`.

### Task 2: Folder creation/navigation and upload placement

**Files:** Create `DriveDirectoryNamePolicy.java`, `DriveUploadContentValidator.java`, `DriveFolderService.java`, `DriveFolderController.java`, `DriveFolderResponse.java`; modify `DriveUploadService.java`, `DriveFileController.java`, `DriveFileResponse.java`; test `DriveUploadContentValidatorTest.java`, `DriveFolderControllerTest.java` and `DriveFileControllerTest.java`.

**Interfaces:** `DriveDirectoryNamePolicy.normalize(String name): String` uses Unicode NFC and Locale.ROOT case folding for comparisons (never persist plaintext normalizations); rejects blank, path separators, dot/dot-dot, control characters and >255 chars. `DriveFolderService.createFolder(String name, String parentFolderId): DriveFolderResponse`; `listFolderContents(String folderId): DriveDirectoryListResponse` (folders + files). `DriveUploadService.upload(MultipartFile file, String folderId): DriveFileResponse` with existing one-argument overload delegating to root. Add nullable `folderId` to file DTO.

`DriveUploadContentValidator.validate(byte[] bytes, String declaredContentType): void` applies bounded format-specific validation for every configured allowed type, including PDF, PNG, JPEG, plain text and allowed Office containers. Define the signature/structure checks and safe text policy explicitly; do not classify solely by extension or trust a matching magic prefix on a truncated file. Reuse existing parsers where suitable and justify any new dependency.

- [x] **Step 1: Write failing API tests** `createsProjectsInvoices2026AndListsImmediateChildren`, `uploadIntoChildNotRoot`, `conflictingFileAndFolderSiblingNamesReject`, `twoConcurrentCreatesCannotDuplicateName`, `foreignFolderReadForbiddenWithoutNameLeak`, `rejectsDeclaredMimeAndSignatureMismatch`, `rejectsCorruptAllowedFormatsBeforeStorage`, `validSupportedFormatsStillUpload`. Use clearly labelled synthetic fixtures and assert rejected uploads create no object, metadata, audit-success record or outbox event. Assert 201, 200, 409 and 404 statuses plus decrypted responses only on allowed reads.
- [x] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveUploadContentValidatorTest,DriveFolderControllerTest,DriveFileControllerTest test`.
- [x] **Step 3: Implement:** HTTP `POST /api/drive/folders` body `{name,parentFolderId}`, `GET /api/drive/folders/contents?folderId=...`, `GET /api/drive/folders/{folderId}`, upload multipart optional `folderId`. Validate bounded source bytes before object storage, metadata writes or outbox publication. Under workspace row lock compare decrypted file and folder sibling names before persist. Keep old flat-list API compatible; UI uses new folder-scoped endpoint.
- [x] **Step 4: Verify GREEN:** same scoped tests and `./mvnw test`; ensure seeded and sharing behavior remain intact.
- [x] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test && git commit -m "feat(drive): create folders and browse directory contents"`.

### Task 3: Authenticated original file download and preview

**Files:** Modify `FileEncryptionService.java`, `FileObjectStorage.java`, `S3FileObjectStorage.java` and `DriveFileController.java`; create `DriveFileContentService.java` and `DriveFileContent.java`; test `DriveFileContentServiceTest.java` and `DriveFileControllerTest.java` (update its fake object store).

**Interfaces:** `FileObjectStorage.getEncryptedObject(String storageKey, long maxCiphertextBytes): byte[]`, `FileEncryptionService.decryptBytes(byte[] ciphertext, String ivBase64): byte[]` (authenticated AES-GCM), `DriveFileContentService.read(String fileId): DriveFileContent(byte[] bytes, String name, String contentType)`. HTTP `GET /api/drive/files/{fileId}/download` and `/preview`; preview only PDF, PNG, JPEG and text.

- [x] **Step 1: Write failing tests** `originalDownloadBytesMatchUpload`, `previewDeniesUnsupportedOfficeType`, `legacyMislabeledOrCorruptContentCannotPreview`, `previewCannotExecuteActiveContent`, `viewerWithoutFileViewCannotFetchBytes`, `tamperedCiphertextFailsWithoutLeak`, `oversizedObjectRejectedBeforeBuffering`; assert no-store, nosniff and safe Content-Disposition.
- [x] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveFileContentServiceTest,DriveFileControllerTest test`.
- [x] **Step 3: Implement:** authorize file VIEW before loading source metadata/bytes; add bounded S3 GetObject and AES-GCM tag verification, attachment vs inline safe headers and sanitized failures. Reuse Task 2 format validation before previewing legacy bytes, and define a viewer isolation/rendering policy that prevents active document content from executing; headers alone are not that policy. Preserve authenticated original attachment downloads without rewriting bytes. No plaintext object URL or cache.
- [x] **Step 4: Verify GREEN:** same scoped tests; check byte-for-byte comparison against originals.
- [x] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test && git commit -m "feat(drive): serve protected original downloads and previews"`.

### Task 4: Metadata-only rename and move

**Files:** Create `DriveFileMutationService.java`; modify `DriveFolderService.java`, `DriveFolderRepository.java`, `DriveFileRepository.java`, folder/file controllers; test `DriveFileMutationServiceTest.java` and `DriveFolderControllerTest.java`.

**Interfaces:** `DriveFileMutationService.renameFile(String fileId, String name): DriveFileResponse`, `moveFile(String fileId, String destinationFolderId): DriveFileResponse`; `DriveFolderService.renameFolder(String folderId, String name): DriveFolderResponse`, `moveFolder(String folderId, String destinationFolderId): DriveFolderResponse`. HTTP PATCH endpoints `/api/drive/files/{fileId}/name|location` and `/api/drive/folders/{folderId}/name|location`.

- [x] **Step 1: Write failing tests** `renamePreservesObjectKeyFileIdAndOcrLink`, `moveFolderPreservesSubtreeReferences`, `rejectCycleSelfOrDescendant`, `rejectConcurrentCollision`, `viewOnlyUserCannotRenameOrMove`.
- [x] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveFileMutationServiceTest,DriveFolderControllerTest test`.
- [x] **Step 3: Implement:** serialize workspace mutations via Task 1 lock; compare normalized encrypted sibling names under transaction; active owner permission required for source/destination; update parent IDs/name ciphertext only, not blobs or child IDs. Record metadata-only audit entries. Do not add an unconsumed event merely for future search reindexing; the separate Search plan will introduce and consume its source-update event atomically.
- [x] **Step 4: Verify GREEN:** same tests plus `DriveFileRepositoryTest`.
- [x] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test && git commit -m "feat(drive): rename and move files and folder trees"`.

### Task 5: Bounded recursive folder ZIP download

**Files:** Create `DriveArchiveService.java`, `DriveArchiveProperties.java`, `DriveZipArchive.java`; modify `DriveFolderController.java`, `application.yml`, `.env.example`; test `DriveArchiveServiceTest.java`, `DriveFolderControllerTest.java`.

**Interfaces:** `DriveArchiveService.prepareZip(String folderId): DriveZipArchive`; result is an owned temporary ZIP path and a safe filename with `close()` deleting it. HTTP `GET /api/drive/folders/{folderId}/download`. Defaults: max **1,000 entries, 512 MiB uncompressed, 2-minute preparation**; configurable and enforced server-side.

- [ ] **Step 1: Write failing tests** `zipPreservesSubfoldersEmptyDirsAndExactSourceBytes`, `rejectDotDotAndAbsoluteArchiveEntries`, `rejectAnyDeniedDescendant`, `tooManyFilesOrBytesFailsBeforeResponse`, `failedOrCancelledArchiveCleansTemporaryFiles`.
- [ ] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveArchiveServiceTest,DriveFolderControllerTest test`.
- [ ] **Step 3: Implement:** snapshot/authorize complete subtree including each file VIEW, preflight limits and normalized relative paths before returning a response. Build with `ZipOutputStream` to permission-restricted temp; clean on all paths; stream already-complete ZIP with `finally` close. Never publish partial or plaintext temp files.
- [ ] **Step 4: Verify GREEN:** same tests; verify the ZIP is readable and every entry's decompressed bytes match its source.
- [ ] **Step 5: Commit:** `git add apps/api/src/main apps/api/src/test .env.example && git commit -m "feat(drive): export authorized folder trees as ZIP"`.

### Task 6: Drive folder explorer and file actions

**Files:** Modify `apps/web/src/lib/drive-api.ts`, `features/drive/use-drive-files.ts`, `features/drive/drive-screen.tsx`, `drive-screen.test.tsx`; create `drive-folder-explorer.tsx`, `drive-file-actions.tsx`, `drive-folder-actions.tsx`, `drive-preview.tsx` and focused Vitest tests in `apps/web/src/features/drive/`.

**Interfaces:** API functions `fetchDriveFolderContents(folderId: string | null)`, `createDriveFolder(name, parentFolderId)`, `renameDriveFile`, `renameDriveFolder`, `moveDriveFile`, `moveDriveFolder`, `downloadDriveFile`, `downloadDriveFolderZip`. TanStack query key `["drive-folder-contents", folderId ?? "root"]`; upload mutation receives `{file: File, folderId: string | null}`.

- [ ] **Step 1: Write failing UI tests** `navigatesNestedFoldersWithBreadcrumbs`, `uploadsIntoCurrentFolder`, `renamesAndMovesFileWithoutLosingSelection`, `previewsPdfImageTextByProtectedFetch`, `downloadsFolderZip`, `mobileFolderPickerAndEmptyState`; stub actual network responses, not static state overrides.
- [ ] **Step 2: Verify RED:** `cd apps/web && pnpm test -- drive-folder-explorer` then `pnpm test -- drive-screen`.
- [ ] **Step 3: Implement:** reuse AppShell/Query and existing design components; folder contents not flat workspace list, blob URLs revoked after preview/download, error/loading/denied states and keyboard-accessible controls. Implement Task 3's safe viewer policy, render plain text as text, and test active-content isolation in the preview component. Scope query invalidation to affected folder and retain existing snapshot test overrides.
- [ ] **Step 4: Verify GREEN:** `cd apps/web && pnpm test -- drive && pnpm lint && pnpm typecheck && pnpm build`.
- [ ] **Step 5: Commit:** `git add apps/web/src && git commit -m "feat(drive): navigate folders and manage files in the UI"`.

### Task 7: Complete integration acceptance for this stage

**Files:** Create `apps/api/src/test/java/com/openecosystem/os/drive/DriveCoreJourneyTest.java`; modify `docs/development/TEST_COMMANDS.md` and existing API tests as needed.

**Interfaces:** No new APIs; verify Tasks 1–6 together.

- [ ] **Step 1: Write failing integration test** `nestedUploadRenameMoveDownloadZipPreservesBytesAndLineage` and `foreignUserCannotInspectOrMutateSubtree`; assert stable file ID, ciphertext key, ZIP bytes, no unauthorized labels.
- [ ] **Step 2: Verify RED:** `cd apps/api && ./mvnw -Dtest=DriveCoreJourneyTest test`.
- [ ] **Step 3: Fix only discovered integration gaps:** document reproducible Compose smoke steps and clearly state **Trash and Search indexing remain separate subsequent work**.
- [ ] **Step 4: Verify GREEN:** `cd apps/api && ./mvnw test && ./mvnw verify`; `cd apps/web && pnpm test && pnpm lint && pnpm typecheck && pnpm build`. Test real ZIP bytes on a private Compose stack or record blockers.
- [ ] **Step 5: Commit:** `git add apps/api/src/test docs/development/TEST_COMMANDS.md && git commit -m "test(drive): verify the encrypted file-management journey"`.

## Self-review and handoff

The seven tasks are deliberately scoped so every implementation/verification gate can be reviewed independently. Confirm approved upload validation and safe preview are covered in both implementation and tests, including legacy files. Full MVP Core still requires the [Trash plan](2026-10-18-mvp-core-drive-trash.md) and later OCR/Search/invoice/image-PDF plans. Continue in the isolated worktree/branch and update the execution record after each verified slice.
