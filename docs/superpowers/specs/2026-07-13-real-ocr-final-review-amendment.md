# Real OCR Final Review Remediation Amendment

**Status:** Approved

**Date:** 2026-07-13

**Applies to:** `docs/superpowers/specs/2026-07-10-real-ocr-invoice-extraction-design.md`

**Reviewed implementation:** `feat/real-ocr-extraction` at `28d5de382813faa48a5bf222d765d29bf557079d`

## Purpose and authority

This amendment resolves the disputed design findings in
`.superpowers/sdd/real-ocr-final-review.md` without reopening the completed real-OCR
architecture. It is a delta to the approved 2026-07-10 design. Requirements in the
original design remain authoritative unless this amendment explicitly replaces them.

The remediation must preserve the existing V7/V8 schema, metadata-only OCR events,
worker/API ownership split, persisted OCR and extraction contracts, duplicate-delivery
fences, Media/OCR route, and test-only status of fake invoice data. No historical
migration is rewritten. No heartbeat or claim-lease schema is introduced.

## Evidence and compatibility

The reviewed implementation already provides the correct outer flow:

```txt
encrypted Drive source
  -> worker claim
  -> structured OCR result
  -> OcrCompleted metadata event
  -> API-owned extraction
  -> notification, audit, Knowledge placeholder, and search
  -> Media/OCR review UI
```

The remediation changes bounded execution, per-page source selection, word structure,
field resolution, list projections, polling, accessibility, runtime tooling, and
verification. It does not change event payload versions, REST routes, persisted table
shapes, or the workflow action order.

## Selected PDF isolation architecture

### Packaging

Add a dedicated lightweight Maven companion under `apps/worker/pdf-helper`. It is a
build-time module and a second executable inside the existing worker image, not a new
service, container, port, queue consumer, or deployable. PDFBox belongs to this helper;
the Spring worker no longer loads untrusted PDFs in-process.

The helper has two commands:

1. `inspect` loads the PDF and returns only the validated page count.
2. `page` reloads the PDF, analyzes one requested page, and returns either structured
   native words or a rendered image in the parent-owned temporary directory.

The helper protocol is versioned JSON on stdout. The parent passes arguments through
`ProcessBuilder(List<String>)`, caps helper/Tesseract stdout at the existing 10 MiB
process-output limit and stderr at 4 KiB, never logs protocol content, and treats malformed
or unsupported protocol output as a fixed sanitized provider failure. The helper writes
only to the exact output path created by the parent.

Invoking one helper process per page intentionally trades repeated PDF loading for the
smallest enforceable page boundary. A hostile load, text extraction, image inspection,
or render is stopped by terminating that page's process. A single long-lived helper and
an in-process executor are rejected because they cannot prove hard per-page termination.

### Parent ownership

The Spring worker remains the security and lifecycle owner. It owns:

- the document and page deadlines;
- helper and Tesseract command construction;
- bounded output draining and process-tree termination;
- page-count, rendered-pixel, word-count, and temporary-storage limits;
- source decryption and the parent-owned temporary directory;
- cleanup retries and content-free cleanup telemetry; and
- mapping all helper, Tesseract, timeout, protocol, and cleanup failures to fixed safe
  codes and summaries.

The helper receives no database, broker, object-store, encryption, workspace, actor, or
network credentials.

## Execution budgets and claim expiry

Add an explicit `documentTimeout` with a default of 10 minutes. It begins immediately
after a claim is acquired and covers permit wait, source read/decryption, PDF inspection,
every helper invocation, Tesseract, parsing, and in-memory result construction. Keep the
existing per-page default of 60 seconds, but apply it to the combined page helper plus
Tesseract work rather than Tesseract alone. Each process receives the smaller of the
remaining page budget and remaining document budget.

Add a 2-minute `persistenceCleanupMargin`. At startup, reject configuration unless all
of these invariants hold:

```txt
documentTimeout > 0
pageTimeout > 0
pageTimeout <= documentTimeout
persistenceCleanupMargin > 0
staleProcessingTimeout > documentTimeout + persistenceCleanupMargin
```

