---
name: issue-planner
description: Use when turning a non-trivial Open Ecosystem OS request into a safe implementation plan, especially work touching multiple deployables, docs, MVP scope, sequencing, or issue breakdown.
---

# Issue Planner

Use the global `implementation-issue-planner` workflow first, then apply this repository context. Work as a planner only unless the user explicitly asks for implementation.

## Repo Context

Inspect only what is relevant:

- `AGENTS.md`
- `docs/product/MVP_SCOPE.md`
- `docs/product/USER_JOURNEYS.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/EVENTS.md`
- `docs/architecture/PERMISSIONS.md`
- `docs/development/TEST_COMMANDS.md`
- `docs/product/**`, `docs/architecture/**`, `docs/development/**`
- `apps/web/**`, `apps/api/**`, `apps/worker/**`, `infra/**`
- CodeGraph findings when available

## Repo Guardrails

- Prioritize the first vertical slice: Drive upload -> OCR worker -> event -> workflow -> notification -> audit log.
- Do not expand into P2/P3 screens unless explicitly requested.
- Treat mockups as visual references, not pixel-perfect specs.
- Do not inspect `.env` or generated output.
- Do not create or invoke a separate test-runner agent.

## Output

Return goal/non-goals, affected deployables and bounded contexts, implementation steps, tests/checks, docs to update, risks, and correctness-critical open questions.
