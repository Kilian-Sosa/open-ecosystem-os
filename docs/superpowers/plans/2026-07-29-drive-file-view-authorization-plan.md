# Drive File Visibility and `file:view` Authorization Implementation Plan

**Status:** Approved on 2026-07-29 after the documented amendments.

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> `superpowers:executing-plans` to implement this plan task-by-task. Do not use
> subagents unless the user explicitly changes the no-subagent instruction.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist Drive file visibility and explicit user `file:view` grants,
then enforce one non-enumerating source-file decision in Drive and Media/OCR
before protected metadata or content loads, and fail-closed filter every
user-facing normalized Search result by the same source-file decision.

**Architecture:** Add `private`/`workspace` visibility to `drive_files` and a
file-specific user-grant table. A common-security authorization port evaluates
owner, eligible workspace visibility, and unrevoked user grants from
Drive-owned policy queries; Drive and OCR both call it, while OCR first loads a
three-column source reference and only then loads legacy or rich content.
Search reuses the batch decision against each candidate's persisted
`metadata.fileId` lineage before merge or response serialization.

**Tech Stack:** Java 25, Spring Boot 4, JDBC, PostgreSQL 16, H2 PostgreSQL mode,
Flyway, JUnit 5, AssertJ, Mockito, Testcontainers, Next.js/TypeScript, Vitest.

## Global Constraints

- Work only on `feat/real-ocr-extraction`; run
  `git status --short --branch` before editing and preserve all existing dirty
  and untracked user files.
- The product decisions in
  `docs/superpowers/specs/2026-07-29-drive-file-view-authorization-design.md`
  were approved on 2026-07-29. The concrete steps require: existing/new files
  are `private`; no
  private-file role bypass; workspace visibility is available to
  `INSTANCE_OWNER`, `WORKSPACE_ADMIN`, `DEVELOPER`, `EDITOR`, and `VIEWER`;
  groups are deferred; share mutation is owner-only; resource denial is 404
  after successful session authentication; and Drive responses add
  `visibility`.
- Do not begin production implementation as part of the documentation task.
  The amended design and this plan must be self-reviewed and committed as two
  separate documentation commits first.
- If a later product decision differs, update the design and this plan before
  implementing that difference.
- Do not rewrite Flyway V1-V8. Add V9 only.
- Do not add public links, group placeholders, deny grants, inherited folder
  permissions, conditions, a generic ACL schema, or new dependencies.
- Keep `FileUploaded` and OCR event payloads unchanged. Never put visibility,
  grants, filenames, storage keys, OCR text, extracted values, or warning text
  into events or decision logs.
- Preserve current upload/list/detail and Media/OCR routes. This plan does not
  add a download or sharing endpoint.
- Missing, foreign-workspace, and denied resources remain non-enumerating.
- Run PowerShell/CMD commands on Windows. Use the repository Maven wrapper and
  the narrowest tests first.
- This plan is approved, but production execution starts only in a later
  explicit implementation request. Each production task remains separately
  testable and commit-scoped.

## File and interface map

### Persistence and Drive ownership

- `V9__drive_file_visibility_and_user_grants.sql` owns schema, constraints,
  backfill, and indexes.
- `DriveFileVisibility` owns the two Java visibility values.
- `DriveFileMetadata` carries visibility with Drive metadata.
- `DriveFileRepository` persists visibility, loads only authorized IDs after
  policy filtering, and changes visibility.
- `DriveFileUserGrant` and `DriveFileUserGrantRepository` own user grant state,
  active membership validation on mutation, reactivation, and revocation.

### Reusable authorization

- `ResourceAuthorizationService` is the common port used by Drive and OCR.
- `DefaultResourceAuthorizationService` supports exactly `FILE + VIEW`.
- `FileViewAuthorizationRepository` owns single and batch policy SQL.
- `FileViewAuthorizationFacts` is policy-only and contains no protected file
  metadata.

### Drive application flow

- `DriveUploadService` uploads private files and performs authorization before
  list/detail metadata mapping.
- `DriveFileSharingService` is an internal, owner-only transactional command
  boundary. No controller/UI is added.
- `DriveFileResponse` adds the visibility string.

### OCR application flow

- `OcrJobSourceReference` is the pre-authorization record.
- `OcrJobSummary` is the post-authorization, content-free list record.
- `OcrJobRepository` separates source-reference, summary, and protected-detail
  queries.
- `OcrJobQueryService` authorizes the source before file metadata, legacy text,
  rich OCR, extraction, or lifecycle queries.

### Search disclosure flow

- `WorkflowRunner` persists the source Drive file ID in
  `search_documents.metadata_json.fileId`.
- `MeilisearchIndexClient` carries that metadata into the index.
- `SearchService` fail-closed filters PostgreSQL and Meilisearch candidates
  through `ResourceAuthorizationService.allowedResourceIds` before merge or
  response serialization.

---

### Task 1: Persistence migration and repositories

**Deliverable:** V9 migrates existing files to private visibility and persists
one auditable, revocable `file:view` user grant per file/user relationship.

**Files:**

- Create:
  `apps/api/src/main/resources/db/migration/V9__drive_file_visibility_and_user_grants.sql`
- Create:
  `apps/api/src/main/java/com/openecosystem/os/drive/DriveFileVisibility.java`
- Modify:
  `apps/api/src/main/java/com/openecosystem/os/drive/DriveFileMetadata.java`
- Modify:
  `apps/api/src/main/java/com/openecosystem/os/drive/DriveFileRepository.java`
