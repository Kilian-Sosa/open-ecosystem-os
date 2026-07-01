# Agent Instructions - Open Ecosystem OS

These instructions are for Codex or any coding agent working in this repository.

Always read before coding:

- `docs/development/DESIGN.md`
- `docs/product/MVP_SCOPE.md`
- `docs/product/USER_JOURNEYS.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/EVENTS.md`
- `docs/architecture/PERMISSIONS.md`

## Primary goal

Build a consistent, responsive, accessible, self-hosted productivity ecosystem.

Prioritize the first vertical slice:

```txt
Drive upload -> OCR worker -> event -> workflow -> notification -> audit log
```

## Current stack

Frontend:

- Next.js App Router
- React
- TypeScript
- Tailwind CSS
- shadcn/ui / Radix primitives as the design-system target; verify packages are installed before use
- Lucide icons
- TanStack Query
- Vitest and Testing Library

Backend:

- Spring Boot modular monolith API
- Spring Boot worker
- Java 25
- PostgreSQL
- Redis
- RabbitMQ
- MinIO
- Meilisearch

Infrastructure:

- Docker Compose for local development
- Kubernetes manifests/Helm later
- optional external observability stack
- active GitHub Actions for web, API, worker, docs, Kubernetes, and security checks

## Repository status

This is no longer only a scaffold. `apps/web`, `apps/api`, and `apps/worker`
exist and have local checks. Some docs were written earlier as planning docs,
so verify implementation-sensitive claims against code, package scripts,
Maven POMs, Compose files, Kubernetes manifests, and active workflows before
acting on them.

Use the tracked repo skills in `.agents/skills/` for planning and review.
They are workflow guides, not independent edit permission. Use them to produce
findings, risks, test selections, and implementation guidance.

## Code rules

- Use TypeScript types in frontend.
- Use Java types/records/DTOs in backend.
- Keep components small and composable.
- Prefer clear module boundaries.
- Avoid unnecessary abstractions.
- Keep mock data in dedicated files.
- Do not introduce backend calls in frontend unless the API contract exists.
- Do not use `any` unless unavoidable and justified.
- Do not add comments unless the logic is non-obvious.

## File and tool hygiene

- Do not inspect real secret files such as `.env`; use `.env.example` for
  variable names and fake values.
- Do not manually edit generated or local output such as `node_modules/`,
  `.next/`, `target/`, `coverage/`, `playwright-report/`, `test-results/`,
  `*.tsbuildinfo`, local Docker volumes, local logs, or `.codegraph/`.
- Keep fake/test data clearly labelled. Never commit real personal documents,
  real OCR text, real AI prompts/responses, private keys, or production tokens.
- Prefer `rg`/`rg --files` for repository search. Exclude generated output
  when searching broadly.
- If CodeGraph is initialized, consult it before manual symbol tracing for
  architecture, flow, impact, or "where is this" questions. If it is not
  initialized, propose `codegraph init -i` as a local setup step and do not
  commit the generated `.codegraph/` directory.

## Specialist skill use

Use the repo skills under `.agents/skills/` when a task needs focused review:

- `$issue-planner` before non-trivial implementation.
- `$architecture-reviewer` for modules, routes, events, workflows, storage,
  infrastructure, and deployment boundaries.
- `$security-privacy-reviewer` for files, OCR, AI, secrets, auth/RBAC,
  audit, plugins, external calls, and destructive actions.
- `$accessibility-reviewer` for UI/UX, frontend, and PR reviews that need
  keyboard, semantics, focus, ARIA, contrast, forms/errors, screen-reader
  states, and WCAG-oriented accessibility risk checks.
- `$backend-spring-reviewer` for API, worker, Java, Spring, outbox,
  repository, and event-consumer changes.
- `$frontend-next-reviewer` for Next.js, React, design-system, responsive,
  TanStack Query, and state-handling changes.
- `$database-migration-reviewer` for Flyway, schema, seed data, constraints,
  indexes, and idempotency changes.

Do not create or invoke a separate test-runner agent. Select tests from
`docs/development/TEST_COMMANDS.md` and run the narrowest relevant checks first.
Defer an E2E validator role until Playwright/Compose E2E exists.

## Design rules

- Follow `DESIGN.md` strictly.
- Use semantic design tokens.
- Never create a new visual language for a page.
- Every major screen must have desktop and mobile behavior.
- Desktop internal apps use sidebar + top bar + content + optional right panel.
- Mobile internal apps use top header + stacked cards + bottom sheets.

## State rules

Every data-driven page must support:

- loading
- empty
- error
- normal
- permission denied where relevant

## Event-driven rules

When adding asynchronous behavior:

- emit typed events
- use correlation IDs
- make consumers idempotent
- record audit entries where relevant
- define retry behavior
- define DLQ behavior for failures
- do not publish an event from a transaction without considering outbox/reliability

## Security rules

- Never log secrets.
- Never store raw API keys unencrypted.
- Never log document content, AI prompts, OCR text, or user file content by default.
- Use resource permissions for files, pages, workflows, integrations, and admin areas.
- Destructive AI/tool actions require confirmation.

## Testing expectations

For new frontend pages:

- add render test if test setup exists
- add Storybook story if Storybook exists
- verify mobile and desktop layouts

For backend modules:

- add unit tests for domain rules
- add integration tests for repository/event/worker behavior when possible
- use Testcontainers later for PostgreSQL/RabbitMQ/Redis/MinIO flows

## Output expectation for each task

When finishing a task, summarize:

1. files changed
2. design system components reused/created
3. responsive behavior
4. backend/API contracts changed
5. events emitted/consumed
6. tests run
7. assumptions/tradeoffs

## Development quality workflow

For any implementation task, also follow:

- `docs/development/DEVELOPMENT_WORKFLOW.md`
- `docs/development/QUALITY_GATES.md`
- `docs/development/CI_CD.md`
- `docs/development/FORMATTING_LINTING.md`
- `docs/development/SECURITY_AND_VULNERABILITY_CHECKS.md`
- `docs/development/TEST_COMMANDS.md`
- `docs/development/LOCAL_DEVELOPMENT_CHECKLIST.md`

Active workflows already exist under `.github/workflows/`. Keep templates in
`docs/templates/github-workflows/` deferred until their commands exist and pass
locally, especially E2E/Playwright workflows.

---

## Design references and mockups

Before implementing any screen, read:

1. `DESIGN.md`
2. `docs/development/SCREEN_SPECS.md`
3. `docs/design/SCREEN_CATALOG.md`
4. `docs/design/COMPONENT_INVENTORY.md`
5. the relevant mockup path listed in `SCREEN_CATALOG.md`

Mockups are visual references only. The implementation source of truth is:

- semantic design tokens
- reusable components
- written screen specifications
- accessibility rules
- responsive rules
- MVP priority

Do not implement a new layout pattern if an existing shell or component covers it. If a new component is justified, update `docs/design/COMPONENT_INVENTORY.md`.

## MVP scope guard

The design folder contains the long-term vision. Must not implement `P2` or `P3` screens unless explicitly instructed. First implementation should prioritize `P0` and the invoice automation vertical slice.

## Route planning

Before adding or changing routes, check `docs/development/ROUTES.md` and keep route names consistent with the catalog.
