---
name: refine-prompt
description: Use when turning a rough Open Ecosystem OS request into a precise, implementation-ready Codex prompt with goals, context, requirements, non-goals, assumptions, open questions, suggested naming, and verification requirements.
---

# Refine A Prompt

Use the global `refine-implementation-prompt` workflow first, then apply this repository context. Work as a prompt editor and technical planner; do not implement unless the user asks.

## Repo Context

Inspect only what is needed:

- `AGENTS.md`
- `docs/development/DEVELOPMENT_WORKFLOW.md`
- `docs/development/QUALITY_GATES.md`
- `docs/development/TEST_COMMANDS.md`
- `docs/product/MVP_SCOPE.md` and `docs/product/USER_JOURNEYS.md` for product flows
- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/EVENTS.md` for async behavior
- `docs/architecture/PERMISSIONS.md` for auth, RBAC, files, OCR, AI, settings, admin, workflows, search, notifications, audit, or finance data
- frontend design, screen, catalog, inventory, and route docs for UI work
- `instructions/backend-endpoint-naming.md` for backend endpoints, controllers, or OpenAPI naming
- `git status --short --branch`
- CodeGraph when structural context would reduce guesswork

## Repo Guardrails

- Preserve user intent and product language while removing ambiguity.
- Prefer existing project patterns, routes, components, endpoint shapes, Java records/DTOs, services, repositories, event envelope fields, response wrappers, validation conventions, and test style.
- Keep scope to MVP/P0/P1 unless the user explicitly asks for P2/P3 work.
- Do not inspect or print real secret values.

## Output

Return a paste-ready prompt, key assumptions, correctness-critical open questions, suggested naming, and recommended checks from local docs.
