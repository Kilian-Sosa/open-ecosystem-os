# Drive directory and upload validation

Task 2 of the [Drive organization plan](../superpowers/plans/2026-10-18-mvp-core-drive-organization.md)
implements the approved [Drive lifecycle requirements](../superpowers/specs/2026-10-18-mvp-core-drive-design.md).
Spec impact: **no spec change**; this implements the existing folder and upload contract.

## API

- `POST /api/drive/folders`: JSON `{name,parentFolderId}`; returns 201 with
  folder ID, parent ID, decrypted name and timestamps.
- `GET /api/drive/folders/contents?folderId=...`: immediate `folders` and
  `files` arrays; omitted or blank ID means root.
- `GET /api/drive/folders/{folderId}`: owner-authorized folder metadata.
- `POST /api/drive/files`: optional multipart `folderId`; omitted means root.
  File responses now include nullable `folderId`. Existing flat file listing,
  file IDs, S3 keys and `FileUploaded` v1 remain compatible.

Folder operations require an active same-workspace owner. Root has no owner;
an active workspace member may create their own private items there. Listing
root filters folders by owner and files by the existing `FILE + VIEW` policy.
Named folder denial and missing IDs both return generic 404 responses. An
explicit file grant does not grant access to its containing folder.

New names reject blank values, dot/dot-dot, separators, controls, Unicode
format characters and names longer than 255 UTF-16 code units. Sibling comparison
uses NFC and `Locale.ROOT` lowercase, followed by NFC again; neither the name
nor its comparison key is persisted in plaintext. Legacy names remain readable
and participate in comparison without being rejected by new-name validation.
File/folder collisions return generic 409 responses.

Creates and uploads retain the workspace row lock from sibling checking
through metadata persistence. Upload storage also occurs inside this transaction
to prevent transient objects for conflict/authorization denials. This trades
workspace mutation throughput for simple serialization. Failed object writes
or database transactions attempt encrypted-object cleanup.

Folder creation records `drive.folder.created` audit metadata with resource,
actor, workspace and correlation IDs, without name/path attributes. It emits
no domain event. Upload metadata, audit and the existing outbox event commit
together.

## Content policy

The configured MIME allowlist and upload limit still apply. The validator adds
a hard 25 MiB source cap and reads at most the lower configured limit plus one
byte before encryption or persistence. An allowed MIME without an implemented
validator fails closed. Extensions do not determine acceptance.

- **PDF:** require a supported PDF version header and final EOF marker;
  PDFBox parses in strict mode and verifies a non-encrypted page tree with
  1–2,000 pages and positive media boxes. Page content streams are read with
  an aggregate 64 MiB decoded-data limit. This is structural validation,
  not PDF/A conformance or malware detection.
- **PNG:** require signature, bounded chunk lengths/count (4,096), correct
  chunk CRCs, IHDR, image data and final IEND; decode with the JDK image reader.
- **JPEG:** require SOI/EOI and successful JDK decoding without repair warnings.
  Both image formats are limited to 16 million pixels before raster allocation.
- **Text:** strict UTF-8, optional BOM; only tab, CR and LF controls are allowed.
  Reject recognized PDF/ZIP signatures disguised as text. HTML-like text remains
  literal text; a future viewer must render it as text, never as markup.
- **DOCX/XLSX/PPTX:** require complete single-volume ZIP central-directory and
  local-entry agreement, CRC/size checks, unique safe paths, matching main-part
  content type/root namespace and the package's office-document relationship.
  Parse every XML/relationship part with DTDs and external entities disabled;
  reject external package relationships and VBA payloads. Limit packages to
  128 entries, 8 MiB per expanded entry, 64 MiB total, XML depth 128 and
  100,000 elements per part. ZIP64 and encrypted packages are rejected.

All rejection responses use generic messages without parser exceptions,
document text or names. No plaintext validation temporary files are created.
Content acceptance does not make a document safe to execute or embed: preview
is separately scoped to Task 3 and the safe frontend viewer to Task 6.

PDFBox 3.0.4 is newly used by the API to reuse the parser already used by the
worker PDF helper. The JDK supplies ZIP, image and XML parsing; no Office
framework is added. The [upstream security advisory list](https://pdfbox.apache.org/security.html)
was checked on 2026-10-20: the listed 2026 traversal advisories affect the
examples artifact, which is not included. Parser input/decoded-data checks are
not a process sandbox or a hard heap/CPU limit for third-party parser internals.

## Verification

From `apps/api`, PowerShell:

```powershell
.\mvnw.cmd '-Dtest=DriveDirectoryNamePolicyTest,DriveUploadContentValidatorTest,DriveFolderControllerTest,DriveFileControllerTest' test
.\mvnw.cmd verify
```

Tests use synthetic PDF/image/Office/text fixtures and real HTTP, database,
encryption, authorization and outbox behavior, replacing only object storage.
They cover nested/empty folders, root placement, collisions and concurrent
creates/uploads, private/foreign denial, authorization before name decryption,
legacy names, supported formats, corrupt bytes and rejection without side effects.
The full suite also exercises the existing PostgreSQL migration/repository tests.
Frontend/responsive behavior, original download/preview, rename/move, ZIP export,
Trash and Search updates remain in subsequent tasks.
