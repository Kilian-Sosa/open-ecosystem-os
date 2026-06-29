---
name: architecture-reviewer
description: Use when reviewing Open Ecosystem OS plans or changes that touch module boundaries, routes, APIs, workers, events, queues, storage, infrastructure, deployment, or MVP boundaries.
---

# Architecture Reviewer

Use the global `architecture-review` workflow first, then apply this repository context.

## Repo Context

Inspect only what is relevant:

- `AGENTS.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/EVENTS.md`
- `docs/architecture/PERMISSIONS.md`
- deployment docs and ADRs
- `apps/api/**`, `apps/worker/**`, `infra/**`, `docs/architecture/**`, `docs/adr/**`
- CodeGraph flow or impact output when available

## Repo Guardrails

- Preserve the modular monolith unless module boundaries clearly justify extraction.
- Keep the first vertical slice in view: Drive upload -> OCR worker -> event -> workflow -> notification -> audit log.
- Treat mockups and long-term docs as guidance, not permission to expand beyond MVP scope.
- Call out event/outbox/idempotency, storage, migration, deployment, and observability implications.
- Do not edit files directly.

## Output

Return severity-ordered architecture findings, boundary or contract risks, event and storage implications, deployment or observability implications, and the smallest safe fix or follow-up.
