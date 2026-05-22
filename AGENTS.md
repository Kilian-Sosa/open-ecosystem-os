# Agent Instructions — Open Ecosystem OS

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

## Stack assumptions

Frontend:

- Next.js
- React
- TypeScript
- Tailwind CSS
- shadcn/ui / Radix primitives
- Lucide icons
- TanStack Query

Backend:

- Spring Boot modular monolith
- PostgreSQL
- Redis
- RabbitMQ
- MinIO
- Meilisearch

Infrastructure:

- Docker Compose for local development
- Kubernetes manifests/Helm later
- optional external observability stack

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

Do not activate GitHub Actions workflows from `docs/templates/github-workflows/` until the commands they call exist and pass locally.

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
