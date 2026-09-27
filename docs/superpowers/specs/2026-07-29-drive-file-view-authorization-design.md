# Drive File Visibility and `file:view` Authorization Design

**Status:** Approved for implementation planning on 2026-07-29.

**Date:** 2026-07-29

**Target branch:** `feat/real-ocr-extraction`

## Intent

Close CV-04 with the smallest persisted policy that can answer whether an active
workspace principal may perform `file:view` on a Drive file. Drive and Media/OCR
must use the same decision. The policy must distinguish a private file from a
workspace-visible file and must support explicit user sharing without treating
workspace membership or a role name as universal access.

This design does not implement a general enterprise RBAC/ABAC platform. It adds
only the resource, action, visibility, and grant concepts required by Drive and
source-derived OCR disclosure.

## Evidence and current gap

- `docs/architecture/PERMISSIONS.md` requires `file:view` on the source file
  before OCR status, extracted text, words, extraction fields, warnings,
  provenance, or lifecycle detail is disclosed.
- `drive_files` has `workspace_id` and `owner_id`, but no visibility field.
- `workspace_memberships` stores one row per role. Active membership is inferred
  by joining it to active `identity_users` and `workspaces`; the membership
  table itself has no status column.
- The seeded principal has `WORKSPACE_ADMIN` and `DEVELOPMENT_PLACEHOLDER`.
  Those roles are authenticated but are not currently evaluated as file
  capabilities.
- `ResourcePermissionDecision` currently permits only the same-workspace file
  owner. This was a safe bounded remediation, not the persisted sharing policy
  described by `PERMISSIONS.md`.
- There is no user share, group, group-membership, ACL, policy, or permission
  grant table.
- `DriveFileController` currently exposes upload, list, and metadata detail.
  There is no Drive download endpoint. A future download must use the same
  `file:view` boundary before reading a storage key or object.
- `OcrJobRepository.findByIdForWorkspace` and `listByWorkspace` map the whole
  `ocr_jobs` row, including legacy `extracted_text`, before the current owner
  check. Rich OCR and extraction repositories run later, but the legacy text
  load is already too early.
- `docs/architecture/SECURITY.md` and
  `instructions/backend-endpoint-naming.md`, named in the task/instructions,
  are not present in this checkout. This design used
  `docs/development/SECURITY_AND_VULNERABILITY_CHECKS.md` and the implemented
  controller conventions instead.

## Considered approaches

### A. Visibility on `drive_files` plus a file-specific user-grant table

Store the one-to-one base visibility on `drive_files` and store zero or more
user grants in `drive_file_user_grants`. Evaluate owner, workspace visibility,
and active user grants through one authorization service.

This is the approved approach. It keeps the common list predicate cheap, preserves
Drive as the owner of file policy persistence, and avoids a polymorphic ACL
whose other resource types have no implementation.

### B. Separate one-to-one `drive_file_policies` plus a user-grant table

This keeps all policy state outside `drive_files`, but every file requires a
second row and every list/detail path requires an additional join. There is no
independent policy lifecycle that justifies the extra table in the MVP.

### C. General resource ACL/ABAC tables

A polymorphic resource table, subjects, conditions, roles, inherited policies,
and deny grants could model future products. It is rejected because it creates
an unneeded platform before Drive has a complete two-mode policy.

## Persisted visibility model

### Supported modes

The initial persisted modes are exactly:

| Stored value | Meaning                                                                                                                      |
| ------------ | ---------------------------------------------------------------------------------------------------------------------------- |
| `private`    | Only the active file owner or an active explicitly granted user may view. No workspace role bypasses private visibility.     |
| `workspace`  | Active members with a workspace-content role may view; an explicit user grant may also allow an otherwise ineligible member. |

There is no `shared` mode. A private file with one or more active user grants is
specifically shared while its base visibility remains `private`. There is no
`public` mode or public link in this design.

### Storage location and defaults

Add `visibility` directly to `drive_files`. It is mandatory, constrained, and
represented in Java by `DriveFileVisibility.PRIVATE` and
`DriveFileVisibility.WORKSPACE`.

Approved defaults:

- existing rows migrate to `private`;
- newly uploaded files default to `private` in both Java and the database.

