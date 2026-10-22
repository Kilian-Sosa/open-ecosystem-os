# Deployment Strategy

## Deployment principle

The product must be easy to run locally, credible to deploy on a personal server, and structured enough to evolve toward production-grade Kubernetes.

## Local development

Use Docker Compose first.

Local services:

- web
- api
- worker
- PostgreSQL
- Redis
- RabbitMQ
- MinIO
- Meilisearch
- optional observability stack

Commands:

```bash
cp .env.example .env
make up
make logs
make down
make obs-up
```

The optional observability profile is enabled through the Compose override and
profile flag:

```bash
docker compose --env-file .env -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.observability.yml --profile observability up -d --build
```

Base startup must not depend on Prometheus, Grafana, Loki, or the
OpenTelemetry Collector.

## Container strategy

Each deployable gets its own image:

```txt
open-ecosystem-web
open-ecosystem-api
open-ecosystem-worker
```

Each image should:

- be small enough for practical deployment
- have health checks
- run as non-root where possible
- receive config through environment variables
- not include secrets in the image

The worker image includes the Tesseract executable and English (`eng`) language data. It runs as a non-root user and defaults to `OCR_PROVIDER=tesseract`; mock OCR is test-only and is not a supported runtime setting. Compose configures the worker command, languages, single-document concurrency, page timeout/count, render DPI, input, rendered-pixel, and process-output bounds. Compose mounts a bounded writable `/tmp` while retaining a read-only worker root filesystem.

## Kubernetes strategy

Start with raw manifests or Kustomize overlays:

```txt
infra/k8s/base
infra/k8s/overlays/dev
infra/k8s/overlays/prod
```

Consider Helm only after manifests stabilize.

The worker Deployment keeps `readOnlyRootFilesystem`, drops Linux capabilities, runs as UID/GID 10001, and mounts only a bounded 128 MiB `emptyDir` at `/tmp`. The `JAVA_TOOL_OPTIONS` setting maps Java temporary-file creation to that volume. Its capacity covers the single-document 25 MiB input cap plus a worst-case rendered page bounded by `OCR_MAX_RENDERED_PIXELS`; the base ConfigMap carries the same non-secret Tesseract and application-limit settings as Compose. Credentials and encryption keys remain Kubernetes Secrets. Production retains the bounded temporary volume and has higher worker CPU/memory limits for PDF rendering while application-level document concurrency remains one.

## Suggested namespaces

```txt
open-ecosystem-os
open-ecosystem-data
open-ecosystem-observability
```

## Kubernetes resources

### Application namespace

- web Deployment + Service
- api Deployment + Service
- worker Deployment
- ConfigMaps
- Secrets
- ServiceAccount
- NetworkPolicies later
- Ingress or Gateway API route

### Data namespace

- PostgreSQL StatefulSet or external DB
- Redis Deployment/StatefulSet or external
- RabbitMQ StatefulSet or operator later
- MinIO StatefulSet or external S3
- Meilisearch Deployment/StatefulSet
- PVCs

### Observability namespace

- Grafana
- Prometheus
- Loki
- Tempo later
- Alertmanager
- OpenTelemetry Collector or Grafana Alloy

The local Compose profile includes Prometheus, Grafana, Loki, and an
OpenTelemetry Collector placeholder. Production-like Kubernetes observability
should add authentication, retention policy, storage sizing, log shipping, and
exporters for RabbitMQ, object storage, and host/container metrics.

## Ingress/Gateway

For MVP, Ingress is acceptable.

Later, evaluate Kubernetes Gateway API, especially because the Kubernetes documentation notes that Ingress is stable but frozen and newer features are being developed through Gateway API.

## Secrets

Use Kubernetes Secrets initially, but remember that default Kubernetes Secrets are not a complete secret-management solution unless encryption at rest and access controls are configured.

Later options:

- External Secrets Operator
- SOPS
- Sealed Secrets
- HashiCorp Vault
- cloud provider secret manager

## Backup strategy

Back up:

- PostgreSQL
- MinIO bucket
- Meilisearch indexes or reindex source data
- configuration
- workflow definitions
- themes
- plugin manifests

Initial restore test should be scripted.

## Deployment milestones

### Stage 1 — Local Compose

- all services run locally
- demo data can be seeded/reset
- observability optional profile works

### Stage 2 — Single server Docker

- same Compose stack can run on personal server
- volumes configured
- reverse proxy/TLS configured separately

### Stage 3 — Kubernetes dev

- raw manifests deploy web/api/worker
- data services run in-cluster for dev
- ingress configured

### Stage 4 — Kubernetes production-like

- resource limits
- probes
- NetworkPolicies
- external secrets
- backup jobs
- observability stack
- persistent volumes

## Drive lifecycle rollout and remote cleanup proof

Drive Trash now requires API and worker lifecycle fences together. Stop the old
API and workers, let outstanding legacy Meilisearch writes settle, migrate the
shared database through V12, then start the matching API/worker versions. An old
worker ignores tombstones and has untracked remote writes; it is not a safe
rolling upgrade or ordinary rollback after Trash is used. The API remains the
final read-authorization boundary. No new service or infrastructure is required.

Meilisearch must support asynchronous tasks and `customMetadata` on document
mutations and task responses. The local contract tests passed against 1.49.0.
Keep task history for unresolved operations, and disable automatic proxy retries
of mutation POSTs. Drive-derived writes must use the tracked client exclusively.
The worker's `openecosystem.search.lifecycle.enabled` defaults to true;
`openecosystem.search.lifecycle.poll-ms` defaults to 2000, with 25 source files
per poll. Known failures persist a ten-second retry time; normal progress is
polled after two seconds. Invalid lifecycle envelopes use the existing Search
DLQ; valid deliveries can be acknowledged once reconciliation is durably tracked.

Inspect metadata-only state, never projection values or source document content:

```sql
select workspace_id, file_id, revision, desired_state, reconciled_revision,
       next_attempt_at, last_error_code
from drive_search_lifecycle
where reconciled_revision <> revision;

select workspace_id, file_id, document_id, desired_revision, desired_present,
       completed_revision, operation_id, operation_state, task_uid, lookup_cursor
from drive_search_remote_documents
where operation_id is not null or completed_revision <> desired_revision;
```

`SUBMITTING`/`UNCERTAIN` without a task UID means the request might have been
accepted. The poller searches paginated remote task history for that exact
operation ID. Once found, it waits for a terminal outcome before applying the
current desired revision. A task that never appears stays unresolved, including
a crash between persisting submission intent and actually sending the request.
Do not clear it because a lease or timeout elapsed. Operator resolution requires
quiescing writers and establishing the fate of that specific request; otherwise
cleanup must remain pending. Gateway 5xx/timeouts are ambiguous, not evidence of
rejection. No error message or diagnostic includes source content.

`DriveSearchLifecycle.cleanupComplete(workspaceId, fileId, revision)` is true
only for that exact current `ABSENT` revision after reconciliation, with no
outstanding write/removal. The later permanent-purge service must use this proof
before declaring remote cleanup complete; this change adds no physical-purge
endpoint and makes no S3-erasure claim. Retain the lifecycle/operation identifiers
needed for reconciliation independently of deletable source/projection rows.

Restoration resumes eligible pipeline work transactionally. Original completed
OCR envelopes must remain in the outbox so their event identity can be replayed
without duplicate extraction. Missing jobs are recreated, pending OCR receives a
replacement outbox request, and completed OCR resumes skipped triggering. Failed
OCR is not implicitly retried. Existing invoice projections remain excluded
until the separate confirmation feature supplies persisted user confirmation;
restoration never confirms a draft.