- Create:
  `apps/api/src/main/java/com/openecosystem/os/drive/DriveFileUserGrant.java`
- Create:
  `apps/api/src/main/java/com/openecosystem/os/drive/DriveFileUserGrantRepository.java`
- Modify:
  `apps/api/src/test/java/com/openecosystem/os/drive/DriveFileRepositoryTest.java`
- Create:
  `apps/api/src/test/java/com/openecosystem/os/drive/DriveFileUserGrantPostgresRepositoryTest.java`
- Modify:
  `apps/api/src/test/java/com/openecosystem/os/migration/RealOcrPostgresMigrationTest.java`

**Interfaces produced:**

```java
public enum DriveFileVisibility {
  PRIVATE("private"),
  WORKSPACE("workspace");

  public String value();

  public static DriveFileVisibility fromValue(String value);
}
```

```java
public record DriveFileUserGrant(
    String grantId,
    String workspaceId,
    String fileId,
    String granteeUserId,
    String action,
    String grantedByUserId,
    Instant grantedAt,
    String revokedByUserId,
    Instant revokedAt,
    Instant createdAt,
    Instant updatedAt) {

  public boolean active() {
    return revokedAt == null;
  }
}
```

```java
public final class DriveFileUserGrantRepository {
  Optional<DriveFileUserGrant> find(
      String workspaceId, String fileId, String granteeUserId, String action);

  DriveFileUserGrant grantOrReactivateView(
      String grantId,
      String workspaceId,
      String fileId,
      String granteeUserId,
      String grantedByUserId,
      Instant grantedAt);

  Optional<DriveFileUserGrant> revokeView(
      String workspaceId,
      String fileId,
      String granteeUserId,
      String revokedByUserId,
      Instant revokedAt);
}
```

`grantOrReactivateView` must affect one row only when the file belongs to the
workspace and both grantor and grantee are active users with at least one
membership row in that active workspace. It reuses the unique relationship row
and clears revocation fields. `revokeView` is idempotent: absent or already
revoked returns the current/empty result without creating a second row.

`DriveFileRepository` gains:

```java
List<String> listIdsByWorkspace(String workspaceId);

List<DriveFileMetadata> listByIdsForWorkspace(
    String workspaceId, Collection<String> fileIds);

boolean updateVisibility(
    String fileId,
    String workspaceId,
    DriveFileVisibility visibility,
    Instant updatedAt);
```

- [ ] **Step 1: Write the V9 migration**

  Use the exact DDL from the design. Keep the database default `private`,
  explicitly constrain `private|workspace`, add the composite
  `(file_id, workspace_id)` uniqueness required by the grant lineage foreign
  key, create the composite file and user foreign keys, enforce the paired
  revocation fields and time ordering, and add all four indexes.

- [ ] **Step 2: Extend the PostgreSQL migration test with V8-to-V9 RED coverage**

  Add a test that migrates to target 8, inserts active identity/workspace/member
  rows plus a representative file, migrates to latest, and asserts:

  ```java
  assertThat(
          jdbcTemplate.queryForObject(
              "select visibility from drive_files where file_id = ?",
              String.class,
              "file_legacy"))
      .isEqualTo("private");
  ```

  Also assert invalid visibility/action/revocation pairs fail; foreign file
  workspace lineage fails; duplicate file/user/action fails; and the expected
  indexes exist in `pg_indexes`.

- [ ] **Step 3: Add repository RED tests**

  In `DriveFileRepositoryTest`, assert saves explicitly persist both visibility
  values and `listByIdsForWorkspace` preserves newest-first ordering while
  excluding other workspaces.

  In `DriveFileUserGrantPostgresRepositoryTest`, use PostgreSQL Testcontainers
  and cover:
  - active same-workspace grant creation;
  - no grant for a missing, disabled, removed, or foreign-workspace grantee;
  - no grant from an inactive/foreign grantor;
  - regrant reuses `grant_id`, clears revocation, and updates grant audit fields;
  - two concurrent grant commands both succeed and leave one active row;
  - concurrent grant/regrant commands return successful insert-or-update
    outcomes rather than a duplicate-key failure;
  - revoke is paired and idempotent; and
  - one row per workspace/file/user/`file:view`.

- [ ] **Step 4: Run the focused RED tests**

  ```powershell
  apps\api\mvnw.cmd -q "-Dtest=DriveFileRepositoryTest,DriveFileUserGrantPostgresRepositoryTest,RealOcrPostgresMigrationTest" test
  ```

  Expected before implementation: compilation/migration failures because V9,
  visibility, and grant repository types do not exist.

- [ ] **Step 5: Implement the Java visibility mapping and repository SQL**

  Add `DriveFileVisibility visibility` after `ownerId` in
  `DriveFileMetadata`. Update every constructor, insert column list, argument
  list, and row mapper explicitly.

  Implement `grantOrReactivateView` transactionally as:
  1. validate active file/grantor/grantee lineage with content-free `exists`
     queries;
  2. execute one PostgreSQL `insert ... on conflict on constraint
drive_file_user_grants_target_unique do update` statement;
  3. preserve the existing `grant_id` and `created_at` on conflict;
  4. replace grantor/time, clear revocation fields, and update `updated_at`; and
  5. return the resulting row.

  A valid concurrent duplicate grant or regrant command must receive an atomic
  insert-or-update outcome and must not fail merely because another transaction
  won the race. Do not catch a unique violation and retry inside the same
  PostgreSQL transaction.

  H2 2.4 PostgreSQL mode does not support `on conflict do update`. Keep this
  repository's SQL and concurrency coverage in
  `DriveFileUserGrantPostgresRepositoryTest`; do not add a production H2
  dialect or weaken the concurrency assertion.

  Do not select names, emails, file metadata, or content for lineage checks.