This is the least-privilege choice and preserves the observable behavior of the
current owner-only remediation. It also aligns with the existing private object
storage posture. Choosing `workspace` for either population is a product policy
change, not a migration convenience.

### Migration

Create one forward-only Flyway migration:

`apps/api/src/main/resources/db/migration/V9__drive_file_visibility_and_user_grants.sql`

The visibility portion is:

```sql
alter table drive_files
  add column visibility varchar(32) not null default 'private';

alter table drive_files
  add constraint drive_files_visibility_valid
  check (visibility in ('private', 'workspace'));

alter table drive_files
  add constraint drive_files_file_workspace_unique
  unique (file_id, workspace_id);

create index drive_files_workspace_owner_created_at_idx
  on drive_files (workspace_id, owner_id, created_at desc);

create index drive_files_workspace_visibility_created_at_idx
  on drive_files (workspace_id, visibility, created_at desc);
```

The non-null default backfills existing PostgreSQL and H2 rows. The application
must still pass `private` explicitly on new inserts so its intent does not
depend on a database default. The composite uniqueness is required by the
grant table's workspace-lineage foreign key; `file_id` remains the primary key.

## Explicit user sharing

### Group decision

No group or group-membership domain exists in code or migrations. Group grants
are therefore explicitly deferred. V9 must not add `grantee_type`, nullable
group columns, placeholder group IDs, or an unevaluated group-grant table.

Adding groups later requires an approved identity/workspace group design with:

- workspace-scoped group identity;
- active group membership and removal semantics;
- group administration authorization;
- group deletion and audit behavior; and
- a new migration and authorization-decision case.

### User grant schema

V9 creates:

```sql
create table drive_file_user_grants (
  grant_id varchar(64) primary key,
  workspace_id varchar(128) not null references workspaces (workspace_id),
  file_id varchar(64) not null,
  grantee_user_id varchar(128) not null references identity_users (user_id),
  action varchar(64) not null,
  granted_by_user_id varchar(128) not null references identity_users (user_id),
  granted_at timestamp with time zone not null,
  revoked_by_user_id varchar(128) references identity_users (user_id),
  revoked_at timestamp with time zone,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  constraint drive_file_user_grants_file_workspace_fk
    foreign key (file_id, workspace_id)
    references drive_files (file_id, workspace_id)
    on delete cascade,
  constraint drive_file_user_grants_action_valid
    check (action = 'file:view'),
  constraint drive_file_user_grants_revocation_pair
    check (
      (revoked_at is null and revoked_by_user_id is null)
      or
      (revoked_at is not null and revoked_by_user_id is not null)
    ),
  constraint drive_file_user_grants_revocation_order
    check (revoked_at is null or revoked_at >= granted_at),
  constraint drive_file_user_grants_target_unique
    unique (workspace_id, file_id, grantee_user_id, action)
);

create index drive_file_user_grants_grantee_lookup_idx
  on drive_file_user_grants (
    workspace_id, grantee_user_id, action, revoked_at, file_id
  );

create index drive_file_user_grants_file_lookup_idx
  on drive_file_user_grants (
    workspace_id, file_id, action, revoked_at
  );
```

The initially supported grant action is exactly `file:view`. Adding `file:edit`,
`file:share`, or `file:manage` requires another policy decision and migration;
callers must not infer them from `file:view`.

`grant_id` is the stable identity of the file/user/action relationship. Regrant
reactivates the same row by replacing `granted_by_user_id` and `granted_at`,
clearing the revocation fields, and updating `updated_at`. The immutable
`audit_records` entries preserve each grant, revoke, and regrant transition.

The current multi-role membership key prevents a direct foreign key from
`(workspace_id, grantee_user_id)` to `workspace_memberships`. The grant command
must use an insert/update guarded by an active same-workspace membership query.
The decision query repeats that active-membership check, so a retained grant
never authorizes a disabled or removed member.

Grant and regrant commands are concurrency-idempotent. PostgreSQL uses one
`insert ... on conflict on constraint
drive_file_user_grants_target_unique do update` statement. The conflict branch
keeps the existing `grant_id` and `created_at`, replaces
`granted_by_user_id`/`granted_at`, clears both revocation fields, and updates
`updated_at`. The statement returns the resulting row. A valid concurrent
duplicate command therefore receives an insert-or-update outcome without
creating a second relationship or surfacing a spurious conflict.

