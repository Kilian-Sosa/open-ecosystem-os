---
name: frontend-screen-review
description: Use when reviewing an Open Ecosystem OS frontend screen before or after implementation, especially route, MVP priority, design token, shell/component reuse, responsive behavior, accessibility, state coverage, API boundary, or component test concerns.
---

# Frontend Screen Review

Use the global repo-agnostic `frontend-screen-review` workflow first, then apply this repository context.

## Repo Context

Inspect only what is relevant:

- `AGENTS.md`
- `docs/development/DESIGN.md`
- `docs/development/SCREEN_SPECS.md`
- `docs/design/SCREEN_CATALOG.md`
- `docs/design/COMPONENT_INVENTORY.md`
- `docs/development/ROUTES.md`
- relevant mockup path from the screen catalog
- changed screen, component, hook, data, API helper, and test files

## Repo Guardrails

- Route name and MVP priority must match the catalog.
- Reuse existing shell/components before proposing new layout patterns.
- Use semantic tokens and existing visual language.
- Verify desktop and mobile behavior.
- Check loading, empty, error, normal, and permission-denied states where relevant.
- API helpers must match existing backend contracts or remain typed mock data.

## Output

Return severity-ordered findings, recommended fixes, state/responsive/accessibility gaps, API boundary concerns, and frontend checks to run.