- [ ] **Step 6: Run Task 1 verification**

  ```powershell
  apps\api\mvnw.cmd -q "-Dtest=DriveFileRepositoryTest,DriveFileUserGrantPostgresRepositoryTest,RealOcrPostgresMigrationTest" test
  apps\api\mvnw.cmd -q spotless:check
  ```

  Expected: the H2 Drive repository test and PostgreSQL 16 grant/migration
  tests pass.

- [ ] **Step 7: Commit Task 1 after its checks pass**

  Proposed commit:

  ```powershell
  git add -- apps/api/src/main/resources/db/migration/V9__drive_file_visibility_and_user_grants.sql apps/api/src/main/java/com/openecosystem/os/drive apps/api/src/test/java/com/openecosystem/os/drive apps/api/src/test/java/com/openecosystem/os/migration/RealOcrPostgresMigrationTest.java
  git commit -m "feat(drive): persist file visibility and user grants"
  ```

---

### Task 2: Reusable `file:view` decision service

**Deliverable:** One common port returns deterministic allow/deny decisions and
one batch path returns the same allowed IDs without loading protected file
metadata.

**Consumes from Task 1:**

- `DriveFileVisibility`
- `drive_files.visibility`
- `drive_file_user_grants`

**Files:**

- Create:
  `apps/api/src/main/java/com/openecosystem/os/common/security/ResourceType.java`
- Create:
  `apps/api/src/main/java/com/openecosystem/os/common/security/ResourceAction.java`
- Create:
  `apps/api/src/main/java/com/openecosystem/os/common/security/AuthorizationDecisionCode.java`
- Create:
  `apps/api/src/main/java/com/openecosystem/os/common/security/AuthorizationDecision.java`
- Create:
  `apps/api/src/main/java/com/openecosystem/os/common/security/ResourceAuthorizationService.java`
- Create:
  `apps/api/src/main/java/com/openecosystem/os/common/security/DefaultResourceAuthorizationService.java`
- Create:
  `apps/api/src/main/java/com/openecosystem/os/drive/FileViewAuthorizationFacts.java`
- Create:
  `apps/api/src/main/java/com/openecosystem/os/drive/FileViewAuthorizationRepository.java`
- Create:
  `apps/api/src/test/java/com/openecosystem/os/common/security/DefaultResourceAuthorizationServiceTest.java`
- Create:
  `apps/api/src/test/java/com/openecosystem/os/drive/FileViewAuthorizationRepositoryTest.java`
- Modify:
  `apps/api/src/test/java/com/openecosystem/os/common/security/PlaceholderAuthenticationContextTest.java`

**Interfaces produced:**

```java
public interface ResourceAuthorizationService {
  AuthorizationDecision decide(
      AuthenticatedPrincipal principal,
      String workspaceId,
      ResourceType resourceType,
      String resourceId,
      ResourceAction action);

  Set<String> allowedResourceIds(
      AuthenticatedPrincipal principal,
      String workspaceId,
      ResourceType resourceType,
      Collection<String> resourceIds,
      ResourceAction action);
}
```

```java
public record AuthorizationDecision(
    boolean allowed, AuthorizationDecisionCode code) {

  public static AuthorizationDecision allow(AuthorizationDecisionCode code);

  public static AuthorizationDecision deny(AuthorizationDecisionCode code);
}
```

```java
public record FileViewAuthorizationFacts(
    String fileId,
    String workspaceId,
    String ownerId,
    DriveFileVisibility visibility,
    boolean activeMember,
    boolean workspaceVisibilityRole,
    boolean activeUserGrant) {}
```

```java
public final class FileViewAuthorizationRepository {
  Optional<FileViewAuthorizationFacts> findFacts(
      String actorId, String workspaceId, String fileId);

  Set<String> findAllowedFileIds(
      String actorId, String workspaceId, Collection<String> fileIds);
}
```

The implementation supports only `ResourceType.FILE` and
`ResourceAction.VIEW`. It chunks batch IDs into groups of at most 500 and
returns an immutable set.

- [ ] **Step 1: Write the complete decision-table RED test**

  Use a mocked `FileViewAuthorizationRepository` for exact precedence:

  ```java
  assertThat(service.decide(principal, workspace, FILE, fileId, VIEW))
      .isEqualTo(new AuthorizationDecision(true, ALLOW_OWNER));
  ```

  Cover owner, private admin non-bypass, workspace role allow,
  Guest/Auditor workspace denial, explicit grant allow, revoked/no grant,
  inactive member, workspace mismatch, resource missing, unsupported
  resource/action, and precedence when multiple allow facts are true.

- [ ] **Step 2: Write real SQL RED tests for singular and batch parity**

  Seed all supported roles, private/workspace files, active/revoked grants,
  disabled users, a disabled workspace, and removed memberships. For every
  candidate:

  ```java
  boolean singularAllowed =
      service.decide(principal, workspaceId, FILE, fileId, VIEW).allowed();
  assertThat(batchAllowed.contains(fileId)).isEqualTo(singularAllowed);
  ```

  Assert the repository's policy queries do not return encrypted names,
  storage keys, checksums, OCR columns, or grant-target email/display name.
  Assert `findFacts` returns a row with `activeMember=false` for each existing
  same-workspace file requested by a disabled actor, an actor in a disabled
  workspace, and an actor whose membership row was removed. Assert only a
  missing or foreign-workspace file returns `Optional.empty()`.

