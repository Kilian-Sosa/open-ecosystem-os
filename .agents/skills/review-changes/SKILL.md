---
name: review-changes
description: Use when performing a focused code review of current Open Ecosystem OS changes for regressions, stale docs, contract mismatches, missing tests, generated files, frontend gaps, backend event/security gaps, or migration risks.
---

# Review Changes

Review the current changes in this repository. Work as a code reviewer:
findings first, ordered by severity. Do not edit files unless explicitly asked.

## Inputs

- Current working-tree diff, commit diff, or PR diff.
- `AGENTS.md`, `docs/development/DEVELOPMENT_WORKFLOW.md`,
  `docs/development/QUALITY_GATES.md`, `docs/development/TEST_COMMANDS.md`,
  `docs/architecture/EVENTS.md`, and `docs/architecture/PERMISSIONS.md`.

## Review Checklist

- Behavioral regressions.
- Stale docs or contract mismatches.
- Missing tests.
- Generated files touched by mistake.
- Frontend state, accessibility, or responsive gaps.
- Backend event, outbox, idempotency, or security gaps.
- Migration or data integrity risks.

## Output

Return findings first, ordered by severity, with file references. Then list
open questions, checks to run, and a short summary.
