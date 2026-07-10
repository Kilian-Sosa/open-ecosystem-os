# Screen Specs

Every major screen must support desktop and mobile behavior.

## Public portfolio

### Home / Landing

Purpose: explain the ecosystem and direct users to demo, apps, docs, architecture, and GitHub.

Sections:

- hero
- product preview
- featured apps
- self-hosting promise
- architecture teaser
- case studies
- open-source CTA
- footer

### About

Purpose: explain why the ecosystem exists.

Sections:

- hero
- mission
- ecosystem overview
- principles
- architecture summary
- roadmap preview
- open-source CTA

### Architecture

Purpose: prove technical depth.

Sections:

- system overview
- bounded contexts
- event-driven layer
- storage layer
- Kubernetes/deployment
- external observability stack
- tradeoffs

### Design System

Purpose: document UI rules and reusable components.

Sections:

- colors
- typography
- spacing
- components
- states
- responsive rules
- accessibility rules
- theme tokens

### Case studies

Required first case studies:

- Open Ecosystem Flows
- Open Pages
- Media/OCR Pipeline

Each case study includes:

- problem
- constraints
- architecture
- data model
- events
- UI screenshots
- tradeoffs
- next improvements

## Workspace

### Dashboard

Desktop:

- sidebar
- top command/search bar
- KPI cards
- quick actions
- recent files
- active automations
- recent activity
- system/storage summary

Mobile:

- top header
- search
- app shortcuts
- stacked KPI cards
- activity feed
- bottom nav

### Drive

Desktop:

- folder tree
- file table/grid
- upload action
- filters/search
- selected file inspector panel

Mobile:

- header
- search
- folder chips
- file cards
- selected file bottom sheet

### PDF Editor

Required features:

- add watermark
- AI-assisted redaction
- delete metadata
- real permanent redaction
- OCR scanned PDFs
- merge/split/reorder/delete pages
- compress/export
- annotations
- fill/sign basic
- version history

Desktop:

- page thumbnails
- canvas
- toolbar
- right inspector

Mobile:

- preview-first
- bottom tool/action bar
- export/save actions

### Media/OCR

Desktop:

- inbox/upload queue
- processing pipeline
- asset grid
- selected asset details
- read-only lifecycle trace after selected job metadata
- extracted text panel

Mobile:

- upload-first flow
- job cards
- processing status
- stacked lifecycle cards in the selected-job bottom sheet
- extracted text bottom sheet

Lifecycle behavior:

- show upload, OCR queue/attempt/outcome, correlated workflow/extraction, and
  downstream notification/search facts when durable evidence exists
- show explicit awaiting state for active jobs, scheduled retries, running
  workflows, and pending outbox publication
- distinguish unavailable evidence from pending work and never infer broker
  delivery, retry, dead-letter, consumption, or completion
- keep extracted text separate from diagnostics and link to the
  correlation-filtered audit route when a correlation ID exists

### Open Pages

Desktop:

- left page tree
- main block editor
- collaborator/comments indicators
- right metadata/backlinks/activity panel

Mobile:

- reading/editing view
- block add button
- comments bottom sheet
- page actions bottom sheet

### Open Ecosystem Flows

Desktop:

- workflow list/templates
- builder canvas or vertical builder
- node catalog
- right configuration panel
- execution history

Mobile:

- workflow cards
- run status
- step list
- configuration bottom sheet

### Global Search

Desktop:

- query input
- filters by content type
- result list
- right preview/source panel
- AI summary optional

Mobile:

- search-first layout
- filter chips
- stacked results
- preview bottom sheet

### Notifications

Desktop:

- notification inbox
- filters
- selected notification detail
- preferences link

Mobile:

- stacked notification cards
- quick actions

### Activity/Audit Logs

Desktop:

- filterable log table/timeline
- actor/resource/action filters
- selected event detail

Mobile:

- timeline cards
- filter drawer

## Platform/admin

Screens:

- Admin Dashboard
- System Status
- Security Settings
- Backup and Restore
- Integrations
- App Management
- Theme Builder
- Event Catalog
- API Explorer
- ADR/Technical Decisions
- Advanced Admin Analytics
- Plugin Developer Portal later
- Plugin Review/Admin Flow later
- Marketplace later