With the bound application and deployment defaults, `15m > 10m + 2m`. The record's
in-memory fallback must use the same 15-minute value. Compose, Kubernetes,
`.env.example`, and bound-configuration tests must carry the same values. A document that remains
inside its legal 10-minute execution budget cannot be reclaimed. Completion, retry, and
failure updates continue to use the existing `processing_started_at` fence.

The margin is reserved for the completion transaction, outbox/audit/consumption writes,
process termination, and temporary cleanup. The worker must not start new untrusted work
after the document deadline. No heartbeat, renewable lease, or schema migration is
approved because the explicit budget and startup invariant preserve the existing claim
semantics.

## Deterministic native-PDF selection

Selection remains page-local and generic. A page produces exactly one source kind:
`pdf_text_layer` or `tesseract_tsv`.

Definitions:

- A usable native token contains at least two Unicode letter-or-digit characters.
- A malformed character is a non-whitespace control, replacement, unpaired surrogate,
  or private-use character.
- Malformed ratio is malformed characters divided by non-whitespace characters.
- Native vertical span is the top-to-bottom span of native word boxes divided by page
  height.
- A substantial raster exists when one displayed raster covers at least 25% of the crop
  box, or the sum of clipped displayed raster areas, capped at page area, reaches 40%.

Native text is minimally usable only when it has at least 20 Unicode letter-or-digit
characters, at least 3 usable tokens, and a malformed ratio no greater than 2%.

When no substantial raster exists, minimally usable native text is retained. When a
substantial raster exists, native text is retained only when it also has at least 80
Unicode letter-or-digit characters, 12 usable tokens, 3 non-empty lines, and 20% native
vertical span. Otherwise the page is rendered and sent to Tesseract.

These thresholds deliberately identify sparse headers, footers, watermarks, partial
accessibility layers, and malformed overlays without looking for invoice labels or field
names. Tests must include a genuine born-digital page, a born-digital page with a small
logo, a scanned page with a substantial image plus a 20-character footer, a partial text
overlay, malformed native text, and a mixed document.

If fallback is selected, native words are discarded for that page. OCR and native words
are never concatenated or merged, preventing duplicate evidence.

## Native word structure

The helper must derive words from PDFBox `TextPosition` data with position sorting enabled.
It must preserve deterministic page order and assign stable positive identifiers:

- block increments at PDF article/content-block boundaries, with block `1` when the PDF
  has no article structure;
- paragraph increments at PDFBox paragraph boundaries within a block;
- line increments at PDFBox line boundaries within a paragraph;
- word increments within a line; and
- page-word and document reading order increment after the stable
  page/block/paragraph/line/word sort.

Native word boxes use the same top-left, render-DPI pixel coordinate system as Tesseract
TSV. PDF point coordinates are translated relative to the crop box and converted with
deterministic floor-for-origin and ceil-for-extent rounding. A box may be absent only
when PDFBox supplies no usable position data for that word. Page text is reconstructed
from the same grouped words, inserting spaces within lines and newlines between lines.

The API continues grouping by page/block/paragraph/line. A born-digital multi-word label
and its same-line or next-line value must therefore behave the same as Tesseract TSV
evidence.

## Extraction ambiguity

For each field, collect all valid explicitly labelled candidates before selecting one.
Normalize each valid candidate using the field's existing normalizer.

- Zero normalized values means the existing missing/invalid behavior applies.
- More than one distinct normalized value means the field is ambiguous, regardless of
  geometric score. Do not persist the field; emit the existing value-free
  `ambiguous_<field>` warning and set `review_required`.
- One normalized value permits geometric score to select the best provenance among
  equivalent candidates for that value. Equal-scoring equivalent evidence uses stable
  page and reading order as the final tie-breaker.

This rule applies to every field, not only amounts.

## Currency evidence

An explicit three-letter ISO 4217 code accepted by `java.util.Currency` is valid. The
only symbol/marker aliases supported by this amendment are:

