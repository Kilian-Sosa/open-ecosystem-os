# Roadmap

**Current milestone plan — 2026-10-18.** This reorganizes the original
phase catalog below. Proposed acceptance contracts live in
[MVP milestones](../superpowers/specs/2026-10-18-mvp-milestones-design.md),
and evidence-backed state is tracked in the
[implementation inventory](../superpowers/reviews/2026-10-18-mvp-implementation-inventory.md).
The linked written specs still require user review before implementation plans.

## MVP Core: personal, usable and private

| Sequence                      | Outcome                                                                              | Baseline / next boundary                                                            |
| ----------------------------- | ------------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------- |
| 1. Drive lifecycle            | File read/preview/download; nested folders, rename/move, ZIP trees and 30-day Trash  | Upload/list/metadata exist; folders, file delivery, Trash and restore missing       |
| 2. Document search foundation | Filename indexed after upload; OCR text indexed after processing                     | Search API/worker exist; these two independent source indexes missing               |
| 3. Reusable OCR               | Local PDFs/images with visible text/provenance and statuses                          | Real OCR exists; needs Drive-first OCR-only action and E2E verification             |
| 4. Invoice review             | Built-in extraction, editable draft, explicit confirmation, approved-field indexing  | Heuristic extractor exists; review/confirm missing and current index gate incorrect |
| 5. Image-to-PDF               | Reorder selected images, generate and store one encrypted PDF                        | No implemented assembly workflow                                                    |
| 6. Core acceptance            | Repeatable private Compose user journeys, durable data, privacy and regression tests | Infra/CI foundation exists; Core E2E and deletion-race proofs missing               |

Sequence is a suggested dependency order, **not an implementation
authorization**. Create a focused plan for each approved modular spec.
Core does not require a public demo, Open Pages, advanced PDF editor or
user-authored automation builder.

## MVP Complete: distribution and public demo

1. Replace seeded development headers with real authentication/session
   security; add real users, invitations/onboarding and hardened permissions.
2. Provide reproducible installation, configuration, upgrades and
   backup/restore with a demonstrated recovery test.
3. Deploy a publicly accessible **isolated, disposable demo** with fake
   documents and resource controls; expose no real private workspace.
4. Publish accurate public product/docs and self-hosting instructions,
   operational status and a release-quality security/E2E validation path.

These workstreams require their own SDD specs before implementation. MVP
Complete does **not** implicitly include every app from the older phase
catalog.

## Post-MVP product expansion

- Open Pages and collaboration-lite knowledge documents.
- Advanced PDF editing beyond Core's image-to-PDF generation.
- Open Ledger's personal/household finance tracker.
- Rich visual workflow builder, integrations, AI assistant, developer/SDK
  features, marketplace, media apps, community and other later modules.

---

## Historical phase catalog (superseded release boundaries)

The phases below remain a useful record of the original architecture and
product aspirations. Their "MVP" naming and ordering do **not** override
the current milestone scopes above, and a phase's presence does not mean
its deliverables were implemented.

## Phase 0 — Blueprint and repository foundation

Deliverables:

- monorepo created
- docs and decision files added
- DESIGN.md and AGENTS.md added
- Docker Compose scaffold
- Kubernetes scaffold
- initial CI skeleton

## Phase 1 — UI and backend foundations

Deliverables:

- Next.js app shell
- design tokens
- shared UI components
- dashboard page
- Spring Boot API skeleton
- PostgreSQL connection
- health/readiness endpoints
- initial auth placeholder
- Dockerized web/api/worker

## Phase 2 — Drive and object storage

Deliverables:

- file upload
- file metadata
- MinIO storage
- Drive file list
- file detail panel
- FileUploaded event
- audit record for upload

## Phase 3 — Media/OCR pipeline

Deliverables:

- OCR job entity
- queue-based worker
- job status lifecycle
- extracted text storage
- OcrCompleted/OcrFailed events
- Media/OCR UI
- search indexing of OCR text

## Phase 4 — Open Ecosystem Flows MVP

Deliverables:

- workflow definition model
- workflow versions
- manual trigger
- file-upload/OCR-completed trigger
- basic action nodes
- execution history
- retries
- dead-letter handling
- workflow run detail UI

## Phase 5 — Flagship invoice automation demo

Deliverables:

- seeded invoice PDF
- OCR processing
- AI/mock structured extraction
- workflow automation
- notification
- audit log
- search result
- case study page
- demo reset command

## Phase 6 — Open Pages MVP

Deliverables:

- nested pages
- block editor using existing editor framework
- comments-lite
- search indexing
- AI summary action
- page events

## Phase 7 — Security, permissions, and admin hardening

Deliverables:

- RBAC model
- resource permissions
- sharing modal logic
- security settings
- activity/audit log filtering
- backup/restore MVP

## Phase 8 — Kubernetes and observability

Deliverables:

- Kubernetes manifests or Helm chart
- dev and prod overlays
- Prometheus/Grafana/Loki profile
- metrics dashboards
- worker metrics
- alert rules

## Phase 9 — Extensibility

Deliverables:

- App management
- Event catalog
- API explorer
- Plugin developer portal
- Plugin review queue
- Marketplace MVP

## Phase 10 — Later apps

Candidates:

- music player
- video/Plex-like media app
- advanced analytics
- community forum
- billing/subscriptions for hosted/support plans

## Phase 11 — Open Ledger finance tracker

Open Ledger should be implemented after the core OCR, events, search, notifications, and workflow foundations are stable.

Deliverables:

- Open Ledger app shell and navigation
- dashboard with monthly income, expenses, net balance, savings rate, recent transactions, rules, receipt queue, and insights
- manual expense/income creation
- transactions table/list with filters and details panel
- receipt upload/scan review queue
- OCR/AI extraction draft for receipts
- budgets and rules page
- products and prices page with initial product alias support
- reports page with charts and AI summary placeholder
- settings page for household, categories, payment methods, OCR/AI, privacy, and localization
- finance events emitted for audit/search/notifications/workflows
- finance data export/delete flows

This phase should explicitly avoid bank integrations.
