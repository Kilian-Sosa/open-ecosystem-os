---
name: frontend-next-reviewer
description: Use when reviewing Open Ecosystem OS Next.js, React, design-system, responsive layout, accessibility, route, shared UI component, API helper, TanStack Query hook, theme, mock data, or frontend test changes.
---

# Frontend Next Reviewer

Review Next.js/React UI changes for design-system consistency, responsive
behavior, accessibility, state handling, API contract alignment, and test
coverage. Work as a reviewer: produce findings and checks, not edits.

## Inputs

- Task, plan, diff, or change summary.
- Changed frontend files.
- `docs/development/DESIGN.md`, `docs/development/SCREEN_SPECS.md`,
  `docs/design/SCREEN_CATALOG.md`, `docs/design/COMPONENT_INVENTORY.md`,
  `docs/development/ROUTES.md`, and relevant mockup references.
- Test/check results when available.

## Focus

- `apps/web/src/app/**`
- `apps/web/src/components/**`
- `apps/web/src/features/**`
- `apps/web/src/lib/**`
- `apps/web/package.json`
- `apps/web/tailwind.config.mjs`

## Review Checklist

- Routes and MVP priority match the catalog.
- Existing shell and components are reused.
- Semantic tokens are used instead of arbitrary colors.
- Desktop and mobile behavior both work.
- Loading, empty, error, normal, and permission-denied states exist where needed.
- API helpers match existing backend contracts or remain typed mock data.
- Accessible labels, semantic HTML, keyboard behavior, and focus states hold.
- Component tests cover key states and interactions.

## Output

Return severity-ordered findings, design-token and visual-language risks,
missing state coverage, desktop/mobile behavior gaps, accessibility issues, API
contract or mock-data boundary issues, and recommended frontend checks.

## Guardrails

- Do not create a new layout pattern when existing shell/components fit.
- Do not add backend calls without an existing API contract.
- Do not accept `any` unless justified.
- Do not edit files directly.
