# MVP milestones and specification architecture

**Status:** Milestone scope approved 2026-10-18; Drive folder amendment awaiting written review
**Date:** 2026-10-18
**Evidence baseline:** `origin/develop` at `9d3b856` (merged PR #47)

## Purpose

Open Ecosystem OS is a self-hosted personal document workspace that can later
become a distributable, publicly demonstrable open-source product. Its first
milestone must solve real problems for its owner, not merely demonstrate a
pipeline or show polished mock screens.

This design replaces the **release boundaries** in earlier MVP plans. Existing
historical specs, code, and tests remain valuable evidence, but a former
"MVP" label does not override the milestones agreed here.

## Milestones

### MVP Core — personally usable, private

Deliver five end-to-end capabilities:

1. **Drive:** upload, list, download, preview, rename, move, and delete files;
   create, navigate, rename, move, and recursively delete nested folders;
   download whole folders (including their subfolders) as ZIP archives.
2. **OCR:** process supported PDFs/images and inspect extracted text.
3. **Images to PDF:** select multiple images, set page order, generate a PDF,
   and save/download the output.
4. **Invoice extraction:** use a built-in Drive action to extract structured
   invoice data, review, correct, and explicitly confirm it.
5. **Search:** find filenames and raw OCR text independently of invoice
   confirmation; find **structured invoice fields only after confirmation**.

These are real data-backed capabilities, not simulated screenshots. They must
work after restarts and handle empty/error/loading states. The existing
workflow engine, event outbox, notification and audit support may serve these
features without requiring their own elaborate user-facing applications.

Core runs in a trusted private/local environment with seeded development
identity; that identity is **not authentication and must never be exposed as a
publicly accessible instance**. Use local/offline Tesseract OCR and local
heuristic extraction by default. No cloud AI dependency or outbound document
processing is required.

### MVP Complete — safe distribution and public demonstration

Build on Core with real authentication and sessions; multiuser/workspace
onboarding and authorization; install/configuration and upgrade documentation;
safe, isolated public demonstration; reasonable operator controls, recovery,
data export/restore, and production-facing security/quality validation.
Public portfolio/docs should explain genuine features and the architecture.
No public access to Core's seeded-header identity flow is permitted.

MVP Complete is a **release-readiness** milestone, not a requirement to
implement every app in the original vision. Feature expansions require their
own separately approved specifications.

### Post-MVP — additional products and extensibility

Open Pages, advanced PDF editing (redaction, split, annotations, signing),
visual workflow authoring, Open Ledger, plugin marketplace, integrations,
community, billing, music/video and other proposed apps remain later work.
Simple **image-to-PDF merging** belongs to Core; it does not entail a general
PDF editor. Existing Flows UI may stay accessible, but a configurable workflow
builder is **not** a Core completion criterion.

## Core user journeys

1. Create a folder and nested subfolder → upload/move/rename private files
   → browse by folder → preview/download a file or download the whole folder
   as ZIP → delete files or folder trees and their derived/searchable data.
2. Upload a supported image or PDF → inspect asynchronous OCR state and text
   → search the file and its OCR text.
3. Select two or more images in Drive → reorder → run the predefined
   conversion → open/download the generated PDF from Drive.
4. Select an invoice image or PDF → run the predefined invoice extraction →
   view the original alongside detected fields and warnings → edit or confirm
   values → search the confirmed structured data.

## Architecture and separation of responsibilities

- **Web (Next.js):** Drive-first entry point, built-in action controls, file
  preview, OCR/job progress, invoice review form, search/results. All relevant
  views remain usable on desktop and mobile.
- **API (Spring Boot modular monolith):** file permissions and metadata,
  secured content delivery, conversion/extraction initiation, draft edits,
  confirmation, search projections, typed outbox events and audit metadata.
- **Worker:** bounded, asynchronous OCR and PDF assembly with encrypted
  source retrieval and safe temporary-file cleanup.
- **PostgreSQL:** transactional source of truth for folders and file placement,
  files, jobs, OCR results, drafts, confirmed extraction records, workflow
  executions and index states.
- **Private MinIO/S3:** encrypted originals and generated documents; avoid
  storing document binaries in database rows.
- **RabbitMQ:** asynchronous work with idempotent event consumption,
  retry/failure state and observability.
- **Meilisearch with safe local fallback:** a derived, rebuildable search
  projection; permission checks enforced by the API on every result.

Use the already implemented modular boundaries rather than creating new
deployables for these flows. Built-in operations may reuse the existing Flows
execution engine, but must not depend on the user composing JSON workflows.

## Cross-cutting contracts

- Every content read, preview, transformation, draft edit, confirmation and
  search result must respect workspace scope and the source file's `file:view`
  permission; mutations require an appropriate stronger permission.
- Never place file contents, OCR text, invoice values, plaintext filenames,
  or decrypted object material in events, application logs, notification
  payloads, or audit attributes.
- Preserve originals and provenance; generated PDFs are new Drive files with
  lineage, not silent mutations of input files.
- Use stable file and folder IDs and workspace-scoped parent relationships for
  nesting: renaming/moving must not change object keys or break OCR/invoice
  provenance and search links. Reject cyclic folder moves.
- Validate type/size/page/quantity limits before expensive processing and
  safely reject corrupt or unsupported inputs. File processing is bounded.
- Failures and retries must not create duplicate output files, OCR results,
  invoice confirmations or search records; provide visible job outcomes.
- Search is eventually consistent with completed operations, but an unconfirmed
  invoice draft must **never** become a structured-field search result.
- Tests must cover API authorization, worker/output correctness, concurrency
  and idempotency, end-to-end user journeys, and source deletion/index removal.
- Keep build, lint, formatting, test, security, Docker Compose and existing
  Kubernetes/observability contracts intact. Deferrals must be explicit.

## Modular specifications

The specs below define independent, reviewable workstreams. Implementation
plans are created **only after approval of the written specs**, one feature at
a time:

| Specification                                                  | Contract                                                               |
| -------------------------------------------------------------- | ---------------------------------------------------------------------- |
| [Drive](2026-10-18-mvp-core-drive-design.md)                   | Files, nested folders, rename/move, ZIP export, authorization/deletion |
| [OCR](2026-10-18-mvp-core-ocr-design.md)                       | Native-text-first PDF OCR, progress, results and failure boundaries    |
| [Image-to-PDF](2026-10-18-mvp-core-image-to-pdf-design.md)     | Ordered images, deterministic PDF output and Drive integration         |
| [Invoice review](2026-10-18-mvp-core-invoice-review-design.md) | Private extraction draft, corrections, confirmation and indexing gate  |
| [Search](2026-10-18-mvp-core-search-design.md)                 | Filename/raw OCR/confirmed field indexing, authorization and deletion  |
| [Platform](2026-10-18-mvp-platform-design.md)                  | Reused Flows/events/audit/deployment foundation and Complete readiness |

Current-state evidence and remaining work:
[MVP implementation inventory](../reviews/2026-10-18-mvp-implementation-inventory.md).

## Explicit exclusions and decision precedence

This milestone design supersedes earlier MVP assumptions that Open Pages,
advanced PDF editing, a customizable Flows builder, a mock-only invoice demo,
or a public portfolio must ship in **Core**. The older
`2026-07-10-real-ocr-invoice-extraction-design.md` allowed approved
extraction-only search and prohibited raw OCR indexing at that time; the new
explicit user decision now **requires filename and raw OCR search before
invoice confirmation**, while still prohibiting unconfirmed structured
invoice-field indexing.

## Approval and delivery gates

This document and its linked designs are **proposed specs**, not a claim that
their requirements are implemented. Before implementation: obtain written
spec approval; then prepare and review a focused implementation plan. Mark
progress only when tests and behavior substantiate it.
