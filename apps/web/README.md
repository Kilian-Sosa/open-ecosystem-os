# apps/web

Next.js frontend for Open Ecosystem OS.

Responsibilities:

- public portfolio
- authenticated workspace
- dashboard
- Drive UI
- Media/OCR UI
- Open Pages UI
- Open Ecosystem Flows UI
- admin/platform UI
- design system showcase

## Current setup

The app already exists. Do not run `create-next-app` in this directory.

Current baseline:

- Next.js App Router under `src/app`
- React and TypeScript
- Tailwind CSS with semantic design tokens in `src/app/globals.css`
- TanStack Query providers in `src/components/providers`
- reusable local UI components in `src/components/ui`
- feature screens under `src/features`
- typed API helpers and mock data under `src/lib`
- Vitest and Testing Library for render/unit tests

## Local commands

Run from `apps/web`:

```bash
corepack pnpm install
corepack pnpm format:check
corepack pnpm lint
corepack pnpm typecheck
corepack pnpm test
corepack pnpm build
```

From the repository root, prefer the matching `make` targets when a task spans
multiple deployables.

## UI rules

Before implementing or changing a screen, read the root `AGENTS.md`, the design
contract, `docs/development/SCREEN_SPECS.md`,
`docs/design/SCREEN_CATALOG.md`, `docs/design/COMPONENT_INVENTORY.md`, and
`docs/development/ROUTES.md`.

Use existing shell and UI components before adding new ones. Every data-driven
screen should keep loading, empty, error, normal, and permission-denied states
where relevant.
