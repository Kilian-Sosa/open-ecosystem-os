# Product Requirements

## Core requirements

Open Ecosystem OS must provide:

- public portfolio and docs
- authenticated workspace
- self-hosted deployment path
- responsive desktop/mobile UI
- design-system-driven frontend
- modular backend boundaries
- event-driven workflow support
- auditability
- external observability integration
- Dockerized deployables
- Kubernetes path

## Public portfolio requirements

Must include:

- Home
- About
- Apps overview
- Architecture
- Design System
- Case studies
- Self-hosting installation
- Docs
- Roadmap
- Changelog

## Workspace requirements

Must include:

- Dashboard
- Drive
- Media/OCR
- PDF Editor
- Open Pages
- Open Ecosystem Flows
- Global Search
- Notifications
- Activity/Audit Logs
- Settings/Security

## PDF Editor requirements

The PDF editor should support:

- add watermark
- AI-assisted redaction
- delete metadata
- real permanent redaction, not just drawing overlays
- OCR for scanned PDFs
- merge PDFs
- split PDFs
- reorder pages
- delete pages
- rotate pages
- compress/export
- annotations
- fill/sign basic documents
- version history
- save/export to Drive
- audit log for transformations

AI-assisted redaction must:

- detect sensitive information
- show suggestions to the user
- require review/confirmation
- remove content from the PDF content stream/searchable text layer where possible
- remove/update indexed OCR/search content

Sensitive data categories:

- names
- emails
- phone numbers
- addresses
- NIF/NIE/CIF-like identifiers
- IBAN/bank details
- signatures
- faces in scanned images, later

## Developer/Test Data Tools

Later utility tools should include:

- Test IBAN generator
- Test NIF generator
- Fake invoice generator
- Fake document generator

All generated values must be clearly labeled as fake/test data.

## Open Pages requirements

MVP:

- nested pages
- block editor
- headings, paragraphs, lists, checklists, quotes, code blocks
- file embeds
- basic comments
- search indexing
- AI summary

Later:

- databases
- table/kanban/gallery views
- backlinks
- real-time collaboration
- version history
- templates

## Open Ecosystem Flows requirements

MVP:

- workflow definitions as JSON
- workflow versions
- manual trigger
- FileUploaded/OcrCompleted triggers
- basic actions
- execution history
- retries
- failure state
- DLQ concept

Later:

- visual node canvas
- plugin nodes
- credential vault
- webhook triggers
- HTTP request action
- template gallery

## Security requirements

- RBAC/resource permissions
- password/auth provider support later
- session management
- API keys later
- audit log for sensitive actions
- AI tool actions require permission checks
- secrets never logged
- generated demo data never uses real personal data

## Observability requirements

App exposes:

- health endpoint
- readiness endpoint
- metrics endpoint
- structured logs with correlation IDs
- worker/job metrics

External stack optional:

- Prometheus
- Grafana
- Loki
- OpenTelemetry Collector/Grafana Alloy
- Tempo later
- Alertmanager later

## Open Ledger requirements

Open Ledger must support a privacy-first finance tracker without bank connections.

### Transactions

- add expense
- add income
- edit/delete/duplicate/split transactions
- fields: amount, item/title, merchant/source, person, category, payment method, date/time, status, tags, notes, linked receipt
- filter by type, date range, category, person, shop/source, payment method, status, tags
- export transaction data to CSV/PDF

### Receipts

- scan/upload receipt image or PDF
- process receipt through Media/OCR
- suggest merchant, date, total, category, person, and line items
- show OCR confidence and low-confidence warnings
- require review before saving extracted data
- allow re-running OCR and editing fields
- create transaction after confirmation

### Budgets and rules

- create category budgets
- create saving goals
- create habit/frequency rules
- create merchant/subscription limits
- evaluate rules on transaction creation/update
- generate alerts for near-limit, exceeded, and at-risk rules

### Products and prices

- store receipt line items
- normalize product aliases
- calculate unit prices
- compare stores for recurring products
- track product price history
- generate price alerts and recommendations

### Reports

- income, expenses, net balance, savings rate
- spending by category
- spending by merchant/shop
- spending by person
- recurring vs variable expenses
- monthly comparison
- product price intelligence
- AI summary of spending changes

### Settings

- household members
- categories and tags
- payment methods
- recurring income/expenses
- OCR/AI parsing preferences
- default budgets/rules
- notification preferences
- export/delete finance data
- currency, locale, first day of week, date format

### Privacy and safety

- no bank connection in MVP
- no external OCR/AI processing unless explicitly configured
- finance data export and deletion must be supported
- shared-household actions must be permissioned and audited
- AI suggestions must be reviewable and correctable