- [ ] **Step 3: Add stale/synthetic-principal membership tests**

  Extend `PlaceholderAuthenticationContextTest` to preserve the current 403
  behavior for a normal disabled/removed member. Separately prove that a
  directly constructed or seeded fallback principal still receives
  `DENY_INACTIVE_MEMBERSHIP` from the resource service when persisted active
  membership is absent.

- [ ] **Step 4: Run the focused RED tests**

  ```powershell
  apps\api\mvnw.cmd -q "-Dtest=DefaultResourceAuthorizationServiceTest,FileViewAuthorizationRepositoryTest,PlaceholderAuthenticationContextTest" test
  ```

  Expected before implementation: compilation failures for the new
  authorization port and facts repository.

- [ ] **Step 5: Implement exact precedence**

  Implement:

  ```java
  if (principal == null || !principal.authenticated()) {
    return deny(DENY_UNAUTHENTICATED);
  }
  if (!workspaceId.equals(principal.workspaceId())) {
    return deny(DENY_WORKSPACE_MISMATCH);
  }
  if (resourceType != FILE || action != VIEW) {
    return deny(DENY_UNSUPPORTED);
  }
  FileViewAuthorizationFacts facts =
      repository.findFacts(principal.actorId(), workspaceId, resourceId)
          .orElse(null);
  if (facts == null) return deny(DENY_RESOURCE_NOT_FOUND);
  if (!facts.activeMember()) return deny(DENY_INACTIVE_MEMBERSHIP);
  if (principal.actorId().equals(facts.ownerId())) return allow(ALLOW_OWNER);
  if (facts.visibility() == WORKSPACE && facts.workspaceVisibilityRole()) {
    return allow(ALLOW_WORKSPACE_VISIBILITY);
  }
  if (facts.activeUserGrant()) return allow(ALLOW_USER_GRANT);
  return deny(DENY_NO_POLICY);
  ```

  Do not add an admin/owner role bypass branch.

- [ ] **Step 6: Implement singular and batch policy SQL**

  `findFacts` selects only file/workspace/owner/visibility and correlated
  `exists` booleans. Its outer query must be:

  ```sql
  from drive_files f
  where f.file_id = ?
    and f.workspace_id = ?
  ```

  Do not inner-join the actor, workspace, or membership through the outer
  `where`, because doing so would collapse inactive cases into resource-not-
  found. Compute `activeMember` with an `exists` expression that requires an
  active actor, active workspace, and any membership row. Compute
  `workspaceVisibilityRole` and `activeUserGrant` with the same active
  membership prerequisite so both are false when `activeMember=false`.

  `findAllowedFileIds` selects only `file_id` and may apply the full active
  allow predicate because denied rows are intentionally absent from batch
  output. Workspace visibility additionally requires a role in the approved
  workspace-content set. User grants require `action = 'file:view'` and
  `revoked_at is null`.

- [ ] **Step 7: Run Task 2 verification**

  ```powershell
  apps\api\mvnw.cmd -q "-Dtest=DefaultResourceAuthorizationServiceTest,FileViewAuthorizationRepositoryTest,PlaceholderAuthenticationContextTest" test
  apps\api\mvnw.cmd -q spotless:check
  ```

  Expected: every decision-table case and singular/batch parity test passes.

- [ ] **Step 8: Commit Task 2 after its checks pass**

  Proposed commit:

  ```powershell
  git add -- apps/api/src/main/java/com/openecosystem/os/common/security apps/api/src/main/java/com/openecosystem/os/drive/FileViewAuthorizationFacts.java apps/api/src/main/java/com/openecosystem/os/drive/FileViewAuthorizationRepository.java apps/api/src/test/java/com/openecosystem/os/common/security apps/api/src/test/java/com/openecosystem/os/drive/FileViewAuthorizationRepositoryTest.java
  git commit -m "feat(auth): add reusable file view decisions"
  ```

---

### Task 3: Drive enforcement

**Deliverable:** Upload persists private visibility; list/detail load only
allowed file metadata; user grants/workspace visibility work; denied detail is
the same 404 as missing; share mutations are internal, owner-only, and audited.

**Consumes from Tasks 1-2:**

- `ResourceAuthorizationService`
- `DriveFileVisibility`
- `DriveFileUserGrantRepository`
- `DriveFileRepository.listIdsByWorkspace/listByIdsForWorkspace`

**Files:**

- Modify:
  `apps/api/src/main/java/com/openecosystem/os/drive/DriveUploadService.java`
- Modify:
  `apps/api/src/main/java/com/openecosystem/os/drive/DriveFileResponse.java`
- Create:
  `apps/api/src/main/java/com/openecosystem/os/drive/DriveFileSharingService.java`
- Modify:
  `apps/api/src/test/java/com/openecosystem/os/drive/DriveFileControllerTest.java`
- Create:
  `apps/api/src/test/java/com/openecosystem/os/drive/DriveFileSharingServiceTest.java`
- Modify: `apps/web/src/lib/drive-api.ts`
- Modify: `apps/web/src/lib/drive-mock-data.ts`
- Modify: `apps/web/src/features/drive/drive-screen.test.tsx`

**Interfaces produced:**

