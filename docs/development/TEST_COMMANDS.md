# Test Commands

This document defines the command names that should be expected and preserved. The concrete commands may be implemented as the app skeletons are created.

## Root Makefile targets

Recommended root-level targets:

```bash
make install
make format
make format-check
make lint
make typecheck
make test
make test-unit
make test-integration
make test-e2e
make build
make docker-up
make docker-down
make docker-logs
make smoke
make security-scan
make k8s-validate
make ci-local
```

## Frontend commands

Expected under `apps/web`:

```bash
pnpm install
pnpm format:check
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

Deferred until the matching tooling/configuration exists:

```bash
pnpm storybook
pnpm build-storybook
pnpm playwright test
```

## Backend commands

Expected under `apps/api`:

```bash
./mvnw spotless:check
./mvnw spotless:apply
./mvnw test
./mvnw verify
./mvnw package
./mvnw spring-boot:run
```

Recommended Maven profiles later:

```bash
./mvnw verify -P integration-tests
./mvnw verify -P security-scan
```

## Worker commands

If worker is a separate app:

```bash
cd apps/worker
./mvnw test
./mvnw verify
./mvnw package
```

If worker lives inside backend initially, use backend commands.

## Docker Compose commands

```bash
cd infra/docker
docker compose up -d
docker compose ps
docker compose logs -f api
docker compose down -v
```

With observability profile/file:

```bash
cd infra/docker
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d
```

## Kubernetes validation commands

```bash
kustomize build infra/k8s/base | kubeconform -strict -summary
trivy config infra/k8s
```

## Security commands

```bash
trivy fs --severity HIGH,CRITICAL --exit-code 1 .
trivy config infra/k8s
```

Backend dependency check after Maven setup:

```bash
cd apps/api
./mvnw verify -P security-scan
```

Frontend audit after package manager selection:

```bash
cd apps/web
pnpm audit --audit-level high
```

## Local check preference

Should run the narrowest relevant command first.

Example for a frontend component change:

```bash
cd apps/web
pnpm test -- ComponentName
pnpm lint
pnpm typecheck
```

Example for a backend domain change:

```bash
cd apps/api
./mvnw -Dtest=WorkflowGraphValidatorTest test
./mvnw test
```

Example for an infrastructure change:

```bash
kustomize build infra/k8s/base | kubeconform -strict -summary
trivy config infra/k8s
```
