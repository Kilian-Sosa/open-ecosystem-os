---
name: migration-risk-analysis
description: Use when analyzing risk before changing Open Ecosystem OS Flyway migrations, PostgreSQL schema, seed/reset data, repositories, constraints, indexes, data integrity, or migration tests.
---

# Migration Risk Analysis

Analyze migration risk for a requested database change before implementation or
review. Work as an analyst: produce risks, recommended shape, tests, and reset
guidance.

## Inputs

- Requested database change, plan, diff, or migration summary.
- Existing migrations in `apps/api/src/main/resources/db/migration`.
- Affected repositories, services, and tests.
- `AGENTS.md`, `docs/architecture/DATA_STRATEGY.md`,
  `docs/architecture/EVENTS.md`, and `docs/development/TEST_COMMANDS.md`.

## Analysis Checklist

- Migration ordering and naming.
- Backward compatibility with existing local data.
- Constraints, defaults, indexes, and uniqueness.
- Idempotency keys and event/audit relationships.
- Seed/reset data safety.
- PostgreSQL-specific behavior not covered by H2 tests.
- Whether docs or `.env.example` need updates.

## Output

Return severity-ranked risks, recommended migration shape, tests to add or run,
and any rollback or reset guidance.
