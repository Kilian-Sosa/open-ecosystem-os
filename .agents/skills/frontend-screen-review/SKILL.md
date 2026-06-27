---
name: frontend-screen-review
description: Use when reviewing an Open Ecosystem OS frontend screen before or after implementation, especially route, MVP priority, design token, shell/component reuse, responsive behavior, accessibility, state coverage, API boundary, or component test concerns.
---

# Frontend Screen Review

Review a frontend screen against the Open Ecosystem OS design and state
requirements. Work as a reviewer: produce findings, fixes, and frontend checks.

## Inputs

- Screen request, plan, diff, or implementation summary.
- Changed screen, component, hook, data, and test files.
- `AGENTS.md`, `docs/development/DESIGN.md`,
  `docs/development/SCREEN_SPECS.md`, `docs/design/SCREEN_CATALOG.md`,
  `docs/design/COMPONENT_INVENTORY.md`, `docs/development/ROUTES.md`, and the
  relevant mockup path from the screen catalog.

## Review Checklist

- Route name and MVP priority are correct.
- Existing shell/components are reused.
- Semantic tokens are used instead of arbitrary colors.
- Desktop and mobile behavior both work.
- Loading, empty, error, normal, and permission-denied states exist where
  relevant.
- API helpers match existing backend contracts or remain typed mock data.
- Accessible labels, semantic HTML, keyboard behavior, and focus states hold.
- Component tests cover key states.

## Output

Return findings ordered by severity, recommended fixes, and frontend checks to
run.
