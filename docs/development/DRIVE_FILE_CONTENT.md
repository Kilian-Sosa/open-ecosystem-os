# Protected Drive file content

Task 3 implements the approved Drive lifecycle download and preview requirements.
Spec impact: **no spec change**. The API adds content reads without changing
file IDs, storage keys, permissions, schema, upload events or OCR associations.
The web viewer is Task 6; this document defines its backend contract.

## HTTP contract

- `GET /api/drive/files/{fileId}/download`: original decrypted bytes with
  attachment disposition, recorded MIME type and a safe filename.
- `GET /api/drive/files/{fileId}/preview`: inline PDF, PNG, JPEG or UTF-8 plain
  text. Other upload types return 415 and remain downloadable.

Both routes evaluate the existing `FILE + VIEW` policy before source metadata
or object access. Private owners, workspace visibility and active explicit
grants behave as on metadata reads. Denied, missing and foreign-workspace
resources return non-enumerating 404 responses. Inactive identities or removed
memberships are rejected by existing authentication/authorization policy.

Successful content responses send `Cache-Control: no-store`,
`X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, safe UTF-8
Content-Disposition and `Content-Security-Policy: sandbox; default-src 'none';
frame-ancestors 'self'`. Unsafe legacy filenames fall back to `download`;
filenames never become object URLs. Errors contain no names, content, parser
diagnostics, object keys or cryptographic causes. Expected content failures
become sanitized `ApiException` responses rather than logged stack traces.

## Original-content integrity and bounds

Authorize first, read scoped metadata, then fetch ciphertext. Reads accept at
most the lesser of the configured upload limit and 25 MiB of plaintext; the
ciphertext allowance is exactly recorded size + 16 AES-GCM authentication-tag
bytes. Reject invalid metadata before fetching the object. Verify the recorded
algorithm/key ID, IV, GCM tag, plaintext size and SHA-256 before returning bytes.
No unauthenticated partial plaintext is returned.

S3 reads inspect advertised Content-Length before allocating a body buffer and
also read at most allowance + 1 bytes, so absent or false lengths cannot bypass
the bound. Oversized, truncated or inconsistent objects fail closed, abort the
response stream, and close resources. Missing objects and authentication or
storage failures return a generic 500. Lowering the configured size limit can
make larger previously stored originals unavailable until the limit is restored.
Reads do not create audit-success records or publish new events.

## Preview rendering and isolation

Every preview reuses the Task 2 structural validator, including for legacy
files. Authentically encrypted but malformed/mislabeled source bytes return
422; their original attachment download remains available. A corrupted GCM
tag or checksum fails before either download or preview with a generic 500.

PDF preview **never returns the source PDF**. PDFBox renders each page without
annotations, then writes a fresh PDF containing only newly encoded JPEG page
images. No source catalog, actions, JavaScript, forms, embedded files, links,
metadata, fonts or content streams are copied into that document. All pages
must pass the bounds; the API never silently truncates a preview. Rendering
uses memory-only PDFBox readers/writers and does not create plaintext files.
See the [PDFBox 3 memory/cache behavior](https://pdfbox.apache.org/3.0/migration.html).

PDF limits per request:

- 40 pages, rendered at 72 DPI;
- 4 million pixels per page, 32 million total, maximum dimension 8192 pixels;
- 25 MiB serialized preview;
- a 30-second budget checked between rendered pages;
- one PDF validation/render operation at a time per API instance; concurrent
  attempts return 503 and may retry.

The budget is cooperative, not a hard timeout or process sandbox. PDFBox
parser/renderer internals can exceed these application-level memory/CPU
estimates on adversarial inputs. The existing trusted-private Core deployment
boundary still applies. A process-isolated renderer remains future hardening.
Raster previews trade text selection, interactive forms, annotation appearance,
vector zoom fidelity and document accessibility metadata for inert display.
Original attachment downloads preserve all original bytes and features.

PNG/JPEG are structurally validated and retain their bytes. Text is validated
UTF-8 and served strictly as `text/plain;charset=UTF-8`, including when it
contains HTML-looking text. Headers add defense in depth; the PDF rebuilding
and restricted rendering contexts provide the content-execution policy.

Task 6 must fetch these protected routes and render images as images and text
as escaped text nodes, never HTML. Display only the rebuilt PDF preview, never
the original download as an inline preview. Prefer a canvas PDF viewer without
scripting/forms/link actions; do not depend on sandboxed native PDF plugins,
whose support varies by browser. Revoke temporary blob URLs when closed or
replaced and retain metadata/download fallback on preview errors. No public
object URL or persistent client cache is part of this contract.

## Verification

From `apps/api`, PowerShell:

```powershell
.\mvnw.cmd '-Dtest=DriveFileContentServiceTest,DriveFileControllerTest,S3FileObjectStorageTest' test
.\mvnw.cmd verify
```

Synthetic tests exercise real HTTP, database, encryption and authorization;
only S3 is replaced at that boundary. Storage-specific tests exercise the real
bounded adapter against controlled SDK response streams, including oversized
and false/missing Content-Length responses. Tests compare original bytes,
check active-content removal, render preview pages to verify appearance and
rotation, reject excessive rendering, and check private/foreign/revoked access,
legacy corruption, tag tampering, length/checksum mismatch and safe headers.
