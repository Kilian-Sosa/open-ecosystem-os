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
make docker-watch
make docker-down
make docker-logs
make smoke
make smoke-real-ocr
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
./mvnw spotless:check
./mvnw spotless:apply
./mvnw test
./mvnw verify
./mvnw package
./mvnw verify -P security-scan
```

If worker lives inside backend initially, use backend commands.

## Docker Compose commands

```bash
cd infra/docker
docker compose up -d
docker compose up --build --watch
docker compose ps
docker compose logs -f api
docker compose down -v
```

With observability profile/file:

```bash
cd infra/docker
docker compose -f docker-compose.yml -f docker-compose.observability.yml --profile observability up -d
docker compose -f docker-compose.yml -f docker-compose.observability.yml --profile observability up --build --watch
```

From the repository root, prefer:

```bash
make obs-up
make obs-watch
make obs-ps
make obs-down
```

Real OCR Compose smoke:

```bash
make smoke-real-ocr
# PowerShell: .\scripts\smoke-real-ocr.ps1 -StartStack
# POSIX shell: sh scripts/smoke-real-ocr.sh --start-stack
```

The smoke uploads only the committed, clearly labelled synthetic fixture and requires a running containerized Tesseract binary with `eng` language data. It asserts persisted structured words, observed extraction fields, and a review-required extraction; it does not exercise a runtime mock.

## Kubernetes validation commands

```bash
kubectl kustomize infra/k8s/base | kubeconform -strict -summary
kubectl kustomize infra/k8s/overlays/dev | kubeconform -strict -summary
kubectl kustomize infra/k8s/overlays/prod | kubeconform -strict -summary
make k8s-validate
trivy config infra/k8s
```

On Windows, run the repository PowerShell validator directly:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\k8s-validate.ps1 -KubeconformImage ghcr.io/yannh/kubeconform:latest
```

`make k8s-validate` uses local `kubeconform` when installed. If it is not
installed but Docker is available, it runs `ghcr.io/yannh/kubeconform:latest`.
If neither is available, it still renders all Kustomize overlays.

## Security commands

```bash
trivy fs --severity HIGH,CRITICAL --exit-code 1 .
trivy config infra/k8s
```

Backend dependency check after Maven setup:

```bash
cd apps/api
./mvnw verify -P security-scan
cd ../worker
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
docker compose --env-file .env -f infra/docker/docker-compose.yml config
docker compose --env-file .env -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.observability.yml --profile observability config
kubectl kustomize infra/k8s/base | kubeconform -strict -summary
kubectl kustomize infra/k8s/overlays/dev | kubeconform -strict -summary
kubectl kustomize infra/k8s/overlays/prod | kubeconform -strict -summary
make k8s-validate
trivy config infra/k8s
```