```sql
insert into drive_file_user_grants (
  grant_id,
  workspace_id,
  file_id,
  grantee_user_id,
  action,
  granted_by_user_id,
  granted_at,
  revoked_by_user_id,
  revoked_at,
  created_at,
  updated_at
) values (
  :grant_id,
  :workspace_id,
  :file_id,
  :grantee_user_id,
  'file:view',
  :granted_by_user_id,
  :granted_at,
  null,
  null,
  :created_at,
  :updated_at
)
on conflict on constraint drive_file_user_grants_target_unique
do update set
  granted_by_user_id = excluded.granted_by_user_id,
  granted_at = excluded.granted_at,
  revoked_by_user_id = null,
  revoked_at = null,
  updated_at = excluded.updated_at
returning *;
```

H2 2.4 PostgreSQL mode does not implement `on conflict do update`, so this
repository's upsert and concurrency tests run against PostgreSQL Testcontainers.
H2 remains suitable for authorization-decision and other repository tests that
do not exercise the production-specific upsert. The implementation must not
replace the PostgreSQL atomic statement with a catch-and-retry inside the same
transaction, because a unique violation aborts that transaction.

### Grant mutation and audit

The first implementation does not add public sharing endpoints or UI. It adds a
transactional Drive command boundary that can be invoked by a later controller:

```java
DriveFileUserGrant grantView(
    AuthenticatedPrincipal principal, String fileId, String granteeUserId);

DriveFileUserGrant revokeView(
    AuthenticatedPrincipal principal, String fileId, String granteeUserId);

DriveFileMetadata changeVisibility(
    AuthenticatedPrincipal principal, String fileId, DriveFileVisibility visibility);
```

The approved share-management policy is file-owner only.
`INSTANCE_OWNER` and `WORKSPACE_ADMIN` do not implicitly manage a private file.
No public sharing route or sharing UI is added in this MVP.

Each successful visibility, grant, revoke, or regrant mutation is in the same
database transaction as one audit row. Suggested actions are:

- `drive.file.visibility_changed`;
- `drive.file.view_granted`; and
- `drive.file.view_revoked`.

Audit resource type is `file`; resource ID is the opaque file ID. Attributes
may contain only `grantId`, `granteeUserId`, `action`, and old/new visibility.
They must not contain a filename, email, storage key, MIME-derived content,
file/OCR content, extracted values, warning text, or grant-target display name.
Ordinary allow/deny reads do not create high-volume audit rows. They may emit
content-free metrics tagged by resource type, action, allow/deny, and decision
basis.

## Authorization precedence

### Workspace-content roles

For `workspace` visibility, the approved roles that carry workspace file
content access are:

- `INSTANCE_OWNER`;
- `WORKSPACE_ADMIN`;
- `DEVELOPER`;
- `EDITOR`; and
- `VIEWER`.

`GUEST`, `AUDITOR`, and `DEVELOPMENT_PLACEHOLDER` alone do not make a
workspace-visible file viewable. A Guest or Auditor can be deliberately given
an explicit user `file:view` grant. `DEVELOPMENT_PLACEHOLDER` has no production
capability; the seeded user is eligible because it also has `WORKSPACE_ADMIN`.

No role bypasses a private file.

### Evaluation order

1. Reject an unauthenticated principal or principal/request workspace mismatch.
2. Resolve only authorization facts for `fileId + workspaceId`.
3. Reject a missing file.
4. Reject a disabled user, disabled workspace, or removed membership.
5. Allow an active same-workspace owner.
6. Do not apply any role bypass for a private file.
7. Allow `workspace` visibility when at least one active membership role is in
   the workspace-content role set.
8. Allow an active same-workspace explicit user `file:view` grant.
9. Deny.

Owner precedes workspace visibility, which precedes explicit grant only to
produce a deterministic internal decision basis. All three are allow rules;
there are no explicit deny grants in this model.

### Decision table