| Marker      | Currency |
| ----------- | -------- |
| `€`         | `EUR`    |
| `£`         | `GBP`    |
| `US$`       | `USD`    |
| `CA$`, `C$` | `CAD`    |
| `AU$`, `A$` | `AUD`    |
| `NZ$`       | `NZD`    |

Marker matching is case-insensitive for letters and does not infer from locale,
workspace, supplier, language, or other invoice fields. A bare `$`, `¥`, or any other
unlisted symbol is not a currency value. A labelled bare `$` emits the value-free
`ambiguous_currency` warning and makes the extraction `review_required`. The supported
marker map is an explicit immutable constant covered by table-driven tests.

## Remaining Important finding dispositions

### Worker bounds and provenance

- **I-05:** Read PNG/JPEG dimensions with an `ImageReader` before Tesseract decodes the
  file. Reject non-positive dimensions, overflow in checked multiplication, content-type
  mismatch, or pixels above `maxRenderedPixels` before starting Tesseract.
- **I-06:** Add defaults of 10,000 words per page and 100,000 words per document. Enforce
  the page cap while parsing helper/native or TSV output and the document cap before
  appending or persisting a page.
- **I-12:** Probe `tesseract --version` once at startup with a 5-second timeout and 4 KiB
  output cap. Persist the normalized semantic version from the first line. Startup fails
  with a fixed safe code if the configured executable cannot provide a valid version.

### API correctness and list performance

- **I-09:** The list path performs one workspace-scoped OCR-presence projection and one
  workspace-scoped extraction-summary projection for the listed job IDs. Neither query
  selects document/page/word text, field values, warnings, or source words. Detail reads
  retain the current rich repositories.
- **I-13:** The seeded audit action adds `extractionId`, normalized extraction `status`,
  and `reviewRequired`. It continues excluding OCR text, warning text, values, and source
  words.

### Web polling and accessibility

- **I-10:** Use explicit polling sessions rather than deriving polling only from the last
  response. Job discovery polls every 1.5 seconds for at most 30 seconds/20 attempts.
  Queued or processing jobs poll for at most 5 minutes/200 attempts. Completed OCR waiting
  for extraction polls for at most 30 seconds/20 attempts. Reaching a bound stops
  automatic polling, preserves the job/upload context, and presents a manual Refresh
  action; it never reports failure without durable failure state.
- **I-14:** Informational upload/job/extraction changes use one atomic polite status
  region. Upload rejection, upload failure, and polling-expired warnings use an alert
  region. Stable region placement and message-key deduplication prevent repeated
  announcements across list/detail refreshes.

### Runtime tooling

- **I-11:** Delete the obsolete seed/reset scripts and Makefile targets that call removed
  `/api/demo/invoice-automation` endpoints. `smoke-real-ocr` is the supported synthetic
  invoice command.

## Minor finding dispositions

- **M-01:** Parent-owned cleanup retries deletion at most three times with 50 ms between
  attempts. Exhaustion increments a content-free cleanup-failure metric and logs only the
  failure code and correlation ID, never a path or filename. A startup cleanup examines
  at most 100 direct `ocr-` children below the configured worker temp root and removes only
  directories older than 24 hours.
- **M-02:** The encrypted stream limit is checked
  `maxInputBytes + 16` bytes for the AES-GCM tag using checked addition, while the
  independent plaintext limit remains exactly `maxInputBytes`.
- **M-03:** The implemented invoice journey ends at review state, notification, audit,
  Knowledge placeholder, and search. Kanban/approval is labelled P2 rather than current
  behavior.
- **M-04:** Historical verification reports remain unchanged as historical evidence. The
  remediation creates a new dated verification report that names exact commands and exit
  codes, records unavailable checks, and supersedes contradictory merge claims. The two
  trailing-space defects in the original design are corrected as a documentation-only
  formatting change before a new `git diff --check` result is recorded.

## Authorization decision and merge gate

Inspection found no executable per-file ACL or permission-decision service. The implemented
`PlaceholderAuthenticationContext` proves only active workspace membership, and
`OcrJobQueryService` proves only matching workspace-scoped source existence. Stored roles
are not evaluated for Media/Drive capability.

