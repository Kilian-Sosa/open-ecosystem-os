# Open Ecosystem OS

Open Ecosystem OS is a self-hosted, open-source productivity ecosystem designed as both a useful personal platform and a portfolio-grade engineering showcase.

The goal is not to clone existing tools. The goal is to build a coherent ecosystem where files, pages, OCR, AI, automations, search, notifications, audit logs, and admin operations work together through a modular, event-driven architecture.

## Core idea

A user should be able to run their own private workspace and automate practical workflows such as:

```txt
Upload invoice PDF
  -> OCR extracts text
  -> AI extracts structured data
  -> Workflow automation creates a task/page/knowledge item
  -> Notification is sent
  -> Audit log records the full chain
```

## Repository strategy

This project starts as a monorepo:

```txt
open-ecosystem-os/
  apps/
    web/      # Next.js frontend
    api/      # Spring Boot modular API
    worker/   # Async workers for OCR, flows, indexing, notifications
  packages/
    ui/       # Shared design system components
    shared/   # Shared event names, schemas, constants, API contracts
    config/   # Shared tooling configuration
  docs/       # Product, architecture, ADRs, development docs, prompts
  infra/      # Docker, Kubernetes, Grafana, observability
  scripts/    # Developer scripts and seed/reset commands
```

Start together. Deploy separately. Split into independent repositories only when lifecycle, ownership, security, or reuse demands it.

## Recommended first vertical slice

The first real implementation milestone should be:

```txt
Drive upload -> Media/OCR worker -> domain event -> Open Ecosystem Flows -> Notification -> Audit log
```

This validates the architecture before the project expands.

## Documents to read first

1. `docs/product/PRODUCT_BLUEPRINT.md`
2. `docs/product/MVP_SCOPE.md`
3. `docs/product/USER_JOURNEYS.md`
4. `docs/architecture/ARCHITECTURE.md`
5. `docs/architecture/TECH_STACK.md`
6. `docs/architecture/EVENTS.md`
7. `docs/development/AGENTS.md`
8. `docs/development/DESIGN.md`

## Local development target

Initial local stack:

- web app
- API
- worker
- PostgreSQL
- Redis
- RabbitMQ
- MinIO
- Meilisearch
- optional Grafana/Prometheus/Loki/OpenTelemetry profile

See `infra/docker/docker-compose.yml` and `infra/docker/docker-compose.observability.yml`.

### Docker Compose startup

Create a local environment file and start the base stack:

```bash
cp .env.example .env
make docker-up
make ps
```

Useful follow-up commands:

```bash
make logs
make down
```

Equivalent direct Docker Compose commands from the repository root:

```bash
docker compose --env-file .env -f infra/docker/docker-compose.yml up -d --build
docker compose --env-file .env -f infra/docker/docker-compose.yml ps
docker compose --env-file .env -f infra/docker/docker-compose.yml down
```

Start the optional observability stack only with the override file:

```bash
make obs-up
```

or:

```bash
docker compose --env-file .env -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.observability.yml up -d --build
```

Default local endpoints:

- Web: `http://localhost:3000`
- API health: `http://localhost:8080/health`
- RabbitMQ management: `http://localhost:15672`
- MinIO API: `http://localhost:9000`
- MinIO console: `http://localhost:9001`
- Grafana with observability override: `http://localhost:3001`

PostgreSQL, Redis, RabbitMQ AMQP, and Meilisearch are kept on the internal Compose network by default. Expose additional data-service ports only when a local debugging workflow needs them.

If Docker volumes already exist, changes to first-boot database or broker initialization options may require recreating local volumes:

```bash
docker compose --env-file .env -f infra/docker/docker-compose.yml down -v
```

This deletes local Compose data. The MinIO single-node warning about host failure is expected in local development and should not appear in production storage topology.

## Development, CI/CD, and AI workflow

The development process is documented in:

- `docs/development/DEVELOPMENT_WORKFLOW.md`
- `docs/development/QUALITY_GATES.md`
- `docs/development/CI_CD.md`
- `docs/development/FORMATTING_LINTING.md`
- `docs/development/SECURITY_AND_VULNERABILITY_CHECKS.md`
- `docs/development/TEST_COMMANDS.md`

GitHub Actions templates are intentionally stored in `docs/templates/github-workflows/` until the app skeletons exist. Copy them into `.github/workflows/` only after the commands they call pass locally.

---

## Design reference layer

This repository now includes implementation-facing design references:

- `docs/design/SCREEN_CATALOG.md` — screen inventory, priorities, and mockup references.
- `docs/design/COMPONENT_INVENTORY.md` — reusable component map.
- `docs/design/MOCKUP_REFERENCE_GUIDE.md` — rules for using mockups.
- `docs/development/ROUTES.md` — proposed Next.js route map.
- `docs/design/mockups/` — organized desktop/mobile visual references.

Must treat the mockups as visual references, not as pixel-perfect implementation contracts. The source of truth remains `DESIGN.md`, `AGENTS.md`, and the written screen specs.

## Planned app: Open Ledger

Open Ledger is a planned post-MVP finance tracker for manual expenses/income, receipt OCR, AI-assisted categorization, budgets/rules, product-price intelligence, reports, and privacy-first household finance tracking.

It intentionally avoids bank connections in the initial roadmap. See `docs/product/OPEN_LEDGER.md` for the detailed logic and `docs/design/mockups/apps/open-ledger/` for the mockups.
