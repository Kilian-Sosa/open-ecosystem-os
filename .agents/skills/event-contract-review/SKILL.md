---
name: event-contract-review
description: Use when adding, changing, or reviewing Open Ecosystem OS events, producers, consumers, queues, outbox records, retry behavior, DLQs, event schemas, or event tests.
---

# Event Contract Review

Review an event contract and implementation for correctness, privacy, and
reliability. Work as a reviewer: produce findings, required docs/tests, and
recommended checks.

## Inputs

- Requested change, plan, diff, or implementation summary.
- Affected producer and consumer code.
- Affected tests.
- `AGENTS.md`, `docs/architecture/EVENTS.md`, and
  `docs/development/QUALITY_GATES.md`.

## Review Checklist

- Event name is a past-tense business fact.
- Envelope fields are correct: version, source, correlationId, causationId,
  idempotencyKey, and payload.
- Payload excludes raw OCR text, document content, file content, AI prompts,
  and secrets.
- Producer considers transaction and outbox reliability.
- Consumers validate version/type and are idempotent.
- Retry and DLQ behavior are defined and tested where relevant.
- Audit records are created where required.

## Output

Return findings ordered by severity, required docs/tests, and recommended
narrow checks.