```java
public final class DriveFileSharingService {
  DriveFileUserGrant grantView(
      AuthenticatedPrincipal principal, String fileId, String granteeUserId);

  DriveFileUserGrant revokeView(
      AuthenticatedPrincipal principal, String fileId, String granteeUserId);

  DriveFileMetadata changeVisibility(
      AuthenticatedPrincipal principal,
      String fileId,
      DriveFileVisibility visibility);
}
```

`DriveFileResponse` adds:

```java
String visibility
```

after `fileId`. The web `DriveFile` type adds:

```typescript
visibility: "private" | "workspace";
```

- [ ] **Step 1: Write Drive controller RED tests**

  Add cases for:
  - upload response and database row are `private`;
  - active owner sees its private file;
  - active admin who is not owner does not bypass a private file;
  - workspace-content roles see a `workspace` file;
  - Guest/Auditor without a grant do not see a workspace file;
  - an active explicit user grant exposes a private file;
  - a revoked grant removes list/detail access;
  - foreign-workspace, missing, and denied detail have identical status, error
    code, and public message; and
  - denied files are absent from a 200 list response.

  Assert denied names and storage keys never appear in bodies.

- [ ] **Step 2: Write sharing-service RED tests**

  Prove only `ALLOW_OWNER` can change visibility or grant/revoke. Assert
  `WORKSPACE_ADMIN` without ownership is rejected under the approved baseline.
  Prove a disabled or removed owner cannot mutate or view the private file and
  that no administrator acquires an implicit recovery path. Record this as the
  accepted MVP owner-loss limitation; do not solve it in this task.
  For each success, verify the grant/visibility mutation and one audit row are
  committed together.

  Audit assertions:

  ```java
  assertThat(audit.attributes())
      .containsEntry("action", "file:view")
      .containsEntry("granteeUserId", "usr_grantee")
      .doesNotContainKeys("filename", "email", "storageKey", "extractedText");
  ```

- [ ] **Step 3: Run Drive RED tests**

  ```powershell
  apps\api\mvnw.cmd -q "-Dtest=DriveFileControllerTest,DriveFileSharingServiceTest" test
  ```

  Expected: failures because Drive still loads all workspace metadata before
  its owner-only filter and responses have no visibility.

- [ ] **Step 4: Implement protected-load ordering**

  Upload constructs `DriveFileMetadata` with `PRIVATE`.

  List performs:

  ```java
  List<String> candidates = driveFileRepository.listIdsByWorkspace(workspaceId);
  Set<String> allowed =
      authorizationService.allowedResourceIds(
          principal, workspaceId, FILE, candidates, VIEW);
  return driveFileRepository.listByIdsForWorkspace(workspaceId, allowed).stream()
      .map(this::toResponse)
      .toList();
  ```

  Detail calls `decide` before
  `DriveFileRepository.findByIdForWorkspace`. Any non-allow maps to the existing
  `Drive file was not found` 404.

- [ ] **Step 5: Implement the internal sharing command boundary**

  Use the decision's `ALLOW_OWNER` basis as the owner-only mutation policy.
  Wrap each repository mutation plus `JdbcAuditRecordRepository.save` in one
  `TransactionTemplate` transaction. Add no controller, route, event, or UI.

- [ ] **Step 6: Update the additive web transport contract**

  Add visibility to `DriveFile`, mock fixtures, and test fixtures. Do not render
  a badge, editor, modal, or permission control. Verify the current Drive
  desktop/mobile behavior is unchanged.

- [ ] **Step 7: Run Task 3 verification**

  ```powershell
  apps\api\mvnw.cmd -q "-Dtest=DriveFileControllerTest,DriveFileSharingServiceTest" test
  apps\api\mvnw.cmd -q spotless:check
  apps\web\node_modules\.bin\vitest.CMD run src\features\drive\drive-screen.test.tsx
  Push-Location apps\web
  corepack pnpm format:check
  corepack pnpm lint
  corepack pnpm typecheck
  Pop-Location
  ```

  Expected: all commands pass and no sharing UI is introduced.

- [ ] **Step 8: Commit Task 3 after its checks pass**

  Proposed commit:

  ```powershell
  git add -- apps/api/src/main/java/com/openecosystem/os/drive apps/api/src/test/java/com/openecosystem/os/drive apps/web/src/lib/drive-api.ts apps/web/src/lib/drive-mock-data.ts apps/web/src/features/drive/drive-screen.test.tsx
  git commit -m "feat(drive): enforce persisted file visibility"
  ```

---

### Task 4: OCR enforcement before all content loads

**Deliverable:** OCR list/detail resolves a content-free job/source reference,
authorizes the Drive source, and only then loads legacy text, safe summary
metadata, rich OCR/extraction content, filename metadata, or lifecycle rows.

**Consumes from Task 2:**

- `ResourceAuthorizationService.decide`
- `ResourceAuthorizationService.allowedResourceIds`

**Files:**

- Create:
  `apps/api/src/main/java/com/openecosystem/os/media/OcrJobSourceReference.java`
- Create:
  `apps/api/src/main/java/com/openecosystem/os/media/OcrJobSummary.java`
- Modify:
  `apps/api/src/main/java/com/openecosystem/os/media/OcrJobRepository.java`
- Modify:
  `apps/api/src/main/java/com/openecosystem/os/media/OcrJobQueryService.java`
- Delete:
  `apps/api/src/main/java/com/openecosystem/os/common/security/ResourcePermissionDecision.java`