| Case                                         | Other facts                                                 | Result                  | Internal basis/code          | HTTP behavior                                                                                                       |
| -------------------------------------------- | ----------------------------------------------------------- | ----------------------- | ---------------------------- | ------------------------------------------------------------------------------------------------------------------- |
| Active file owner                            | Same active workspace membership                            | Allow                   | `ALLOW_OWNER`                | Continue                                                                                                            |
| Admin or instance owner, private file        | Not owner; no active user grant                             | Deny                    | `DENY_NO_POLICY`             | Non-enumerating 404                                                                                                 |
| Developer/editor/viewer, private file        | Not owner; no active user grant                             | Deny                    | `DENY_NO_POLICY`             | Non-enumerating 404                                                                                                 |
| Workspace-content role, workspace file       | Active same-workspace membership                            | Allow                   | `ALLOW_WORKSPACE_VISIBILITY` | Continue                                                                                                            |
| Guest/auditor only, workspace file           | No active user grant                                        | Deny                    | `DENY_NO_POLICY`             | Non-enumerating 404                                                                                                 |
| Active explicit user grant                   | Same workspace; private or workspace file                   | Allow                   | `ALLOW_USER_GRANT`           | Continue                                                                                                            |
| Active explicit user grant for Guest/Auditor | Active same-workspace membership                            | Allow                   | `ALLOW_USER_GRANT`           | Continue                                                                                                            |
| Revoked grant                                | No owner/workspace allow path                               | Deny                    | `DENY_NO_POLICY`             | Non-enumerating 404                                                                                                 |
| Workspace visibility plus user grant         | Eligible workspace-content role                             | Allow                   | `ALLOW_WORKSPACE_VISIBILITY` | Continue                                                                                                            |
| Owner plus grant                             | Active owner                                                | Allow                   | `ALLOW_OWNER`                | Continue                                                                                                            |
| Removed membership                           | Grant or ownership remains persisted                        | Deny                    | `DENY_INACTIVE_MEMBERSHIP`   | Normal header principal: authentication 403. A stale/synthetic principal reaching the service: resource 404         |
| Disabled user or workspace                   | Any file/grant facts                                        | Deny                    | `DENY_INACTIVE_MEMBERSHIP`   | Normal header principal: authentication 403. A stale/synthetic principal reaching the service: resource 404         |
| Principal/request workspace mismatch         | Any resource ID                                             | Deny                    | `DENY_WORKSPACE_MISMATCH`    | Authentication 403 when no requested-workspace membership exists; internal/stale caller maps denial to resource 404 |
| File belongs to another workspace            | Requested workspace lookup has no row                       | Deny                    | `DENY_RESOURCE_NOT_FOUND`    | Same 404 as missing/denied                                                                                          |
| Missing source file                          | OCR reference exists, file does not                         | Deny                    | `DENY_RESOURCE_NOT_FOUND`    | Same OCR 404; no content read                                                                                       |
| Active and revoked grant conflict            | Prevented by one-row uniqueness and paired revocation state | No conflict can persist | Constraint violation         | Mutation fails atomically                                                                                           |
| Group grant                                  | Groups not implemented                                      | Deny/no policy path     | `DENY_NO_POLICY`             | No group API                                                                                                        |

An active member may have multiple role rows. Any qualifying workspace-content
role is sufficient for a workspace-visible file. Roles never negate an owner or
explicit user grant because this model has no deny grants.

## Reusable authorization boundary

### Public port and result

Create the following common-security types:

```java
public enum ResourceType {
  FILE
}

public enum ResourceAction {
  VIEW
}

public enum AuthorizationDecisionCode {
  ALLOW_OWNER,
  ALLOW_WORKSPACE_VISIBILITY,
  ALLOW_USER_GRANT,
  DENY_UNAUTHENTICATED,
  DENY_WORKSPACE_MISMATCH,
  DENY_RESOURCE_NOT_FOUND,
  DENY_INACTIVE_MEMBERSHIP,
  DENY_NO_POLICY,
  DENY_UNSUPPORTED
}

public record AuthorizationDecision(
    boolean allowed, AuthorizationDecisionCode code) {}

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

The singular method is the reusable authorization port. The batch method has
identical semantics and exists only so list paths do not create an N+1 query or
load protected metadata before filtering. V1 supports only `FILE + VIEW`;
unsupported combinations deny rather than default-allow.

`AuthorizationDecisionCode` is internal. Controllers and response DTOs must
never serialize it. Resource detail and OCR callers map every non-allowed
resource decision to their existing not-found exception.

### Authorization facts adapter

Drive owns the file-policy SQL through:

```java
interface FileViewAuthorizationRepository {
  Optional<FileViewAuthorizationFacts> findFacts(
      String actorId, String workspaceId, String fileId);

