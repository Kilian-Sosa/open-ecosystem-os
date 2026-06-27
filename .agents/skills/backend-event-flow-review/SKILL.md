---
name: backend-event-flow-review
description: Use when reviewing Open Ecosystem OS backend async behavior, worker flows, RabbitMQ queues, outbox behavior, OCR flow, workflow execution, notifications, audit logs, or the flagship Drive upload to OCR to workflow vertical slice.
---

# Backend Event Flow Review

Review backend event flow for the requested change, especially the flagship
path: Drive upload -> OCR worker -> event -> workflow -> notification -> audit
log. Work as a reviewer: produce findings, missing tests, and checks.

## Inputs

- Requested change, plan, diff, or implementation summary.
- Affected API, worker, repository, migration, and test files.
- `AGENTS.md`, `docs/product/USER_JOURNEYS.md`,
  `docs/architecture/ARCHITECTURE.md`, `docs/architecture/EVENTS.md`, and
  `docs/architecture/PERMISSIONS.md`.

## Review Checklist

- Domain state and outbox writes happen reliably.
- Correlation and causation IDs are preserved.
- Consumers validate event type/version and are idempotent.
- Retry and DLQ behavior are intentional.
- Audit records are present where relevant.
- Event payloads avoid raw OCR text, document content, file content, AI prompts,
  and secrets.
- Failure paths are tested or explicitly deferred.

## Output

Return a concise flow summary, findings ordered by severity, missing tests, and
recommended Maven checks.
