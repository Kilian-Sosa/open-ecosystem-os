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

## Tooling requirements

Use pnpm 10 (the exact version is pinned in `package.json`) and Node.js
`^20.19.0 || ^22.13.0 || >=24`. This is the Node.js range required by ESLint 10
and the existing test tooling. Web and security CI resolve the latest Node.js
22 release; the web container uses Node.js 24.

Next.js and `eslint-config-next` are upgraded together. The flat configuration
in `eslint.config.mjs` uses the Next.js Core Web Vitals and TypeScript presets;
run the normal ESLint CLI with `pnpm lint`. Editors should use the workspace's
installed ESLint and flat configuration.

Next.js 16.4 supplies ESLint 10 compatibility for its bundled Babel parser and
the React/import plugins. pnpm may report unmet ESLint peer ranges for
`eslint-plugin-react`, `eslint-plugin-import`, and `eslint-plugin-jsx-a11y`;
these upstream metadata warnings do not require local adapters, overrides, or
peer-check bypasses. Installation and lint execution must still succeed.
See [Next.js ESLint 10 support](https://github.com/vercel/next.js/pull/99628).

After dependency updates, run both audits:

```bash
pnpm audit --prod --audit-level high
pnpm audit --audit-level high
```

Temporary advisory exceptions are listed in
[SECURITY_EXCEPTIONS.md](../../docs/development/SECURITY_EXCEPTIONS.md).
The configured braces exception is advisory-specific and must be removed when
a compatible upstream fix is available.

## UI rules

Before implementing or changing a screen, read the root `AGENTS.md`, the design
contract, `docs/development/SCREEN_SPECS.md`,
`docs/design/SCREEN_CATALOG.md`, `docs/design/COMPONENT_INVENTORY.md`, and
`docs/development/ROUTES.md`.

Use existing shell and UI components before adding new ones. Every data-driven
screen should keep loading, empty, error, normal, and permission-denied states
where relevant.