---

# Design reference catalog

The full screen list, priorities, and mockup references are maintained in:

- `docs/design/SCREEN_CATALOG.md`
- `docs/design/COMPONENT_INVENTORY.md`
- `docs/design/MOCKUP_REFERENCE_GUIDE.md`
- `docs/development/ROUTES.md`

The design repository contains long-term screens that are not part of the MVP. Must respect the priority tags:

- `P0`: first vertical slice / foundation
- `P1`: MVP
- `P2`: post-MVP
- `P3`: long-term/showcase

Do not implement P2/P3 screens unless the prompt explicitly requests them.

## P0 implementation focus

- App Shell
- Dashboard
- Drive / File Manager
- Media/OCR
- Open Ecosystem Flows basic
- Notifications
- Activity/Audit Logs
- Error/Empty/Loading states
- First-time setup/onboarding

## P1 implementation focus

- Open Pages basic
- PDF Editor basic
- Global Search
- Sharing & Permissions
- Security Settings
- System Status
- Backup & Restore
- Self-hosting Installation
- Architecture and Design System pages

## Open Ledger screens

Open Ledger is a post-MVP workspace app. Every screen must support desktop and mobile layouts.

### Open Ledger Dashboard

Purpose: summarize monthly finance health and direct users to transactions, receipts, budgets, reports, and product-price insights.

Desktop:

- app sidebar
- summary metric cards: income, expenses, net balance, savings rate
- spending by category chart
- recent transactions table
- budget rules/habits panel
- receipt review queue
- products and prices panel
- insights and alerts

Mobile:

- top header
- metric cards in two-column grid
- quick actions: add expense, add income, scan receipt
- spending chart
- recent transactions list
- active rule alert card
- bottom navigation with central scan action

### Transactions

Purpose: manage expenses, income, and receipt-based entries.

Desktop:

- add expense, add income, scan receipt actions
- filters by period, category, person, shop/source, status
- summary metric cards
- transaction table
- right transaction detail panel
- bulk actions and export

Mobile:

- filter chips and search
- summary cards
- transaction cards
- floating add/scan action

### Receipts

Purpose: scan/upload, process, review, and confirm receipts.

Desktop:

- scan/upload/review queue actions
- receipt filters
- receipt table
- right review panel with receipt preview, parsed fields, line items, AI suggestions, confidence, confirm/edit/re-run actions

Mobile:

- receipt cards
- selected receipt bottom sheet with line items and confirm action

### Budgets & Rules

Purpose: define category budgets, habit rules, and savings goals.

Desktop:

- create budget/rule actions
- filters by period/person/category/status
- budget summary cards
- budgets by category table
- rules and habits list
- alerts/recommendations
- spending trend vs budget chart

Mobile:

- tabs for all/budgets/rules/alerts
- budget cards
- rules cards
- alerts cards

### Products & Prices

Purpose: compare recurring product prices across stores and detect savings opportunities.

Desktop:

- compare products/add alias/review alerts actions
- filters by period/store/category/product
- tracked products and price opportunity metrics
- product comparison table
- selected product price history and store ranking panel
- category savings overview
- store basket comparison chart

Mobile:

- top product cards
- store ranking cards
- alerts/recommendations

### Reports

Purpose: explain income, expenses, savings, categories, stores, people, and product-price trends.

Desktop:

- report filters
- KPI cards
- income vs expenses trend
- spending by category
- spending by merchant
- monthly comparison
- spending by person
- recurring vs variable expenses
- AI summary
- report presets/export

Mobile:

- report tabs
- KPI cards
- charts as stacked cards
- AI insights card

### Settings

Purpose: configure household, categories, payment methods, recurring items, OCR/AI parsing, rules, notifications, privacy, and localization.

Desktop:

- settings navigation
- cards for household, categories, payment methods, recurring income/expenses, OCR/AI parsing, budget defaults, notifications, privacy/data, localization
- save/reset actions
- privacy-first explainer panel

Mobile:

- accordion settings groups
- sticky save action
