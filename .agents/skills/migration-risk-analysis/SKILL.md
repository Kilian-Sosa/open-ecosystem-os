---
name: migration-risk-analysis
description: Use when analyzing risk before changing Open Ecosystem OS Flyway migrations, PostgreSQL schema, seed/reset data, repositories, constraints, indexes, data integrity, or migration tests.
---

# Migration Risk Analysis

Use the global repo-agnostic `migration-risk-analysis` workflow first, then apply this repository context.

## Repo Context

Inspect only what is relevant:

- `AGENTS.md`
- `docs/architecture/DATA_STRATEGY.md`
- `docs/architecture/EVENTS.md`
- `docs/development/TEST_COMMANDS.md`
- existing migrations in `apps/api/src/main/resources/db/migration`
- affected repositories, services, seed/reset scripts, and tests

## Repo Guardrails

- Check Flyway ordering and naming.
- Consider backward compatibility with existing local data.
- Review constraints, defaults, indexes, uniqueness, idempotency keys, and event/audit relationships.
- Keep seed/reset data safe and fake.
- Note PostgreSQL behavior not covered by H2 tests.
- Check whether docs or `.env.example` need updates.

## Output

Return severity-ranked risks, recommended migration shape, tests to add or run, and rollback or reset guidance.