The governing `docs/architecture/PERMISSIONS.md` is stricter: it requires `file:view` on
the source file for OCR status, text, structured words, extraction fields, warnings,
provenance, and lifecycle data; it also describes private and specific-user/group sharing.
Consequently, workspace membership plus a matching workspace-scoped `drive_files` row is
not authorization for OCR or extraction detail.

**CV-04 is approved as a binding remediation gate.** OCR/extraction detail access must
require the source file's existing `file:view` permission decision. The API must make that
decision before it loads structured OCR results, extracted text, invoice fields, warnings,
or provenance. Denied and missing resources must preserve the repository's existing
non-enumerating not-found/denied behavior.

The implementation must reuse the repository's permission service, evaluator, port, or
policy mechanism. It must not introduce a parallel authorization model. Because inspection
did not find an executable `file:view` mechanism, the remediation must first confirm that
gap and, if it remains, create the smallest reusable permission-decision boundary that
implements the documented resource/action rule. This is an explicit architectural gap, not
permission to replace `file:view` with workspace membership.

Tests must cover an authorized same-workspace principal, a same-workspace principal without
`file:view`, a foreign-workspace principal, and a missing source file. They must also prove
that OCR and extraction repositories are not called when authorization fails. CV-04 remains
merge-blocking until this decision boundary and its tests pass.

## Verification requirements

The remediation must add or refresh evidence for:

- killable hostile-PDF inspection and per-page helper termination;
- document/page deadline enforcement, claim invariants, and cleanup after timeout;
- native-versus-OCR quality fixtures and born-digital grouped-word extraction;
- image dimension and word-count caps;
- actual Tesseract version persistence;
- distinct-value ambiguity and the explicit currency marker table;
- metadata-only bounded list queries and safe extraction audit metadata;
- bounded polling plus status/alert semantics;
- PostgreSQL V6-to-V8 migration, lineage, duplicate-delivery, and concurrent-claim tests;
- PowerShell and POSIX real-OCR smoke paths;
- Trivy filesystem/IaC and exact worker-image scans; and
- a new integration verification report with exact commands, exit codes, image digest,
  scanner database freshness, limitations, and the authorization gate outcome.

## Finding coverage

| Finding | Amendment disposition                                      |
| ------- | ---------------------------------------------------------- |
| I-01    | Document budget plus strict startup invariant              |
| I-02    | Independently killable inspect/per-page helper             |
| I-03    | Deterministic raster-aware native quality decision         |
| I-04    | Stable native word grouping/order/bounds                   |
| I-05    | Pre-decode image dimension/pixel validation                |
| I-06    | Per-page and per-document word caps                        |
| I-07    | Ambiguity across all distinct normalized values            |
| I-08    | Bare `$` ambiguous; explicit ISO/marker support            |
| I-09    | Batched metadata-only list projections                     |
| I-10    | Explicit bounded polling sessions                          |
| I-11    | Remove obsolete demo commands                              |
| I-12    | Bounded real Tesseract version probe                       |
| I-13    | Extraction status/review audit metadata                    |
| I-14    | Status/alert live-region semantics                         |
| M-01    | Bounded observable cleanup                                 |
| M-02    | Separate ciphertext and plaintext bounds                   |
| M-03    | Journey corrected to implemented MVP                       |
| M-04    | New truthful verification; historical reports retained     |
| CV-01   | PostgreSQL migration/concurrency verification              |
| CV-02   | Trivy filesystem/IaC/image verification                    |
| CV-03   | POSIX syntax and runtime smoke verification                |
| CV-04   | Source-file `file:view` gate before any OCR detail loading |

## Acceptance boundary

Approval of this amendment approves the technical dispositions above, including the CV-04
`file:view` decision gate, and authorizes the companion remediation plan. It does not
approve implementation, permission weakening, a new schema migration, or edits to
historical verification reports. The branch remains unmergeable until all Important
findings are remediated, verification gaps are closed or explicitly accepted, and CV-04's
approved authorization boundary passes its required tests.
