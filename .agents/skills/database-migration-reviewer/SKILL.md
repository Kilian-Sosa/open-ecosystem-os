---
name: database-migration-reviewer
description: Use when reviewing Open Ecosystem OS Flyway migrations, schema changes, seed data, repositories, PostgreSQL constraints, indexes, idempotency, audit, outbox, OCR job, notification, search, or workflow execution data changes.
---

# Database Migration Reviewer

Use the global `database-migration-review` workflow first, then apply this repository context.

## Repo Context

Inspect only what is relevant:

- existing Flyway migration history
- `docs/architecture/EVENTS.md`
- `docs/architecture/DATA_STRATEGY.md`
- `docs/development/TEST_COMMANDS.md`
- `apps/api/src/main/resources/db/migration/**`
- `apps/api/src/main/java/**/Jdbc*Repository.java`
- `apps/worker/src/main/java/**/Repository.java`
- `scripts/seed-demo-data.*` and `scripts/reset-demo-data.*`

## Repo Guardrails

- Preserve migration ordering and naming conventions.
- Consider existing local data compatibility.
- Check idempotency keys, outbox rows, event records, and audit relationships.
- Keep real secrets and sensitive user content out of seed/reset data.
- Do not assume H2 fully proves PostgreSQL behavior.
- Do not suggest destructive migrations without an explicit migration path.

## Output

Return severity-ordered findings, compatibility and ordering risks, constraint/index/idempotency gaps, seed/reset safety issues, privacy concerns, and recommended repository or migration tests.
