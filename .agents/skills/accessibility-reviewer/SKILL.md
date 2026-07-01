---
name: accessibility-reviewer
description: Use when reviewing Open Ecosystem OS UI, UX, frontend, React, Next.js, component, form, modal, menu, drawer, responsive, or PR changes for accessibility, keyboard usability, semantic HTML, ARIA, focus management, contrast, screen-reader states, or WCAG-oriented risk.
---

# Accessibility Reviewer

Use the global `accessibility-reviewer` workflow first, then apply this repository context.

## Repo Context

Inspect only what is relevant:

- `docs/development/DESIGN.md`
- `docs/development/SCREEN_SPECS.md`
- `docs/design/SCREEN_CATALOG.md`
- `docs/design/COMPONENT_INVENTORY.md`
- changed files under `apps/web/src/app/**`, `apps/web/src/components/**`, `apps/web/src/features/**`, and related tests
- existing shared UI, Radix/shadcn, Tailwind token, Testing Library, and accessibility test patterns

## Repo Guardrails

- Preserve `DESIGN.md` requirements for semantic design tokens, visible focus states, status text, reduced motion, and desktop/mobile behavior.
- Check the written screen specs and catalog before treating a mockup as implementation truth.
- Review state coverage for loading, empty, error, normal, and permission-denied states when data-driven UI is touched.
- Prefer existing shared components and Radix/shadcn primitives before recommending custom interactive UI.
- Do not introduce runtime accessibility libraries unless the task explicitly asks for implementation and the dependency is justified.

## Output

Return the global accessibility review format plus Open Ecosystem OS-specific design-token risks, state coverage gaps, responsive/mobile concerns, and recommended frontend checks.
