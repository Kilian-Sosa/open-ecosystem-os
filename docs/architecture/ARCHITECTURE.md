# Architecture

## Architectural strategy

Start as a modular monolith with asynchronous workers and clear module boundaries.

Do not start with many microservices. The system should be designed so modules can be extracted later when lifecycle, load, or security justifies it.

## High-level runtime architecture

```txt
Browser / Mobile Web
  -> Next.js Web App
  -> API Gateway/BFF boundary
  -> Spring Boot API modular monolith
      -> Identity module
      -> Workspace module
      -> Drive module
      -> Media/OCR module
      -> Open Pages module
      -> Open Ecosystem Flows module
      -> Notifications module
      -> Search module
      -> Audit module
      -> Admin module
  -> Worker service(s)
      -> OCR worker
      -> Workflow runner
      -> Search indexer
      -> Notification worker
  -> Infrastructure
      -> PostgreSQL
      -> Redis
      -> RabbitMQ
      -> MinIO
      -> Meilisearch
      -> Optional Grafana observability stack
```

## Core layers

### 1. Presentation layer

- Public portfolio pages
- Authenticated workspace apps
- Admin/platform UI
- Developer/docs UI

Primary technology: Next.js + React + TypeScript.

### 2. API/application layer

- REST/JSON API for MVP
- Later GraphQL is possible but not necessary
- Application services coordinate use cases
- Domain events emitted after meaningful business changes

Primary technology: Spring Boot.

### 3. Domain/module layer

Bounded contexts:

- Identity & Access
- Workspaces
- Drive
- Media/OCR
- Open Pages
- Open Ecosystem Flows
- Notifications
- Search
- Audit
- Admin/System
- Integrations, later
- Plugins, later

### 4. Asynchronous execution layer

Used for:

- OCR processing
- workflow execution
- notification delivery
- search indexing
- backup jobs
- AI extraction/summarization jobs

Primary technology: RabbitMQ for MVP, with NATS JetStream as a possible later alternative.

### 5. Storage layer

- PostgreSQL for transactional data
- PostgreSQL JSONB for flexible workflow/page/form/theme/plugin structures
- MinIO/S3-compatible storage for files and generated assets
- Redis for cache, locks, rate limits, idempotency keys, temporary processing state
- Meilisearch for search/indexing

### 6. Observability layer

External stack, optional but recommended:

- Prometheus/Grafana for metrics and dashboards
- Loki for logs
- Tempo for traces, later
- OpenTelemetry Collector or Grafana Alloy for telemetry collection
- Alertmanager for alert routing

## Recommended deployment topology

### Local development

Docker Compose:

- web
- api
- worker
- postgres
- redis
- rabbitmq
- minio
- meilisearch
- optional observability profile

### Server/Kubernetes

Namespace separation:

```txt
open-ecosystem-os
open-ecosystem-data
open-ecosystem-observability
```

MVP workloads:

- web deployment
- api deployment
- worker deployment
- postgres statefulset or external managed DB
- redis stateful/deployment or external
- rabbitmq statefulset or operator later
- minio statefulset or external S3
- meilisearch deployment/statefulset
- ingress/gateway

## Extraction candidates

Do not extract immediately. Future service candidates:

1. Media/OCR service
2. Workflow runner service
3. Search indexer service
4. Notification service
5. Plugin sandbox/runtime
6. AI gateway/service

Extraction criteria:

- different scaling profile
- different failure isolation needs
- security boundary
- independent lifecycle
- independent team/ownership
- clear API/event contract

## Main architectural risks

### Scope explosion

Risk: too many apps before one vertical slice works.

Control: prioritize invoice automation journey.

### Distributed complexity too early

Risk: microservices before domain boundaries stabilize.

Control: modular monolith first.

### Event inconsistency

Risk: DB commits succeed but events fail to publish.

Control: introduce outbox pattern when workflows depend on reliable publication.

### Permissions inconsistency

Risk: each app invents permissions differently.

Control: define shared resource/action permission model early.

### UI drift

Risk: each app looks different.

Control: enforce DESIGN.md and shared UI package.
