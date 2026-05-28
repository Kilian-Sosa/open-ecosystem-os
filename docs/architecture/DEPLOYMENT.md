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

## Kubernetes strategy

Start with raw manifests or Kustomize overlays:

```txt
infra/k8s/base
infra/k8s/overlays/dev
infra/k8s/overlays/prod
```

Consider Helm only after manifests stabilize.

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
