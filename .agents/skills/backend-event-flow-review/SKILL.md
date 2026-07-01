---
name: backend-event-flow-review
description: Use when reviewing Open Ecosystem OS backend async behavior, worker flows, RabbitMQ queues, outbox behavior, OCR flow, workflow execution, notifications, audit logs, or the flagship Drive upload to OCR to workflow vertical slice.
---

# Backend Event Flow Review

Use the global `event-flow-review` workflow first, then apply this repository context.

## Repo Context

Inspect only what is relevant:

- `AGENTS.md`
- `docs/product/USER_JOURNEYS.md`
- `docs/architecture/ARCHITECTURE.md`
- `docs/architecture/EVENTS.md`
- `docs/architecture/PERMISSIONS.md`
- affected API, worker, repository, migration, config, and test files

## Repo Guardrails

- Prioritize the flagship path: Drive upload -> OCR worker -> event -> workflow -> notification -> audit log.
- Check that domain state and outbox writes are reliable.
- Preserve correlation and causation IDs.
- Require idempotent consumers, intentional retry behavior, and DLQ handling.
- Keep raw OCR text, document content, file content, AI prompts, and secrets out of events, logs, audit attributes, search documents, and notifications.

## Output

Return a concise flow summary, severity-ordered findings, missing tests, and recommended Maven checks.
