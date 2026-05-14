# MVP Scope

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
4. see a workflow triggered from the event
5. see a notification
6. inspect the audit log
7. search for the extracted content
8. view system health and job status
9. understand the architecture from public/docs pages

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

- Forms and Approvals
- Kanban
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