- Delete:
  `apps/api/src/test/java/com/openecosystem/os/common/security/ResourcePermissionDecisionTest.java`
- Create:
  `apps/api/src/test/java/com/openecosystem/os/media/OcrJobRepositoryTest.java`
- Modify:
  `apps/api/src/test/java/com/openecosystem/os/media/OcrJobQueryServiceTest.java`
- Modify:
  `apps/api/src/test/java/com/openecosystem/os/media/OcrJobControllerTest.java`

**Interfaces produced:**

```java
public record OcrJobSourceReference(
    String jobId, String fileId, String workspaceId) {}
```

```java
public record OcrJobSummary(
    String jobId,
    String fileId,
    String workspaceId,
    String contentType,
    OcrJobStatus status,
    String provider,
    int attemptCount,
    int maxAttempts,
    Integer extractedTextLength,
    String failureCode,
    String correlationId,
    Instant queuedAt,
    Instant processingStartedAt,
    Instant completedAt,
    Instant failedAt,
    Instant updatedAt) {}
```

```java
List<OcrJobSourceReference> listSourceReferencesByWorkspace(String workspaceId);

Optional<OcrJobSourceReference> findSourceReferenceByIdForWorkspace(
    String jobId, String workspaceId);

List<OcrJobSummary> findSummariesByIdsForWorkspace(
    Collection<String> jobIds, String workspaceId);

Optional<OcrJob> findDetailByIdForWorkspace(String jobId, String workspaceId);
```

- [ ] **Step 1: Write repository query-shape RED tests**

  Seed a legacy secret in `ocr_jobs.extracted_text` and a private diagnostic in
  `failure_message`. Assert source-reference and summary methods return the
  required values but their SQL/mappers never select or expose
  `extracted_text`, `failure_message`, or `storage_key`.

  Assert `findDetailByIdForWorkspace` remains the explicit full-row path and
  returns legacy text only when called directly.

- [ ] **Step 2: Strengthen query-service denial RED tests**

  Retain the existing denied, foreign-workspace, and missing-source cases.
  Additionally verify none of these run:

  ```java
  verify(driveFileRepository, never()).findByIdForWorkspace(any(), any());
  verify(ocrJobRepository, never()).findDetailByIdForWorkspace(any(), any());
  verify(ocrResultRepository, never()).findByJobIdForWorkspace(any(), any());
  verify(invoiceExtractionRepository, never())
      .findByOcrJobIdForWorkspace(any(), any());
  verify(lifecycleProjectionService, never()).project(any());
  verify(encryptionService, never()).decryptText(any(), any());
  ```

  A missing job must stop before the authorization service. A missing source
  must call only source-reference and policy-facts queries.

- [ ] **Step 3: Add list-path RED tests**

  Seed owner, workspace-visible, user-granted, private-unshared, revoked, and
  foreign jobs. Assert:
  - only authorized job IDs reach `findSummariesByIdsForWorkspace`;
  - only authorized file IDs reach the Drive metadata load;
  - result-presence and extraction-summary queries receive authorized job IDs
    only;
  - no legacy text or rich content method runs; and
  - the JSON remains metadata-only.

- [ ] **Step 4: Run the OCR RED tests**

  ```powershell
  apps\api\mvnw.cmd -q "-Dtest=OcrJobRepositoryTest,OcrJobQueryServiceTest,OcrJobControllerTest" test
  ```

  Expected: failures because current source lookup maps the full `OcrJob`
  before authorization.

- [ ] **Step 5: Implement the detail flow in strict order**

  Implement:

  ```java
  OcrJobSourceReference source =
      ocrJobRepository
          .findSourceReferenceByIdForWorkspace(jobId, principal.workspaceId())
          .orElseThrow(this::ocrNotFound);

  AuthorizationDecision decision =
      authorizationService.decide(
          principal, source.workspaceId(), FILE, source.fileId(), VIEW);
  if (!decision.allowed()) throw ocrNotFound();

  DriveFileMetadata file =
      driveFileRepository
          .findByIdForWorkspace(source.fileId(), source.workspaceId())
          .orElseThrow(this::ocrNotFound);
  OcrJob job =
      ocrJobRepository
          .findDetailByIdForWorkspace(source.jobId(), source.workspaceId())
          .orElseThrow(this::ocrNotFound);
  return toDetailResponse(job, file);
  ```

  Keep rich OCR, extraction, and lifecycle calls inside
  `toDetailResponse` after these steps.

- [ ] **Step 6: Implement the list flow**

  Resolve source references, batch-authorize distinct file IDs, retain only
  allowed job IDs, then load safe summaries and allowed file metadata. Run the
  existing result-presence and extraction-summary projections only for those
  job IDs.

  Do not use `OcrJob` as the list model and do not select `extracted_text`.

- [ ] **Step 7: Remove the owner-only transitional decision**

  After both Drive and OCR compile against `ResourceAuthorizationService`,
  delete `ResourcePermissionDecision` and its owner-only unit test. Use the new
  decision-service tests as the authoritative policy coverage.

- [ ] **Step 8: Run Task 4 verification**

  ```powershell
  apps\api\mvnw.cmd -q "-Dtest=OcrJobRepositoryTest,OcrJobQueryServiceTest,OcrJobControllerTest,DefaultResourceAuthorizationServiceTest" test
  apps\api\mvnw.cmd -q test
  apps\api\mvnw.cmd -q spotless:check
  ```

  Expected: all commands pass; denial tests prove every protected repository,
  decryption, lifecycle, and mapper path is untouched.

