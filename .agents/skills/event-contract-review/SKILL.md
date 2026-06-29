---
name: event-contract-review
description: Use when adding, changing, or reviewing Open Ecosystem OS events, producers, consumers, queues, outbox records, retry behavior, DLQs, event schemas, or event tests.
---

# Event Contract Review

Use the global repo-agnostic `event-contract-review` workflow first, then apply this repository context.

## Repo Context

Inspect only what is relevant:

- `AGENTS.md`
- `docs/architecture/EVENTS.md`
- `docs/development/QUALITY_GATES.md`
- affected producer, consumer, queue, outbox, migration, and test files

## Repo Guardrails

- Event names should be past-tense business facts unless the local event docs say otherwise.
- Check envelope fields: version, source, correlationId, causationId, idempotencyKey, and payload.
- Keep raw OCR text, document content, file content, AI prompts, and secrets out of payloads.
- Producers must consider transaction and outbox reliability.
- Consumers must validate version/type and remain idempotent.
- Retry, DLQ, and audit behavior should be documented or tested where relevant.

## Output

Return severity-ordered findings, required docs/tests, compatibility and privacy risks, and recommended narrow checks.