  Set<String> findAllowedFileIds(
      String actorId, String workspaceId, Collection<String> fileIds);
}

record FileViewAuthorizationFacts(
    String fileId,
    String workspaceId,
    String ownerId,
    DriveFileVisibility visibility,
    boolean activeMember,
    boolean workspaceVisibilityRole,
    boolean activeUserGrant) {}
```

`findFacts` selects only policy columns and `exists` results. It never selects
encrypted names, storage keys, checksums, OCR data, or object bytes. The outer
query is anchored only on `drive_files` by `file_id + workspace_id`. Actor,
workspace, membership, role, and grant state are computed by correlated
`exists` expressions or left-derived booleans; they must not be inner-joined
through the outer `where` clause.

This distinction is required for deterministic inactive-state decisions:

- an existing same-workspace file returns one facts row even when the
  persisted actor is disabled, the workspace is disabled, or membership was
  removed;
- those cases set `activeMember=false`, and both
  `workspaceVisibilityRole=false` and `activeUserGrant=false`;
- a missing file or a file in another workspace returns no row; and
- only no-row results map to `DENY_RESOURCE_NOT_FOUND`.

Conceptually, `activeMember` is:

```sql
exists (
  select 1
  from identity_users u
  join workspaces w on w.workspace_id = f.workspace_id
  where u.user_id = :actor_id
    and u.status = 'active'
    and w.status = 'active'
    and exists (
      select 1
      from workspace_memberships wm
      where wm.workspace_id = f.workspace_id
        and wm.user_id = u.user_id
    )
)
```

The role and active-grant booleans include the same active actor/workspace/
membership predicate. `findAllowedFileIds` may apply the complete allow
predicate directly because its output contains allowed IDs only.

`findAllowedFileIds` applies the same predicate to a bounded candidate ID list
and selects file IDs only. Tests must prove batch and singular decisions agree
for every row in the decision table.

## Non-enumerating response behavior

- A normal header principal that cannot establish an active workspace session
  continues to receive the authentication boundary's current 403. No file or
  OCR lookup runs, so the response does not reveal a resource.
- The seeded development fallback can synthesize the default principal when
  its persisted session row is absent. The authorization repository therefore
  rechecks persisted active membership. If that stale/synthetic principal
  reaches a resource path, it is denied and mapped to the same resource 404;
  ownership or a retained grant cannot bypass the recheck.
- An authenticated active member requesting a missing, foreign-workspace,
  private-unshared, revoked, or otherwise denied Drive file receives the same
  404 `NOT_FOUND` body: `Drive file was not found`.
- The corresponding OCR cases receive the same 404 `NOT_FOUND` body:
  `OCR job was not found`.
- Drive and OCR list endpoints return 200 with only authorized rows. An empty
  authorized result is an empty list, not a permission error.
- A future download uses the Drive 404 behavior for missing and denied files.
- Internal decision codes are available to tests and content-free metrics but
  never to clients.

## Protected-load ordering

### Drive upload

An upload creates a file owned by the authenticated actor with explicit
`private` visibility. The response may be built from the just-created in-memory
metadata because the actor is the owner. The outbox event remains unchanged and
does not include visibility or grant data.

### Drive list

```txt
principal
  -> workspace file IDs only
  -> batch file:view decision
  -> load metadata for allowed IDs only
  -> decrypt allowed filenames
  -> map responses
```

Denied-file encrypted names, checksums, storage keys, or other metadata are not
loaded into response-mapping objects.

### Drive detail and future download

```txt
principal + workspace + file ID
  -> file:view authorization-facts query
  -> deny as 404
  -> load allowed Drive metadata
  -> detail: decrypt name and map response
  -> future download: load/decrypt object and audit download
