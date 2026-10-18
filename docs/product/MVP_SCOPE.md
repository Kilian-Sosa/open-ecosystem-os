# MVP Scope

**Current release scope (2026-10-18):**
[MVP milestones and requirements](../superpowers/specs/2026-10-18-mvp-milestones-design.md)
are the proposed, reviewable successor to the original MVP blueprint. Read
the [implementation inventory](../superpowers/reviews/2026-10-18-mvp-implementation-inventory.md)
for the evidence-backed current status. These specs await user review and
are not a declaration of implementation completion.

## Current two-milestone definition

### MVP Core — personally usable and private

- Full Drive file lifecycle: upload, list, preview, download, rename, move and
  delete, plus nested folders (create, navigate, rename, move, recursively
  delete and download a whole folder tree as ZIP).
- Real local OCR for supported PDFs and images, with accessible results.
- Drive built-in action to merge an ordered set of images into one saved PDF.
- Drive built-in invoice extraction from PDF/image, with review, correction
  and explicit confirmation before structured-field search indexing.
- File-name search after upload and raw OCR search after processing, even
  before invoice confirmation; only confirmed invoice fields are searchable
  as structured data.
- Retain encrypted storage, permission-aware reads/search, event/worker/audit
  foundations, reliable private Compose operation and testable error states.
- Existing seeded development session is allowed **only in a trusted private
  environment**; it must not be exposed as public authentication.

Core does **not** require a configurable workflow builder, Open Pages, a
general PDF editor, an operational public landing site or real multiuser
authentication. Its built-in actions may reuse the Flows engine.

### MVP Complete — distribution and safe public demo

Extend the functional Core with secure real authentication, workspace/user
onboarding, production-facing permissions, reproducible installation and
upgrades, restore-tested backup/recovery, meaningful operator controls,
public documentation and a safe **isolated synthetic-data demo**.

The Complete milestone is release-readiness, not the entire envisioned
suite of apps. Open Pages, advanced PDF editing, Open Ledger, visual
workflow authoring and the plugin/community ecosystem remain post-MVP.

## Modular SDD source of truth

- [Drive](../superpowers/specs/2026-10-18-mvp-core-drive-design.md)
- [OCR](../superpowers/specs/2026-10-18-mvp-core-ocr-design.md)
- [Image-to-PDF](../superpowers/specs/2026-10-18-mvp-core-image-to-pdf-design.md)
- [Invoice review/confirmation](../superpowers/specs/2026-10-18-mvp-core-invoice-review-design.md)
- [Search](../superpowers/specs/2026-10-18-mvp-core-search-design.md)
- [Shared platform and distribution](../superpowers/specs/2026-10-18-mvp-platform-design.md)

---

## Historical original MVP definition (superseded for release gating)

The following sections document the **original broader MVP vision**. Their
"must-have", "MVP success", and P0/P1 labels are not current Core or Complete
acceptance criteria; use the SDD specifications above when planning new work.

## MVP objective

Ship a coherent vertical slice that proves the ecosystem works end-to-end.

The MVP should not try to implement every designed screen. It should implement the minimum product surface required to demonstrate the flagship workflow and architectural foundations.

## MVP modules

### Must-have

- Public portfolio shell
- Authenticated app shell
- Dashboard
- Drive upload and file list
- Media/OCR job lifecycle
- Open Pages basic page creation/viewing
- Open Ecosystem Flows basic workflow execution
- Global Search basic indexing/search
- Notifications
- Activity/Audit Logs
- User settings and security basics
- Admin/System Status summary
- Docker Compose local environment
- Initial Kubernetes manifests
- External observability stack profile

### Nice-to-have in MVP

- PDF watermark
- PDF metadata deletion
- AI-assisted redaction UI with mock detection
- Theme switching: light/dark/system
- Demo data reset
- Basic API explorer page
- Basic event catalog page

### Explicitly not MVP

- Full Notion-like databases/formulas
- Full real-time collaborative editing
- Full n8n-like visual canvas
- Plugin marketplace
- Plugin SDK sandboxing
- Music player
- Video player
- Billing/subscriptions
- Public community forum
- Advanced admin analytics
- Native mobile app

## MVP success criteria

The MVP is successful when a demo user can:

1. log in to a seeded workspace
2. upload an invoice PDF
3. see an OCR job created and completed
4. see a workflow trigger structured extraction and a `review_required` state when needed
5. see a generic notification and audit record for the lifecycle
6. search approved extracted fields
7. view system health and job status
8. understand the architecture from public/docs pages

The implemented invoice lifecycle is OCR, structured extraction, `review_required` where
needed, generic notification/audit metadata, a Knowledge placeholder, and search. Kanban-task
and approval-request creation are explicitly post-MVP/P2 capabilities.

## MVP technical proof points

- Dockerized apps
- Clear monorepo structure
- Modular backend boundaries
- Typed event model
- Worker queue
- Persistent execution records
- Object storage
- Search indexing
- Audit trail
- Design-system-driven UI
- Basic observability endpoints

---

# Design catalog scope warning

The repository includes mockups and screen specs for the full long-term product vision. Not every designed screen belongs to the MVP.

## MVP / P0-P1 focus

- Dashboard
- Drive
- Media/OCR
- Open Ecosystem Flows basic
- Open Pages basic
- PDF Editor basic
- Global Search
- Notifications
- Activity/Audit Logs
- Security Settings
- System Status
- Backup and Restore
- Self-hosting Installation
- First-time Setup
- Error/Empty/Loading states

## Post-MVP / P2

- Forms and approval-request creation
- Kanban task creation
- Integrations
- Theme Builder
- API Explorer
- Event Catalog
- ADR pages
- Import/Export and Migration
- Case Studies

## Long-term / P3

- Marketplace
- Plugin Developer Portal
- Plugin Review/Admin Flow
- Community Forum
- Billing/Subscriptions
- Music Player
- Video Player
- Full Open Pages databases
- Full visual workflow canvas
- Full plugin ecosystem

Must not implement P2/P3 screens unless the prompt explicitly asks for them.

## Open Ledger scope status

Open Ledger is not part of the first ecosystem MVP. It is a post-MVP workspace app because it depends on stable foundations already planned for the first vertical slice:

- Drive/file storage
- Media/OCR processing
- AI-assisted extraction/classification
- Notifications
- Activity/Audit Logs
- Global Search
- Open Ecosystem Flows

If reprioritized, Open Ledger should start with its own constrained MVP:

1. manual expenses and income
2. people/household members
3. categories/tags
4. payment methods
5. monthly dashboard
6. basic budgets and rules
7. receipt upload/scan
8. OCR/AI extraction draft
9. review-before-save flow
10. reports and export

Explicitly not included in Open Ledger MVP:

- bank connections
- investment tracking
- tax filing
- regulated financial advice
- advanced forecasting
- full product-price intelligence without manual review
- automated external processing without user consent
