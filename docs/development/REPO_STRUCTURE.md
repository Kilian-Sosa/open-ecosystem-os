# Repository Structure

## Recommended monorepo

```txt
open-ecosystem-os/
  apps/
    web/
    api/
    worker/
  packages/
    ui/
    shared/
    config/
  docs/
    product/
    architecture/
    adr/
    development/
    prompts/
  infra/
    docker/
    k8s/
    grafana/
  scripts/
```

## apps/web

Next.js frontend.

Responsibilities:

- public portfolio pages
- authenticated workspace
- app shell
- admin/platform UI
- design-system page
- case studies

## apps/api

Spring Boot modular API.

Responsibilities:

- authentication/authorization
- workspace/domain APIs
- metadata persistence
- event creation
- audit records
- search API
- admin API

## apps/worker

Async workers.

Responsibilities:

- OCR processing
- workflow execution
- notification dispatch
- search indexing
- backup jobs
- AI/mock extraction jobs

Initially this may be a separate Spring Boot app or a module inside the API. It is separated in the repo to keep future deployment separation clear.

## packages/ui

Shared frontend design system.

Components:

- shell
- navigation
- cards
- tables
- status chips
- modals
- bottom sheets
- command palette
- empty/error/loading states

## packages/shared

Shared contracts.

Contains:

- event names
- API schemas
- permission constants
- status enums
- workflow node schemas
- theme token schemas

If backend is Java and frontend TypeScript, this package may initially hold TypeScript contracts plus generated OpenAPI contracts later.

## infra/docker

- base Docker Compose
- observability override
- local service configs

## infra/k8s

- base manifests
- dev overlay
- prod overlay

## infra/grafana

- dashboards
- alert rules
- provisioning files later

## docs

Documentation is part of the portfolio.

It should explain product reasoning, technical decisions, user journeys, architecture, deployment, testing, and tradeoffs.
