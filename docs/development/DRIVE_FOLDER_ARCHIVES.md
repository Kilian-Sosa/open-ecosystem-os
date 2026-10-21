# Protected Drive folder archives

Task 5 implements folder ZIP downloads from the approved Drive lifecycle spec.
Spec impact: **no spec change**. No frontend, schema, worker, event, audit,
upload or original-file contracts change. Task 6 supplies the download UI.

## HTTP contract

`GET /api/drive/folders/{folderId}/download` returns a complete ZIP attachment.
Entries are relative to the selected folder, without an enclosing root entry.
Nested directories, empty subfolders and original file bytes are preserved.
Selecting an empty folder returns a valid empty ZIP. No Drive file is created.

The caller must be authenticated and an active workspace member, and own the
selected folder and every descendant folder. Each descendant file must pass
the existing `FILE + VIEW` policy, including workspace visibility and sharing
grants. Folder ownership does not grant file access. Denied, missing or foreign
resources return a non-enumerating 404; a denied descendant fails the entire
request before content reads or download headers.

Successful responses include `application/zip`, Content-Length, a safe UTF-8
attachment filename, `Cache-Control: no-store`, `X-Content-Type-Options:
nosniff`, `Referrer-Policy: no-referrer` and a restrictive sandbox CSP.
There are no public storage URLs. Failure messages omit filenames, paths,
object keys, content and cryptographic causes.

Statuses:

| Status | Meaning                                                                  |
| ------ | ------------------------------------------------------------------------ |
| 200    | Complete ZIP attachment                                                  |
| 404    | Missing/inaccessible root or descendant                                  |
| 413    | Entry or uncompressed-byte limit exceeded                                |
| 422    | Unsafe/colliding entry names, excessive path length or invalid tree      |
| 503    | Preparation exceeded its budget or the preparing thread was interrupted  |
| 500    | Storage, decryption, integrity, transaction or temporary-storage failure |

Existing authentication failures retain their established statuses.

## Snapshot and bounds

Preflight acquires the same workspace row lock as upload/create/rename/move.
It snapshots and authorizes the complete subtree, validates every relative
path and checks limits before creating temporary files. Each database child
query fetches at most the remaining entry allowance plus one. The transaction
commits before content reads and compression, releasing the mutation lock;
transaction failure therefore cannot orphan a prepared archive. Later additions
are excluded. Snapshot names/paths remain consistent if metadata later moves or
renames. Original-content reads recheck file access and authenticated bytes.

| Environment variable                  | Default                 | Meaning                                                       |
| ------------------------------------- | ----------------------- | ------------------------------------------------------------- |
| `DRIVE_ARCHIVE_MAX_ENTRIES`           | `1000`                  | All file and directory entries; selected root is not an entry |
| `DRIVE_ARCHIVE_MAX_UNCOMPRESSED_SIZE` | `512MB`                 | Sum of original file sizes, checked without overflow          |
| `DRIVE_ARCHIVE_PREPARATION_TIMEOUT`   | `2m`                    | Cooperative preparation budget, including snapshot work       |
| `DRIVE_ARCHIVE_TEMP_DIRECTORY`        | JVM temporary directory | Existing writable parent for private temporary workspaces     |

Limits must be positive. Original reads retain Task 3's per-file cap and
size/checksum/AES-GCM integrity checks. A source-length mismatch aborts the
entire archive. ZIP writing buffers one source file at a time and checks the
budget between traversal steps, reads and 64 KiB writes, and after finalization.
The transaction also receives a timeout. The budget is cooperative: a blocked
database/storage/filesystem operation can delay cancellation, but an archive
that exceeds the budget is never returned successfully. It is not a hard
two-minute wall-clock or process-isolation guarantee.

Entry segments use NFC normalization and the existing directory-name policy.
Also reject colon and unsafe portable filename punctuation, trailing periods
or spaces, absolute paths and separators. Detect case/Unicode collisions
across files and folders. Relative paths are limited to 4096 UTF-8 bytes.
Unsafe legacy names fail the export rather than being silently renamed;
original-file download remains available. Long attachment filenames fall back
to `download.zip`.

## Temporary storage and cleanup

Only the ZIP is spooled; individual plaintext source files are never written.
The ZIP contains plaintext originals within the spec's controlled temporary
storage boundary. Its opaque workspace and file are created atomically with
owner-only permissions: POSIX 0700/0600 or Windows owner-only ACLs. Filesystems
without either permission mechanism fail closed. Use controlled ephemeral
storage, preferably a private memory-backed filesystem, for deployments.

The service removes temporary files after preparation exceptions, interruption,
deadline expiry and fatal unwinding. The controller owns the finished archive
and closes it after transfer, including client/output failures. Preparation
finishes before response headers, so preparation failures cannot leak a partial
ZIP. A network failure during transfer can still truncate the client's download.
Process termination or host failure cannot run Java cleanup; ephemeral-storage
lifecycle must remove abandoned workspaces. Cleanup cannot guarantee physical
erasure or overcome filesystem deletion failures.

## Verification

From `apps/api`, PowerShell:

```powershell
.\mvnw.cmd -o '-Dtest=DriveArchiveServiceTest,DriveFolderControllerTest,DriveFolderRepositoryTest,DriveFileRepositoryTest' test
.\mvnw.cmd -o verify
```

Tests use clearly labelled synthetic data. Service tests inspect real ZIPs and
temporary permissions/cleanup; HTTP tests use real database, encryption and
authorization with fake object storage. Repository tests verify bounded reads;
the full suite also exercises PostgreSQL 16 migrations and locking. No live
MinIO, browser, process-crash recovery or hard-timeout proof is claimed.
