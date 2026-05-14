# Development Workflow

This document defines how development of Open Ecosystem OS should be conducted without creating architectural drift, broken CI, or inconsistent UI.

Should treat the repository as a product system, not as a collection of unrelated tasks. Every change must preserve the project contracts in `AGENTS.md`, `DESIGN.md`, `SCREEN_SPECS.md`, and the architecture documents.

## Required reading before any coding task

For every non-trivial task, must read:

1. `AGENTS.md`
2. `DESIGN.md`
3. `docs/product/MVP_SCOPE.md`
4. `docs/architecture/ARCHITECTURE.md`
5. `docs/architecture/EVENTS.md`
6. `docs/architecture/PERMISSIONS.md`
7. The relevant module documentation under `docs/`
8. The files it is about to change

If the task touches CI/CD, quality checks, security, containers, or Kubernetes, must also read:

1. `docs/development/CI_CD.md`
2. `docs/development/QUALITY_GATES.md`
3. `docs/development/FORMATTING_LINTING.md`
4. `docs/development/SECURITY_AND_VULNERABILITY_CHECKS.md`
5. `docs/development/TEST_COMMANDS.md`
6. `docs/architecture/DEPLOYMENT.md`
7. `docs/architecture/OBSERVABILITY.md`

## Operating mode

Should work in small vertical slices. A slice should include code, tests, documentation updates, and the relevant quality checks.

Good task shape:

```txt
Implement Drive file upload metadata + object storage integration + tests + docs update.
```

Bad task shape:

```txt
Build the whole Drive, PDF editor, automations, AI assistant, and admin dashboard.
```

## Mandatory workflow per task

Must follow this sequence:

1. Restate the task in implementation terms.
2. Inspect relevant files.
3. Identify affected modules.
4. Produce a short implementation plan.
5. Make the smallest coherent code changes.
6. Add or update tests.
7. Run the narrowest relevant checks first.
8. Run broader checks when the narrow checks pass.
9. Update docs if behavior, architecture, commands, events, permissions, or deployment changed.
10. Summarize changed files, checks run, results, and remaining risks.

## Planning rule

Before editing, must answer:

- Which deployable is affected? `web`, `api`, `worker`, or `infra`?
- Which bounded context is affected?
- Which tests need to be added or changed?
- Which CI workflow would catch a regression?
- Is any event contract affected?
- Is any permission/security rule affected?
- Is any Docker/Kubernetes/observability configuration affected?

## Definition of done for any feature

A feature is not done until all relevant items are true:

- Code compiles.
- Formatting passes.
- Linting passes.
- Unit tests pass.
- Relevant integration tests pass or are explicitly deferred with a reason.
- Affected E2E test exists or is explicitly deferred with a reason.
- No known high/critical dependency vulnerability is introduced.
- New environment variables are documented in `.env.example`.
- New events are documented in `docs/architecture/EVENTS.md`.
- New permissions are documented in `docs/architecture/PERMISSIONS.md`.
- New infrastructure assumptions are documented.
- UI follows `DESIGN.md` and responsive rules.

## What must not do

Must not:

- Invent a new architecture without updating ADRs.
- Add random colors or one-off UI styles.
- Add a new dependency without explaining why.
- Disable tests to make CI pass.
- Ignore failing checks.
- Store secrets in code, docs, or examples.
- Add generated build artifacts to Git.
- Add temporary files outside approved scratch locations.
- Change unrelated files to “clean up” unless requested.
- Build a feature only in the frontend without a clear backend/mock boundary.
- Add arbitrary CSS for user themes.
- Add unsafe PDF redaction based only on visual overlays.

## Preferred task size

A good task should fit into one pull request and should usually change fewer than 20 files.

Exceptions are allowed for:

- initial scaffold creation
- dependency migration
- CI/CD setup
- design-system foundation
- broad formatting pass

## How to handle failed checks

When a check fails, must:

1. Read the exact error.
2. Identify whether it is caused by the current change or pre-existing state.
3. Fix current-change failures.
4. Do not hide the failure.
5. Re-run the failing check.
6. Report unresolved pre-existing failures clearly.

## summary format

At the end of each task, should produce:

```md
## Summary
- ...

## Files changed
- ...

## Checks run
- `command` — passed/failed

## Architecture impact
- Events: ...
- Permissions: ...
- Infrastructure: ...

## Risks / follow-ups
- ...
```

## References

- OpenAI CLI: https://help.openai.com/en/articles/11096431
- GitHub Actions workflow syntax: https://docs.github.com/actions/reference/workflows-and-actions/workflow-syntax
