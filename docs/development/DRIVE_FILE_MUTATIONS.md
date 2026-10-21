# Drive file and folder mutations

Task 4 of the [Drive organization plan](../superpowers/plans/2026-10-18-mvp-core-drive-organization.md)
implements the approved [Drive lifecycle](../superpowers/specs/2026-10-18-mvp-core-drive-design.md)
rename and move requirements. The API uses the existing seeded private session;
these endpoints do not introduce production authentication.
Configured frontend origins can issue PATCH requests through the existing CORS
policy; other origins remain denied.

## HTTP contract

| Method | Route                                    | JSON body                                         |
| ------ | ---------------------------------------- | ------------------------------------------------- |
| PATCH  | `/api/drive/files/{fileId}/name`         | `{ "name": "Synthetic invoice.txt" }`             |
| PATCH  | `/api/drive/files/{fileId}/location`     | `{ "destinationFolderId": "folder_destination" }` |
| PATCH  | `/api/drive/folders/{folderId}/name`     | `{ "name": "Synthetic invoices" }`                |
| PATCH  | `/api/drive/folders/{folderId}/location` | `{ "destinationFolderId": "folder_destination" }` |

Success returns HTTP 200 and the updated `DriveFileResponse` or
`DriveFolderResponse`, using the existing metadata response shape. A null,
omitted or blank `destinationFolderId` means the workspace root. Moving to the
current parent and renaming to the current name are accepted; successful calls
update the item's timestamp and write an audit record, including repeat calls.

- HTTP 400: invalid new name, including blank, dot/dot-dot, path separators,
  control/format characters or more than 255 UTF-16 code units.
- HTTP 404: missing, foreign-workspace or inaccessible source/destination,
  inactive owner, or an actor who can view a file but does not own it. Errors
  do not include names or paths.
- HTTP 409: same-parent file/folder name conflict, or a folder move into itself
  or its descendants. Names compare using the existing NFC/Locale.ROOT policy.

Only an active resource owner may mutate it. File operations require the
existing FILE/VIEW decision to return `ALLOW_OWNER`; sharing grants and
workspace visibility do not confer mutation rights. Source parent and
destination folders must belong to the actor in the current workspace. Folder
moves also check ownership along the destination ancestry. Root is a private
organization context, without implicit recursive sharing.

## Persistence and concurrency

All four mutations hold the workspace row lock through authorization,
conflict/cycle checks, metadata updates and audit persistence. Uploads and
folder creation already use the same lock. This serializes concurrent moves
and prevents two requests from claiming the same normalized sibling name or
jointly creating a cycle. Conflict checks exclude the item being mutated.

Rename changes only encrypted name, name IV and updated timestamp. Move changes
only parent/folder ID and updated timestamp. Existing legacy names may move
without satisfying new-name validation; their comparison keys still participate
in collision checks. No plaintext comparison keys are persisted.

Stable IDs, content encryption, S3 keys, bytes, visibility, grants, creation
timestamps and OCR/invoice lineage remain unchanged. Moving a folder updates
its parent pointer without rewriting descendant rows or copying objects. No
schema migration or dependency is added.

Successful mutations write `drive.file.renamed`, `drive.file.moved`,
`drive.folder.renamed` or `drive.folder.moved` in the same transaction. Audit
records contain resource/workspace/actor IDs, correlation ID and timestamp;
attributes are empty. Names, paths and storage keys are excluded. No new event
is emitted; filename Search projection updates belong to the separate Search
plan, where the producer and consumer will be implemented together.

## Verification

From `apps/api`, using synthetic fixtures and fake object storage:

```powershell
.\mvnw.cmd '-Dtest=DriveFileMutationServiceTest,DriveFolderControllerTest,DriveFileRepositoryTest' test
.\mvnw.cmd verify
```

Focused tests cover metadata and OCR association preservation, byte-exact
download after rename/move, unchanged subtree rows, encrypted names, root
moves, invalid names, normalized cross-kind collisions, self/descendant cycles,
concurrent renames/moves, foreign workspaces, inaccessible destinations,
inactive owners and view-only actors. Full API verification also runs the
existing PostgreSQL Testcontainers migration/locking tests. No live MinIO or
browser integration is claimed. ZIP export, frontend actions, Trash and Search
indexing remain separate tasks.