```

Object storage must never run before the allow decision.

## OCR integration

### Repository split

Add a non-content record:

```java
public record OcrJobSourceReference(
    String jobId, String fileId, String workspaceId) {}
```

`OcrJobRepository` adds:

```java
List<OcrJobSourceReference> listSourceReferencesByWorkspace(String workspaceId);

Optional<OcrJobSourceReference> findSourceReferenceByIdForWorkspace(
    String jobId, String workspaceId);

List<OcrJobSummary> findSummariesByIdsForWorkspace(
    Collection<String> jobIds, String workspaceId);

Optional<OcrJob> findDetailByIdForWorkspace(String jobId, String workspaceId);
```

The two source-reference queries select only `job_id`, `file_id`, and
`workspace_id`. `OcrJobSummary` contains response metadata but excludes
`extracted_text`, `failure_message`, and `storage_key`.
`findDetailByIdForWorkspace` is the only legacy row mapper that may select
`extracted_text`, and its name makes the protected load explicit.

### Detail flow

```txt
authenticated principal
  -> non-content OCR source reference by job ID + principal workspace
  -> source file:view decision
  -> on denial, throw the same OCR 404
  -> allowed Drive metadata for the filename
  -> full OCR job row, including legacy extracted_text
  -> rich OCR document/pages/words
  -> invoice extraction/fields/warnings/provenance
  -> lifecycle projection
  -> response mapping
```

The following must not run on denial, foreign workspace, missing job, or
missing source:

- `DriveFileRepository.findByIdForWorkspace`;
- filename decryption;
- `OcrJobRepository.findDetailByIdForWorkspace`;
- `JdbcOcrResultRepository.findByJobIdForWorkspace`;
- `JdbcInvoiceExtractionRepository.findByOcrJobIdForWorkspace`;
- `OcrJobLifecycleProjectionService.project` and its query repository;
- legacy extracted-text fallback selection;
- rich word, field, warning, provenance, or lifecycle response mappers.

### List flow

```txt
authenticated principal
  -> non-content source references for the workspace
  -> batch source file:view decision
  -> safe OCR summaries for allowed job IDs only
  -> allowed Drive metadata for filenames only
  -> OCR-result presence and extraction-summary projections for allowed job IDs
  -> response mapping