- [ ] **Step 9: Commit Task 4 after its checks pass**

  Proposed commit:

  ```powershell
  git add -- apps/api/src/main/java/com/openecosystem/os/media apps/api/src/main/java/com/openecosystem/os/common/security apps/api/src/test/java/com/openecosystem/os/media apps/api/src/test/java/com/openecosystem/os/common/security
  git commit -m "fix(ocr): authorize source before content loads"
  ```

---

### Task 5: Tests, documentation, and integration verification

**Deliverable:** PostgreSQL/H2/service/controller evidence covers the full
decision table and load ordering; architecture docs describe the persisted
policy; PostgreSQL and Meilisearch-derived normalized extraction results are
fail-closed filtered by source-file `file:view`; CV-04 evidence is updated
truthfully.

**Files:**

- Modify:
  `apps/api/src/test/java/com/openecosystem/os/migration/RealOcrPostgresMigrationTest.java`
- Modify:
  `apps/api/src/test/java/com/openecosystem/os/common/security/PlaceholderAuthenticationContextTest.java`
- Modify:
  `apps/api/src/test/java/com/openecosystem/os/drive/DriveFileRepositoryTest.java`
- Modify:
  `apps/api/src/test/java/com/openecosystem/os/drive/DriveFileControllerTest.java`
- Modify:
  `apps/api/src/test/java/com/openecosystem/os/drive/DriveFileUserGrantPostgresRepositoryTest.java`
- Modify:
  `apps/api/src/test/java/com/openecosystem/os/media/OcrJobRepositoryTest.java`
- Modify:
  `apps/api/src/test/java/com/openecosystem/os/media/OcrJobQueryServiceTest.java`
- Modify:
  `apps/api/src/test/java/com/openecosystem/os/media/OcrJobControllerTest.java`
- Modify:
  `apps/api/src/main/java/com/openecosystem/os/search/SearchService.java`
- Modify:
  `apps/api/src/test/java/com/openecosystem/os/search/SearchServiceTest.java`
- Modify:
  `apps/api/src/test/java/com/openecosystem/os/flows/WorkflowExecutionServiceTest.java`
- Modify: `docs/architecture/PERMISSIONS.md`
- Modify: `docs/architecture/DATA_STRATEGY.md`
- Modify: `docs/development/SECURITY_AND_VULNERABILITY_CHECKS.md`
- Modify:
  `.superpowers/sdd/real-ocr-remediation-verification.md`

`docs/architecture/SECURITY.md` is absent and must not be invented as part of
this bounded change. The existing security-check document is the applicable
security documentation.

- [ ] **Step 1: Complete the cross-layer authorization matrix**

  Ensure automated coverage for:
  - owner;
  - every persisted workspace role;
  - private and workspace visibility;
  - active/revoked/regranted user grant;
  - Guest/Auditor explicit grant;
  - disabled user/workspace;
  - removed membership;
  - foreign workspace;
  - missing source;
  - no admin private-file bypass;
  - singular/batch parity; and
  - identical missing/denied public responses.

- [ ] **Step 2: Add PostgreSQL rollback-compatibility assertions**

  In the Testcontainers migration test, after V9:
  - execute a pre-V9-shaped `drive_files` insert that omits `visibility` and
    assert it becomes private;
  - prove V9 grant/visibility data survives application-level reads;
  - document that a pre-V9 binary ignores grants and returns to owner-only
    availability without exposing more data; and
  - do not add a down migration.

- [ ] **Step 3: Add Search source-lineage and denial RED tests**

  In `WorkflowExecutionServiceTest`, assert each persisted normalized
  extraction search document contains the exact source Drive file ID at
  `metadata.fileId`.

  In `SearchServiceTest`, cover PostgreSQL-local, Meilisearch, merged, and
  PostgreSQL-fallback candidates. For each backend, assert:
  - owner, eligible workspace visibility, and active user-grant candidates are
    retained according to the batch decision;
  - private-unshared, revoked, foreign, inactive-member, and missing-source
    candidates are omitted;
  - a candidate with missing, blank, or non-string `metadata.fileId` is omitted
    fail-closed;
  - the authorization call receives the distinct source file IDs only;
  - denied result title, summary, normalized metadata, resource link, and ID
    never appear in the `SearchResponse`; and
  - backend labeling and merge counts are computed from filtered results.

- [ ] **Step 4: Implement Search enforcement before response construction**

  Inject `ResourceAuthorizationService` into `SearchService`. Treat every
  currently supported normalized extraction result as Drive-derived. For local
  and Meilisearch candidates independently:

  ```java
  Set<String> allowedFileIds =
      authorizationService.allowedResourceIds(
          principal, principal.workspaceId(), FILE, distinctSourceFileIds, VIEW);
  ```

  Extract `fileId` only from the persisted/indexed metadata, drop candidates
  without a non-blank string lineage, and retain only candidates whose file ID
  is in `allowedFileIds`. Run this filter before `mergeResults`, backend label
  calculation, and public `SearchResponse` construction.

  Do not infer access from search-document workspace, source type, resource
  link, correlation ID, or extraction ID. Do not create a Search-specific
  permission evaluator.

- [ ] **Step 5: Run focused Search verification**

  ```powershell
  apps\api\mvnw.cmd -q "-Dtest=SearchServiceTest,WorkflowExecutionServiceTest,DefaultResourceAuthorizationServiceTest" test
  ```

  Expected: allowed source results remain visible across both backends; denied
  or lineage-free normalized extraction results are absent from every 200
  response and fallback path.

