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

Invoice seed migration isolation regression, from `apps/api`:

```powershell
.\mvnw.cmd -o '-Dtest=JdbcInvoiceExtractionRepositoryTest,InvoiceAutomationSeedMigrationTest' '-Dsurefire.runOrder=reversealphabetical' test
.\mvnw.cmd -o '-Dtest=JdbcInvoiceExtractionRepositoryTest,InvoiceAutomationSeedMigrationTest' '-Dsurefire.runOrder=alphabetical' test
```

The migration test uses a dedicated H2 database so repository/controller cleanup
cannot remove its migrated seed rows. Both orders must pass in a shared test JVM;
the reverse order reproduces the original failure without isolation. Keep the
seed assertions and Flyway migrations intact. Spec impact: **no spec change**;
this fixes test isolation without changing application behavior.

Protected Drive original downloads and safe previews (Task 3), from `apps/api`:

```powershell
.\mvnw.cmd '-Dtest=DriveFileContentServiceTest,DriveFileControllerTest,S3FileObjectStorageTest' test
```

See [the content and viewer contract](DRIVE_FILE_CONTENT.md) for scope, bounds,
synthetic fixtures and browser integration requirements. Run `mvnw.cmd verify`
for the full API suite, PostgreSQL migration tests, formatting and packaging.

Drive metadata rename/move (Task 4), from `apps/api`:

```powershell
.\mvnw.cmd '-Dtest=DriveFileMutationServiceTest,DriveFolderControllerTest,DriveFileRepositoryTest' test
```

See [the mutation contract](DRIVE_FILE_MUTATIONS.md) for request bodies,
authorization, concurrency, compatibility and deferred Search behavior.

Drive folder ZIP downloads (Task 5), from `apps/api`:

```powershell
.\mvnw.cmd -o '-Dtest=DriveArchiveServiceTest,DriveFolderControllerTest,DriveFolderRepositoryTest,DriveFileRepositoryTest' test
```

See [the archive contract](DRIVE_FOLDER_ARCHIVES.md) for authorization,
snapshot semantics, limits, temporary-storage requirements and cleanup.

### Drive organization integration acceptance (Task 7)

From `apps/api`:

```powershell
.\mvnw.cmd -o '-Dtest=DriveCoreJourneyTest' test
.\mvnw.cmd -o test
.\mvnw.cmd -o verify
```

`DriveCoreJourneyTest` uses a dedicated H2 database and real HTTP, authorization,
encryption, metadata mutations and ZIP generation, replacing only S3 with the
existing in-memory object store. It verifies nested navigation, rename, file
movement through root, subtree movement, exact original/ZIP bytes, stable file
IDs/storage keys/ciphertext and unchanged persisted OCR/invoice lineage. Its
clearly labelled synthetic lineage is seeded, not produced by a running OCR
worker. Same-workspace non-owners and foreign-workspace actors cannot read,
preview, download or mutate the subtree or infer labels. The full API suite
also covers sharing, legacy/corrupt upload validation, safe PDF preview,
archive bounds/cleanup, PostgreSQL migrations and locking.

Run the web gates from `apps/web`:

```powershell
pnpm test
pnpm lint
pnpm typecheck
pnpm build
```

If Corepack cannot download pnpm, the installed pinned entry points are:

```powershell
node node_modules/vitest/vitest.mjs run
node node_modules/eslint/bin/eslint.js .
node node_modules/typescript/bin/tsc --noEmit
node node_modules/next/dist/bin/next build
```

For real PostgreSQL/MinIO acceptance, use a **disposable private** Compose
project from the repository root. Use a fresh PowerShell terminal so these
process environment overrides do not affect other work. The explicit example
environment file avoids loading a real `.env`. Confirm the project name is
unused and the alternate ports are free before starting:

```powershell
docker compose ls
Get-NetTCPConnection -State Listen | Where-Object { $_.LocalPort -in 18087,19087,19088,15687 }
$env:API_PORT = '18087'
$env:MINIO_API_PORT = '19087'
$env:MINIO_CONSOLE_PORT = '19088'
$env:RABBITMQ_MANAGEMENT_PORT = '15687'
$env:LOCAL_BIND_ADDRESS = '127.0.0.1'
docker compose --env-file .env.example -p drive-task7 -f infra/docker/docker-compose.yml up -d --build --wait --wait-timeout 180 api
.\scripts\smoke-drive-organization.ps1 -ApiBaseUrl http://127.0.0.1:18087
```

The smoke creates a unique synthetic tree, uploads text and the committed
synthetic PNG, renames/moves files and a subtree, verifies nested/root listings,
compares previews/originals byte for byte and inspects all five ZIP entries,
including the empty directory. It leaves synthetic files for inspection. It
starts no OCR worker and makes no Search-indexing claim. To inspect through the
real UI, run `pnpm dev` from `apps/web` with process environment variables
`NEXT_PUBLIC_API_BASE_URL` and `API_BASE_URL` set to
`http://127.0.0.1:18087`, then open `http://127.0.0.1:3000/app/drive`.

After verification, stop that temporary web server and remove **only this
disposable project's** containers and volumes:

```powershell
docker compose --env-file .env.example -p drive-task7 -f infra/docker/docker-compose.yml down --volumes
```

Verified 2026-10-21: API `test` and `verify` each passed 216 tests with zero
failures/errors/skips; web passed 81 tests, lint, typecheck and production build.
The fresh Compose API/MinIO smoke passed all five ZIP entries and text/image
byte comparisons. Edge displayed the real persisted nested hierarchy and
protected image/text previews. Task 6 retains desktop/mobile and PDF canvas
browser coverage using synthetic HTTP responses; this is not an automated
Compose browser E2E suite. The Task 7 lineage proof is the HTTP integration
test with seeded records, not a fresh OCR-to-invoice execution.

Spec impact: **no spec change**; this verifies the approved Drive organization
contract. **Trash and Search indexing remain separate subsequent work.**

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