```

The list path must not select legacy `extracted_text`, rich OCR text/pages/words,
extraction values/warnings/sources, or denied-file metadata.

## Search integration

Search is a user-facing disclosure surface for normalized extraction data and
is part of CV-04 closure. The current `SearchService` scopes both PostgreSQL and
Meilisearch results only by workspace, which is insufficient for private or
user-shared Drive files.

The existing indexing lineage is sufficient for the smallest enforcement:

- `WorkflowRunner` writes the source Drive file ID to
  `search_documents.metadata_json.fileId` when it creates a normalized
  extraction search document;
- `MeilisearchIndexClient` copies the complete metadata object into the
  indexed document; and
- `MeilisearchSearchClient` copies that metadata into its internal result.

Task 5 must make `SearchService`, for each backend, collect the distinct,
non-blank `fileId` lineage from its candidates, call
`allowedResourceIds(principal, workspaceId, FILE, fileIds, VIEW)`, and retain
only candidates whose source file ID is allowed. Filtering occurs separately
on local and Meilisearch candidates before merge, backend labeling, or
construction of the public `SearchResponse`.

All currently supported normalized extraction search documents are
Drive-derived. A candidate with missing, blank, malformed, or foreign-workspace
`metadata.fileId` is therefore omitted fail-closed. A future non-file search
source must introduce an explicit source classification rather than inheriting
an allow-by-default path.

Search denial is non-enumerating omission from the normal 200 search result,
not a per-hit 404. Tests must cover private-unshared, revoked, foreign,
inactive-member, missing-lineage, PostgreSQL fallback, Meilisearch, and merged
results. CV-04 cannot be recorded as passing unless both backends are filtered
by the same source-file decision. If either path cannot be verified, the
verification report must keep the Search disclosure case explicitly open.

This MVP filter governs user-facing disclosure; it does not redesign trusted
Search index residency or the worker's existing indexing authority. PostgreSQL
and Meilisearch may produce candidate objects inside the API process before
the batch filter, but no denied candidate may be merged, serialized, counted
into the public backend result, or returned to the web client.

## Compatibility and migration behavior

### Existing Drive behavior

- Existing files become private and remain visible to their active owners.
- Same-workspace unshared members continue not to see private files.
- Workspace-visible and user-shared files are newly supported.
- List ordering remains newest first after authorization.
- Upload/list/detail routes remain unchanged.
- There is still no download route; the authorization contract for one is
  defined now to prevent a later bypass.

### Seeded and legacy data

- V9 creates no seeded shares and no group rows.
- The seeded demo user remains able to see its own existing and newly uploaded
  private files.
- Existing test helpers must insert active identity/workspace/membership rows
  before creating user grants.
- Orphaned private files whose owner is no longer an active member are
  intentionally inaccessible. Owner-only sharing with no administrative
  content bypass can therefore leave files inaccessible when the owner is
  disabled or removed. This is an accepted MVP limitation, not a reason to
  introduce an implicit `INSTANCE_OWNER` or `WORKSPACE_ADMIN` bypass.
  Administrative ownership transfer or audited break-glass recovery requires
  a separate approved design.

### API and web

Add an additive `visibility` field with values `private` or `workspace` to
Drive upload, list, and detail responses. It exposes policy state without
exposing another user's grants.

The web `DriveFile` transport type and mock fixtures should accept the field,
but the current MVP does not add a sharing modal, visibility editor, role
manager, or new responsive UI. Existing screens may continue rendering the
same encrypted-file presentation. User-facing share controls are a separate
approved product slice.

### Events and workers

No event schema changes are required. `FileUploaded` remains metadata-only and
background OCR processing continues independently of interactive `file:view`.
Visibility and grants govern disclosure, not whether the existing trusted
worker processes an uploaded source.

### Rollback

Flyway remains forward-only. The safe application rollback is:

1. roll back the application binary;
2. retain V9 schema and data; and
3. do not drop visibility or grants.

The pre-V9 binary ignores the additive response field and extra table. Its
old insert omits `visibility`, so the database default still creates private
files. It also ignores workspace visibility and grants, causing an availability
regression to owner-only access rather than a confidentiality leak.

Dropping V9 would destroy sharing state and is not an ordinary rollback. If a
forced schema reversal is ever approved, export grant and visibility data
first, set all files private, and restore only from a verified backup.

### Residual scope

This design does not add source-derived disclosure to Knowledge,
notifications, audit-list authorization, or workflow execution. Those
surfaces must not expose raw OCR/file content under existing rules. Search is
in scope and must satisfy the preceding fail-closed source filter before CV-04
is closed.

## Approved product decisions

The following decisions were approved on 2026-07-29:

1. **Existing-file default:** `private`.
2. **New-upload default:** explicitly `private`.
3. **Private-file role bypass:** none, including no automatic
   `INSTANCE_OWNER`, `WORKSPACE_ADMIN`, editor, or other role bypass.
4. **Workspace-visible roles:** active `INSTANCE_OWNER`, `WORKSPACE_ADMIN`,
   `DEVELOPER`, `EDITOR`, and `VIEWER`; `GUEST` and `AUDITOR` require an active
   explicit user grant.
5. **Groups:** deferred until a real workspace-group and group-membership model
   is separately approved.
6. **Share managers:** active file owner only for grant, revoke, and visibility
   commands; no public sharing endpoint or UI in this MVP.
7. **Denial semantics:** session/authentication failures remain 403.
   Authenticated missing, foreign, revoked, private-unshared, and otherwise
   denied resources use identical non-enumerating 404 responses. Search denies
   individual hits by omission from the normal 200 result.
8. **API visibility field:** additive field in the Drive API and web transport
   type now, without sharing UI.
9. **Owner-loss limitation:** an owner-disabled or owner-removed private file
   may become inaccessible. This is accepted for MVP; ownership transfer and
   audited break-glass recovery are separate future designs.

## Future decisions outside this MVP

No unresolved product decision blocks the approved five-task implementation.
Separate approval is still required before adding workspace groups, ownership
transfer, audited break-glass recovery, public sharing endpoints/UI, public
links, additional grant actions, or a non-file Search source classification.
