---
name: refine-prompt
description: Use when turning a rough Open Ecosystem OS request into a precise, implementation-ready Codex prompt with goals, context, requirements, non-goals, assumptions, open questions, suggested naming, and verification requirements.
---

# Refine A Prompt

Refine a rough request into a precise, actionable prompt that another Codex
session can execute in the Open Ecosystem OS repository. Work as a prompt editor
and technical planner; do not implement the feature.

## Inputs

Use the user's rough request as the main argument. Optional context may include
named files, target deployable, route, endpoint, screen, event, issue link,
desired level of detail, or known constraints. If inputs are missing, inspect
only what is needed and make assumptions explicit.

## Rules

- Do not implement the requested feature.
- Do not edit, stage, commit, push, rebase, merge, delete, move, or create
  source files unless the user explicitly asks you to save the refined prompt.
- Do not inspect or print real secret values.
- Preserve user intent and product language while removing ambiguity,
  duplication, and unclear naming.
- Separate confirmed requirements from assumptions and open questions.
- Prefer existing project patterns, routes, components, endpoint shapes, Java
  records/DTOs, services, repositories, event envelope fields, response
  wrappers, validation conventions, and test style.
- Call out compatibility, security, privacy, database, migration, performance,
  event, permission, infrastructure, and responsive UI concerns when relevant.
- Keep scope to MVP/P0/P1 unless the user explicitly asks for P2/P3 work.

## Inspect Only What Is Needed

- The rough request.
- `AGENTS.md` and referenced local guidance.
- `docs/development/DEVELOPMENT_WORKFLOW.md`,
  `docs/development/QUALITY_GATES.md`, and
  `docs/development/TEST_COMMANDS.md`.
- `docs/product/MVP_SCOPE.md` and `docs/product/USER_JOURNEYS.md` when product
  flows are involved.
- `docs/architecture/ARCHITECTURE.md`.
- `docs/architecture/EVENTS.md` for events, workers, queues, outbox,
  notifications, audit, search, or async behavior.
- `docs/architecture/PERMISSIONS.md` for auth, RBAC, files, OCR, AI, settings,
  admin, workflows, search, notifications, audit, or finance data.
- `docs/development/DESIGN.md`, `docs/development/SCREEN_SPECS.md`,
  `docs/design/SCREEN_CATALOG.md`, `docs/design/COMPONENT_INVENTORY.md`, and
  `docs/development/ROUTES.md` for frontend screens or routes.
- `instructions/backend-endpoint-naming.md` if present and backend endpoints,
  controllers, or OpenAPI naming are involved.
- `git status --short --branch`.
- Existing files named in the rough request and nearby implementation needed to
  understand behavior.
- `codegraph status` and targeted CodeGraph queries when structural context
  would reduce guesswork. If CodeGraph is not initialized, note that and do not
  create or commit `.codegraph/`.

## Output

### Refined Prompt

Write a paste-ready prompt with:

- Goal.
- Existing context and relevant files.
- Required behavior.
- Non-goals and scope boundaries.
- Affected deployables and bounded contexts.
- Frontend route, state, accessibility, design-system, and responsive
  expectations when relevant.
- API request/response expectations when relevant.
- Data, query, migration, and storage rules when relevant.
- Event, outbox, retry, DLQ, idempotency, correlation, notification, search,
  and audit expectations when relevant.
- Validation and error handling.
- Security, privacy, and authorization checks.
- Backward compatibility constraints.
- Testing and verification requirements, with narrow checks first.
- Documentation updates required.
- Definition of done.

### Key Assumptions

List assumptions required to make the prompt actionable.

### Open Questions

Ask only questions that materially affect correctness, data semantics, API
compatibility, security/privacy, events, permissions, infrastructure,
responsive behavior, or user-visible behavior.

### Suggested Naming

Suggest route, endpoint, parameter, DTO/record, method, field, event, queue,
component, hook, and test names when the rough request uses tentative names.

### Recommended Checks

List relevant local commands from `docs/development/TEST_COMMANDS.md` and the
GitHub Actions workflows that should catch regressions.
