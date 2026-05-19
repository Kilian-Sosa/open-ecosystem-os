# Product Blueprint — Open Ecosystem OS

## Product vision

Open Ecosystem OS is a self-hosted, open-source productivity ecosystem where users own their files, pages, automations, media processing, search, AI workflows, and operational data.

It is both:

1. a useful personal/team workspace
2. a portfolio-grade engineering showcase

The portfolio should not be a separate marketing shell only. The portfolio should present the ecosystem, its live apps, architecture, design system, deployment model, and technical case studies.

## Product promise

Users can run a private workspace that lets them:

- store and organize files
- edit and process PDFs
- run OCR and media extraction
- write block-based pages
- automate workflows across apps
- search everything
- use AI over their own data
- track notifications and audit logs
- self-host with Docker/Kubernetes
- optionally monitor with Grafana/Prometheus/Loki/Tempo

## Core product areas

### Public portfolio

Purpose: explain the project, prove engineering depth, and invite users/recruiters/contributors.

Pages:

- Home / landing
- About
- Apps overview
- Architecture
- Design system
- Case studies
- Self-hosting installation
- Docs
- Roadmap
- Changelog
- Community
- Contact/profile

### Authenticated workspace

Purpose: daily user productivity.

Apps:

- Dashboard
- Drive
- PDF Editor
- Media/OCR
- Open Pages
- Open Ecosystem Flows
- AI Assistant
- Global Search
- Notifications
- Activity/Audit Logs
- Settings/Security

### Platform/admin

Purpose: self-hosted operations and governance.

Areas:

- Admin dashboard
- Users and roles
- System status
- Backup/restore
- Integrations
- Theme builder
- App management
- Advanced analytics
- Event catalog
- API explorer
- ADR/technical decisions

### Later ecosystem extensions

Not MVP:

- Marketplace
- Plugin developer portal
- Plugin review/admin flow
- Music player
- Video player
- Community forum
- Billing/subscriptions for hosted/support plans

## Product principles

1. Self-hosted first, hosted optional later.
2. Data ownership and exportability are core features.
3. AI must be transparent, permission-aware, and auditable.
4. Automation should be useful before it is visually complex.
5. Observability is external infrastructure, not a product replacement for Grafana.
6. Start modular, extract services only when justified.
7. Every app must connect to at least one real user journey.
8. Design must be minimal, responsive, accessible, and AI-understandable.

## Primary flagship demo

The strongest first demo is the invoice automation journey:

```txt
Upload PDF invoice
  -> OCR pipeline extracts text
  -> AI/mock extractor extracts invoice fields
  -> Open Ecosystem Flows reacts to OCR completion
  -> Knowledge/Open Pages item is created
  -> Kanban task or approval is created
  -> Notification is sent
  -> Audit trail records every action
```

This single journey demonstrates event-driven architecture, worker queues, document processing, AI integration, auditability, permissions, and a coherent UI ecosystem.

---

# Product layer model

Open Ecosystem OS is organized into three product layers.

## 1. Public product / portfolio layer

Explains the ecosystem, documents the architecture, demonstrates the design system, and provides case studies.

Includes:

- Landing page
- About page
- Apps overview
- Architecture page
- Design system page
- Self-hosting installation
- Roadmap
- Changelog
- Case studies
- Live demo entry point

## 2. Authenticated workspace layer

The daily-use product surface.

Core apps:

- Dashboard
- Drive
- PDF Editor
- Open Pages
- Open Ecosystem Flows
- Media/OCR
- Global Search
- Notifications
- Activity/Audit Logs

## 3. Platform, admin, and developer layer

Operational, security, extensibility, and open-source surfaces.

Includes:

- Admin dashboard
- System status
- Security settings
- Backup and restore
- Integrations
- Advanced admin analytics
- Event catalog
- API explorer
- ADRs
- Marketplace
- Plugin developer portal
- Plugin review/admin flow
- Community forum

## Implementation warning

The public design catalog represents the long-term vision. MVP implementation must still prioritize the flagship invoice automation journey and avoid building every designed screen upfront.

## Open Ledger product area

Open Ledger is the planned finance-tracking app for the ecosystem. It is a self-hosted personal/household finance tracker focused on manual transactions, receipt OCR, AI-assisted categorization, budget rules, product-price intelligence, and privacy.

Open Ledger belongs to the authenticated workspace layer as a post-MVP app. It should not require bank connections. It should reuse existing ecosystem capabilities instead of building isolated infrastructure.

Core Open Ledger capabilities:

- add expenses with amount, item, merchant/shop/restaurant, category, person, payment method, date, notes, tags, and receipt attachment
- add income with amount, item/source, person, category, date, and recurrence metadata
- scan/upload receipts and convert them into reviewable transaction drafts using Media/OCR and AI parsing
- classify spending into categories such as groceries, eating out, transport, subscriptions, utilities, shopping, health, travel, savings, and other
- define budgets, limits, and habits such as “do not eat out more than once per week” or “groceries under 400 EUR/month”
- compare recurring products across stores using line-item extraction and unit-price normalization
- generate reports for income, expenses, net balance, savings rate, categories, merchants, people, recurring/variable costs, and product prices
- preserve privacy by default: no bank connection, no external AI/OCR unless configured, export/delete data available

Open Ledger reinforces the ecosystem story because it uses Drive, Media/OCR, AI Assistant, Open Ecosystem Flows, Notifications, Global Search, Activity/Audit Logs, and Open Pages together.

See `docs/product/OPEN_LEDGER.md` for the detailed product logic.
