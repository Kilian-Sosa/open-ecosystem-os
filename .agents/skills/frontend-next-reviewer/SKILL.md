---
name: frontend-next-reviewer
description: Use when reviewing Open Ecosystem OS Next.js, React, design-system, responsive layout, accessibility, route, shared UI component, API helper, TanStack Query hook, theme, mock data, or frontend test changes.
---

# Frontend Next Reviewer

Use the global `nextjs-frontend-review` workflow first, then apply this repository context.

## Repo Context

Inspect only what is relevant:

- `docs/development/DESIGN.md`
- `docs/development/SCREEN_SPECS.md`
- `docs/design/SCREEN_CATALOG.md`
- `docs/design/COMPONENT_INVENTORY.md`
- `docs/development/ROUTES.md`
- relevant mockup references
- `apps/web/src/app/**`, `apps/web/src/components/**`, `apps/web/src/features/**`, `apps/web/src/lib/**`
- `apps/web/package.json`, `apps/web/tailwind.config.mjs`

## Repo Guardrails

- Match route names and MVP priority from the catalog.
- Reuse the existing shell and components.
- Use semantic design tokens instead of arbitrary colors.
- Cover desktop and mobile behavior.
- Data-driven pages need loading, empty, error, normal, and permission-denied states where relevant.
- Do not add backend calls without an existing API contract.
- Do not accept `any` unless justified.

## Output

Return severity-ordered findings, design-token risks, state coverage gaps, desktop/mobile issues, accessibility issues, API/mock boundary concerns, and recommended frontend checks.
