# Technology Stack

## Recommended baseline

### Monorepo

- One repository for frontend, backend, workers, infra, docs, and prompts.
- Separate deployables inside `apps/`.
- Shared contracts inside `packages/shared`.

## Frontend

### Core

- Next.js App Router
- React
- TypeScript
- Tailwind CSS
- shadcn/ui and Radix UI primitives
- Lucide icons
- TanStack Query
- Zustand only for local UI/editor state where needed

### Feature-specific frontend libraries

| Feature | Suggested tech | Notes |
|---|---|---|
| App shell | Next.js layouts + shared components | Public/private layout separation |
| Design system | Tailwind CSS variables + shadcn/ui | Avoid hardcoded colors |
| Open Pages editor | TipTap/ProseMirror or Lexical | Do not build editor engine from scratch |
| Kanban | dnd-kit | Good React drag/drop primitives |
| Workflow builder | React Flow later | Start with vertical builder before complex canvas |
| Charts | Recharts or Tremor-style components | Use tokens; avoid hardcoded chart colors |
| Forms | React Hook Form + Zod | Good validation and schemas |
| API state | TanStack Query | Caching, mutations, server state |
| Testing | Vitest, Testing Library, Playwright later | Vitest/Testing Library are current; Playwright is deferred until the E2E harness exists |
| Storybook | Storybook later | Deferred until shared component/design-system validation is configured |

## Backend

### Recommended baseline

- Java 25
- Spring Boot
- Spring Web
- Spring Security
- Spring Data JPA
- Flyway or Liquibase
- PostgreSQL
- Redis
- RabbitMQ
- MinIO/S3 SDK
- Meilisearch client
- Micrometer/OpenTelemetry

### Why Spring Boot

- Strong backend hiring signal
- Fits user's current experience
- Excellent modular monolith support
- Solid integration with PostgreSQL, security, metrics, and queues

### Alternative

NestJS is a valid option for faster full-stack TypeScript delivery, but Spring Boot is preferred for backend credibility and alignment with current strengths.

## Data and storage

| Need | Technology | Why |
|---|---|---|
| Transactional state | PostgreSQL | Users, workspaces, permissions, files, workflow executions, audit logs |
| Flexible documents | PostgreSQL JSONB first | Pages, workflow definitions, forms, themes, plugin manifests |
| Object/files | MinIO/S3-compatible storage | Uploaded files, PDFs, media, previews, exports, backups |
| Cache/locks/rate limits | Redis | Idempotency keys, short-lived state, queues if needed |
| Full-text/semantic search | Meilisearch first | Simple self-hosted search and AI/RAG-friendly indexing |
| Message broker | RabbitMQ first | Reliable queues, DLQ, retries, Spring support |
| Event streaming alternative | NATS JetStream later | Simpler ops and replayable streams if needed |

## Feature technology matrix

| Feature | Frontend | Backend | Storage | Async/events |
|---|---|---|---|---|
| Dashboard | Next.js + cards/charts | API aggregations | PostgreSQL + metrics | none initially |
| Drive | file browser components | Drive module | PostgreSQL + MinIO | FileUploaded |
| PDF Editor | PDF viewer/editor libs | PDF processing module/worker | MinIO + metadata | PdfProcessed, PdfRedacted |
| AI redaction | review UI | AI/mock extractor + redaction worker | MinIO + audit | RedactionRequested/Completed |
| Delete metadata | PDF processing action | worker | MinIO new version | PdfMetadataRemoved |
| Watermark | PDF editor controls | PDF worker | MinIO new version | WatermarkApplied |
| Media/OCR | queue/status UI | OCR module + worker | MinIO + PostgreSQL | OcrRequested/Completed/Failed |
| Open Pages | TipTap/Lexical | Pages module | PostgreSQL + JSONB | PageCreated/Updated/Indexed |
| Open Ecosystem Flows | builder/run UI | Flows module + runner | PostgreSQL + JSONB | WorkflowTriggered/Completed/Failed |
| Search | global results UI | Search module/indexer | Meilisearch | PageIndexed/FileIndexed/OcrIndexed |
| Notifications | inbox UI | Notifications module | PostgreSQL | NotificationCreated/Sent |
| Audit logs | table/timeline UI | Audit module | PostgreSQL | from domain/app events |
| Security | settings UI | Identity/Security module | PostgreSQL + Redis | SecurityEventRaised |
| Backups | status/action UI | Backup worker | MinIO/external target | BackupStarted/Completed/Failed |
| Observability | links/summaries | metrics endpoints | Prometheus/Loki/Tempo | external stack |
| Plugins later | portal UI | plugin registry/sandbox | PostgreSQL JSONB | PluginSubmitted/Approved |

## Infrastructure

### Local

- Docker Compose for local multi-container development.

### Server

- Kubernetes for orchestration.
- Start with raw manifests or Kustomize overlays.
- Consider Helm after the manifests stabilize.

### Observability

- Prometheus + Grafana + Loki first.
- Add OpenTelemetry Collector/Grafana Alloy and Tempo when traces matter.

## Technology decisions to revisit later

- RabbitMQ vs NATS JetStream
- PostgreSQL JSONB vs MongoDB for page/workflow documents
- Meilisearch vs OpenSearch
- Raw Kubernetes manifests vs Helm chart
- TipTap vs Lexical
- Spring Boot workers vs separate worker runtime

## Open Ledger technology choices

| Area | Suggested technology | Notes |
|---|---|---|
| Dashboard and reports | Recharts or token-based chart components | Use shared design tokens; avoid hardcoded chart colors. |
| Transaction forms | React Hook Form + Zod | Strong validation for amount, dates, category, person, and payment method. |
| Tables/lists | Shared DataTable + MobileCardList | Desktop table, mobile card list. |
| Receipt upload | Existing Drive/Media upload components | Reuse UploadDropzone and FilePreview. |
| OCR | Existing Media/OCR pipeline | Receipt OCR should be an app-specific consumer of the generic OCR pipeline. |
| AI categorization | AI Assistant/tool service | AI returns suggestions, never final unreviewed writes for low-confidence extraction. |
| Product matching | Backend service + JSONB alias metadata | Start rule-based/manual, then add AI-assisted matching. |
| Search | Meilisearch | Index transactions, merchants, receipt text, products, and reports. |
| Events | RabbitMQ + outbox | Finance events should drive audit, notifications, search, and automations. |
| Storage | PostgreSQL + JSONB + MinIO | Relational transaction data, JSONB parsed receipt payloads, MinIO receipt files. |

Backend modules likely needed:

- `finance` for transactions, budgets, products, reports, and settings
- `receipt-processing` integration with Media/OCR and AI parsing
- `finance-rules` for budgets, habits, and alerts
- `product-price-intelligence` for line items, aliases, unit prices, and store ranking
