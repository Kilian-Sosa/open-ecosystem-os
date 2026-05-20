# Quality Gates

Quality gates define what must pass before code can be merged, released, or deployed.

The project must optimize for confidence in the flagship journey:

```txt
Upload PDF invoice
→ store file
→ emit FileUploaded
→ OCR worker processes it
→ emit OcrCompleted
→ workflow runs
→ notification is created
→ audit log records everything
→ search finds extracted content
```

## Gate levels

### Level 0 — Local pre-commit sanity

Run before committing when the touched area supports it.

Required:

- formatting check
- lint check
- typecheck/compile
- relevant unit tests

Expected commands once apps are bootstrapped:

```bash
make format-check
make lint
make test-unit
```

### Level 1 — Pull request gate

Runs on every PR.

Required:

- repository formatting check
- frontend lint/typecheck/unit/component tests
- backend compile/unit/slice tests
- worker compile/unit tests
- event schema validation
- Kubernetes manifest validation
- lightweight dependency scan
- secret scan

PRs should not merge if this gate fails.

### Level 2 — Main branch integration gate

Runs after merge to `main`.

Required:

- all PR checks
- integration tests with Testcontainers
- Docker image build
- Docker Compose smoke test
- Playwright E2E smoke test against Compose, deferred until the E2E harness exists
- Trivy filesystem/image scan
- generated artifacts published as CI artifacts

### Level 3 — Nightly gate

Runs on schedule.

Required:

- full integration suite
- full Playwright suite, deferred until Playwright is configured
- k6 performance smoke tests
- kind Kubernetes deployment smoke test
- backup/restore smoke test
- full security scan
- dependency vulnerability report

### Level 4 — Release gate

Runs before tagged release.

Required:

- all main branch checks
- migration tests
- restore-from-backup test
- full E2E suite
- container image scans
- SBOM generation, if enabled
- release notes/changelog update
- versioned Docker images

## Merge policy

A PR is mergeable only when:

- Level 1 checks pass.
- Any Level 2-required deferral is documented.
- The PR has a clear summary.
- New functionality has tests or a documented reason for deferral.
- New environment variables are added to `.env.example`.
- New events/permissions are documented.

## Vulnerability policy

Initial policy:

- Critical vulnerabilities: block merge.
- High vulnerabilities: block merge unless explicitly documented as false positive or not exploitable in this context.
- Medium vulnerabilities: create follow-up issue unless easy to fix.
- Low vulnerabilities: track but do not block.

For development dependencies, evaluate exploitability, but do not ignore critical/high findings casually.

## Formatting policy

Formatting is not subjective. If the formatter says the code is wrong, fix it.

Rules:

- Frontend: Prettier + ESLint.
- Backend Java: Spotless with Google Java Format.
- Markdown/YAML/JSON: Prettier where practical.
- Generated files: do not format manually unless they are committed source files.

## Test coverage policy

Do not chase a meaningless global number early.

Minimum expectations:

- New domain logic: unit tests required.
- New API endpoint: controller/slice or integration test required.
- New worker behavior: event/worker test required.
- New UI behavior: component or E2E test required.
- New event contract: schema/example/producer/consumer test required.
- Critical journey changes: E2E update required.

Coverage thresholds can be introduced later once the architecture stabilizes.

## Allowed temporary exceptions

Temporary exceptions are allowed only if documented in the PR:

```md
## Quality exception
Check deferred: ...
Reason: ...
Risk: ...
Follow-up issue: ...
Deadline: ...
```

Never silently skip checks.
