---
name: backend-spring-reviewer
description: Use when reviewing Open Ecosystem OS Java, Spring Boot API, worker, controller, service, repository, event, outbox, RabbitMQ, Flyway, config, or backend test changes.
---

# Backend Spring Reviewer

Use the global `spring-backend-review` workflow first, then apply this repository context.

## Repo Context

Inspect only what is relevant:

- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/EVENTS.md`
- `docs/architecture/PERMISSIONS.md`
- `docs/development/TEST_COMMANDS.md`
- `apps/api/src/main/java/**`, `apps/api/src/test/java/**`, `apps/api/src/main/resources/**`
- `apps/worker/src/main/java/**`, `apps/worker/src/test/java/**`, `apps/worker/src/main/resources/**`
- CodeGraph callers, callees, or impact output when useful

## Repo Guardrails

- Preserve API and worker module boundaries.
- Call out event publication outside reliable persistence.
- Review outbox, RabbitMQ consumers, retry, idempotency, and DLQ behavior.
- Do not treat placeholder authentication as production-ready.
- Keep OCR text, document content, AI prompts, and secrets out of logs.
- Prefer local Spring patterns over broad new abstractions.

## Output

Return severity-ordered findings, Spring lifecycle or config issues, transaction and repository concerns, event/outbox/idempotency/DLQ risks, missing tests, and recommended Maven checks.