- [ ] **Step 6: Update architecture and security documentation**

  Replace the current owner-only seeded-MVP paragraph in `PERMISSIONS.md` with
  the approved visibility modes, role eligibility, user-grant behavior,
  explicit group deferral, decision precedence, and denial semantics.

  Update `DATA_STRATEGY.md` to state that object storage remains private while
  application visibility is `private|workspace` and explicit user grants are
  PostgreSQL policy state.

  Update `SECURITY_AND_VULNERABILITY_CHECKS.md` with negative authorization
  checks for Drive/OCR and the rule that filenames, storage keys, OCR text, and
  extracted values are not loaded before `file:view`. Document that
  user-facing normalized Search results are batch-filtered by the source file
  decision and lineage-free Drive-derived hits fail closed.

- [ ] **Step 7: Run the complete focused matrix**

  ```powershell
  apps\api\mvnw.cmd -q "-Dtest=DriveFileRepositoryTest,DriveFileUserGrantPostgresRepositoryTest,FileViewAuthorizationRepositoryTest,DefaultResourceAuthorizationServiceTest,PlaceholderAuthenticationContextTest,DriveFileControllerTest,DriveFileSharingServiceTest,OcrJobRepositoryTest,OcrJobQueryServiceTest,OcrJobControllerTest,SearchServiceTest,WorkflowExecutionServiceTest,RealOcrPostgresMigrationTest" test
  apps\web\node_modules\.bin\vitest.CMD run src\features\drive\drive-screen.test.tsx
  ```

  Expected: all tests pass, including PostgreSQL 16 migration constraints.

- [ ] **Step 8: Run broader quality gates**

  ```powershell
  apps\api\mvnw.cmd -q test
  apps\api\mvnw.cmd -q spotless:check
  Push-Location apps\web
  corepack pnpm format:check
  corepack pnpm lint
  corepack pnpm typecheck
  corepack pnpm test
  corepack pnpm build
  Pop-Location
  npx --yes prettier@3.8.3 "docs/superpowers/specs/2026-07-29-drive-file-view-authorization-design.md" "docs/superpowers/plans/2026-07-29-drive-file-view-authorization-plan.md" "docs/architecture/*.md" "docs/development/*.md" --check
  & 'C:\Program Files\Git\cmd\git.exe' diff --check
  ```

  Expected: every command exits 0. If Docker/Testcontainers is unavailable,
  report PostgreSQL verification as blocked; do not substitute H2 evidence.

- [ ] **Step 9: Record truthful CV-04 verification**

  Update `.superpowers/sdd/real-ocr-remediation-verification.md` only after the
  commands run. Record exact commands, exit codes, PostgreSQL version, source
  commit, and this statement only if supported:

  ```txt
  CV-04: PASS — persisted private/workspace visibility and active user file:view
  grants are evaluated through one reusable Drive/OCR/Search authorization
  boundary. Missing, foreign, denied, inactive, and revoked Drive/OCR cases are
  non-enumerating; no Drive metadata, legacy extracted_text, rich
  OCR/extraction, filename decryption, or lifecycle repository runs before an
  allowed source decision; and PostgreSQL, Meilisearch, merged, and fallback
  Search results omit every normalized extraction whose source file is not
  allowed.
  ```

  Otherwise keep CV-04 blocked and name the exact missing case or command. In
  particular, do not mark CV-04 passing if either Search backend, merged
  results, fallback behavior, or source-lineage propagation is unverified.

- [ ] **Step 10: Self-review against the design**

  Verify:
  - no group/public-link/general ACL schema was added;
  - no role bypass appears outside the approved decision table;
  - all new uploads explicitly use private visibility;
  - existing rows migrate private;
  - no `select *` occurs in pre-authorization source/facts/summary queries;
  - the old owner-only `ResourcePermissionDecision` has no callers and is
    removed;
  - API visibility is additive and the web has no new sharing UI;
  - events are unchanged and content-free;
  - audit attributes are allowlisted;
  - Search filters both backends before merge/serialization and drops missing
    lineage fail-closed; and
  - rollback is application-only with V9 retained.

- [ ] **Step 11: Commit Task 5 after its checks pass**

  Proposed commit:

  ```powershell
  git add -- apps/api/src/main/java/com/openecosystem/os/search/SearchService.java apps/api/src/test apps/web/src docs/architecture docs/development/SECURITY_AND_VULNERABILITY_CHECKS.md .superpowers/sdd/real-ocr-remediation-verification.md
  git commit -m "fix(search): enforce source file view"
  ```

## Completion boundary

The implementation is complete only when all five tasks pass independently and
the combined verification proves:

1. V9 safely backfills existing files and enforces grant lineage/state.
2. Singular and batch decisions agree for the complete role/visibility/grant
   matrix.
3. Drive never loads denied metadata and returns the same 404 for missing and
   denied detail.
4. OCR never loads legacy `extracted_text`, rich OCR/extraction content,
   filename metadata, or lifecycle data before source `file:view`.
5. PostgreSQL-local, Meilisearch, merged, and fallback Search responses omit
   every normalized extraction whose source Drive file is not viewable, and
   lineage-free Drive-derived hits fail closed.
6. Groups, role bypass, share managers, defaults, and denial behavior match the
   explicitly approved product decisions.

Do not publish, push, open a PR, or declare CV-04 closed before that evidence is
recorded.
