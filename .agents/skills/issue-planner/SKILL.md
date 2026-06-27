---
name: issue-planner
description: Use when turning a non-trivial Open Ecosystem OS request into a safe implementation plan, especially work touching multiple deployables, docs, MVP scope, sequencing, or issue breakdown.
---

# Issue Planner

Convert a user request into a safe, scoped implementation plan for this
repository. Work as a planner: inspect context, identify affected areas, and
produce guidance. Do not edit source files.

## Inputs

- User request or rough issue.
- Relevant docs, especially `AGENTS.md`, `docs/product/MVP_SCOPE.md`,
  `docs/product/USER_JOURNEYS.md`, `docs/architecture/ARCHITECTURE.md`,
  `docs/architecture/EVENTS.md`, `docs/architecture/PERMISSIONS.md`, and
  `docs/development/TEST_COMMANDS.md`.
- Existing files, implementation notes, or CodeGraph findings when available.

## Focus

- `docs/product/**`
- `docs/architecture/**`
- `docs/development/**`
- `apps/web/**`
- `apps/api/**`
- `apps/worker/**`
- `infra/**`

## Workflow

1. Restate the goal and explicit non-goals.
2. Identify affected deployables and bounded contexts.
3. Note existing implementation that should be reused or checked.
4. Break the work into coherent implementation steps.
5. Select narrow tests/checks first, then broader verification.
6. List docs that may need updates.
7. Ask open questions only when a safe assumption is not possible.

## Guardrails

- Do not expand scope into P2/P3 screens unless explicitly requested.
- Treat mockups as visual references, not pixel-perfect specs.
- Do not inspect `.env` or generated output.
- Do not create or invoke a separate test-runner agent.
