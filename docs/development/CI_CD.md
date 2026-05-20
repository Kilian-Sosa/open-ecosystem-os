# CI/CD Strategy

This project uses CI/CD as a design constraint. Code should be easy to test, scan, build, package, and deploy from the beginning.

GitHub Actions is the default CI/CD system. Workflow templates live under `docs/templates/github-workflows/` until the corresponding app skeletons are implemented. When a template becomes valid, copy it into `.github/workflows/`.

## Why templates first?

The starter repository intentionally contains placeholder app directories. Installing workflows too early would create failing GitHub checks before the actual Next.js/Spring Boot projects exist.

Activation rule:

```txt
Move a workflow from docs/templates/github-workflows/ to .github/workflows/ only when the commands it calls exist and pass locally.
```

## Workflow groups

### 1. Frontend CI

Validates `apps/web` and `packages/ui`.

Responsibilities:

- install dependencies
- format check
- lint
- typecheck
- unit/component tests
- build
- deferred Storybook build after Storybook configuration exists
- deferred Playwright component tests after Playwright configuration exists

### 2. Backend CI

Validates `apps/api` and backend modules.

Responsibilities:

- Java 25 setup
- Maven 3.9.15 bootstrap and dependency cache
- compile
- unit tests
- Spring slice tests
- integration tests with Testcontainers, initially on main/nightly
- package JAR

### 3. Worker CI

Validates `apps/worker` if worker is separate.

Responsibilities:

- compile
- unit tests
- event consumer tests
- package worker image/JAR

If the worker is initially part of the backend codebase, this can be merged into Backend CI.

### 4. Infrastructure CI

Validates infrastructure files.

Responsibilities:

- Docker Compose config validation
- Kubernetes YAML validation with kubeconform
- Kustomize build validation
- Trivy config/IaC scan

### 5. Security CI

Runs dependency and vulnerability scanning.

Responsibilities:

- Trivy filesystem scan
- OWASP Dependency-Check for Java dependencies
- npm/pnpm/yarn audit for frontend dependencies
- secret scanning
- container image scanning after images exist

### 6. E2E / Compose CI

Runs after the basic app skeleton, Playwright configuration, and smoke tests exist.

Responsibilities:

- start Docker Compose stack
- wait for health endpoints
- seed demo data
- run Playwright E2E smoke tests once the E2E harness is no longer deferred
- upload logs/artifacts on failure

### 7. Release CI

Runs on version tags.

Responsibilities:

- run full checks
- build Docker images
- scan Docker images
- push images to registry
- generate release notes
- publish deployment artifacts

## Branch strategy

Recommended:

```txt
main        → stable integration branch
feature/*   → feature branches
fix/*       → bugfix branches
chore/*     → CI/docs/maintenance branches
tag vX.Y.Z  → releases
```

No long-lived develop branch is needed at the beginning.

## Path filters

Use path filters to avoid running every workflow for every docs-only change.

Examples:

- Frontend CI triggers on `apps/web/**`, `packages/ui/**`, `packages/shared/**`.
- Backend CI triggers on `apps/api/**`, `packages/shared/**`.
- Infrastructure CI triggers on `infra/**`, `.github/workflows/**`.
- Docs checks trigger on `docs/**`, `README.md`, `AGENTS.md`, `DESIGN.md`.

Be careful: if a required workflow is skipped by path filters, GitHub can leave required checks pending depending on branch protection configuration. Keep required-check strategy simple until the repo matures.

## CI artifacts

On failures, upload:

- backend test reports
- frontend test reports
- Playwright traces/screenshots/videos, once Playwright is enabled
- Docker Compose logs
- Trivy reports
- Dependency-Check reports
- Kubernetes validation reports

## Deployment environments

Suggested environments:

```txt
local       → Docker Compose
preview     → optional per-PR environment later
staging     → Kubernetes staging namespace
production  → Kubernetes production namespace
```

Do not implement production deployment before the MVP vertical slice is stable.

## Deployment order

1. Build and test images.
2. Scan images.
3. Push images to registry.
4. Apply Kubernetes manifests to staging.
5. Run smoke tests.
6. Promote to production manually or with environment approval.

## Required secrets

Do not add secrets directly to workflow YAML.

Expected future secrets:

```txt
REGISTRY_USERNAME
REGISTRY_TOKEN
KUBE_CONFIG_STAGING
KUBE_CONFIG_PRODUCTION
SENTRY_DSN or OTEL endpoint, optional
```

For self-hosted development, prefer local `.env` files and Kubernetes Secrets.

## References

- GitHub Actions workflow syntax: https://docs.github.com/actions/reference/workflows-and-actions/workflow-syntax
- GitHub Actions product overview: https://github.com/features/actions
- Prettier CI guidance: https://prettier.io/docs/ci
