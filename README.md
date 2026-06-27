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
  docs/       # Product, architecture, ADRs, development docs
  .agents/    # Repo-scoped Codex skills for reusable agent workflows
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
7. `AGENTS.md`
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

`make down` uses the base and observability Compose files so it can cleanly remove the shared networks after `make obs-up`.

Equivalent direct Docker Compose commands from the repository root:

```bash
docker compose --env-file .env -f infra/docker/docker-compose.yml up -d --build
docker compose --env-file .env -f infra/docker/docker-compose.yml ps
docker compose --env-file .env -f infra/docker/docker-compose.yml down
```

Start the optional observability stack only with the override file:

```bash
make obs-up
make obs-ps
```

or:

```bash
docker compose --env-file .env -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.observability.yml --profile observability up -d --build
```

Default local endpoints:

- Web: `http://localhost:3000`
- API health: `http://localhost:8080/health`
- API metrics: `http://localhost:8080/metrics`
- RabbitMQ management: `http://localhost:15672`
- MinIO API: `http://localhost:9000`
- MinIO console: `http://localhost:9001`
- Grafana with observability override: `http://localhost:3001`
- Prometheus with observability override: `http://localhost:9090`
- Loki with observability override: `http://localhost:3100`

These ports are published on `LOCAL_BIND_ADDRESS` from `.env`, which defaults to `127.0.0.1`.

PostgreSQL, Redis, RabbitMQ AMQP, and Meilisearch are kept on the internal Compose network by default. Expose additional data-service ports only when a local debugging workflow needs them.

The optional Grafana profile provisions two MVP dashboards:

- `Open Ecosystem / Platform Overview`
- `Open Ecosystem / Automation Pipeline`

The observability profile is intentionally not required for core app startup. Loki is provisioned as a datasource, but Docker log shipping is deferred until Alloy/Promtail or OTLP log export is added. RabbitMQ queue depth, DLQ, MinIO storage, and host/container metrics need exporters before those panels and alerts can show real values.

### Flagship invoice automation demo

The seeded flagship demo is available at:

```txt
http://localhost:3000/app/demo/invoice-automation
```

It creates fake/test data only: a placeholder invoice document in Drive, mock OCR output, deterministic fake invoice extraction fields, a workflow notification, audit records, and a Meilisearch indexing request/result. Any NIF or IBAN examples in the seeded data are labelled as test data.

With the Compose stack running, start or reset a demo run from the UI or with:

```bash
make seed
make reset
```

On Windows, the same Make targets call `scripts/seed-demo-data.ps1` and `scripts/reset-demo-data.ps1`. You can override `API_BASE_URL`, `WORKSPACE_ID`, and `ACTOR_ID` for either shell.

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

## License

Unless otherwise noted:

- Software source code is licensed under the GNU Affero General Public License v3.0
  only (`AGPL-3.0-only`). See [LICENSE](LICENSE).
- Narrative documentation is licensed under Creative Commons Attribution 4.0
  International (`CC-BY-4.0`). See [docs/LICENSE.md](docs/LICENSE.md).
- Project notices and attributions are tracked in [NOTICE.md](NOTICE.md).
- Project names and logos are governed by [TRADEMARKS.md](TRADEMARKS.md).
- Authors and contributors are listed in [AUTHORS.md](AUTHORS.md).

Copyright (C) 2026 Kilian Sosa Guillén.
