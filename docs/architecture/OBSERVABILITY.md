# Observability

## Positioning

Grafana and the observability stack are not part of the core app. They are an external infrastructure layer.

Open Ecosystem OS should expose metrics, logs, traces, health checks, and audit events. Grafana and related tools should collect and visualize them.

## Recommended stack

MVP:

- structured logs
- health/readiness endpoints
- Prometheus metrics
- Grafana dashboards
- Loki logs

Later:

- OpenTelemetry Collector or Grafana Alloy
- Tempo traces
- Alertmanager alerts

## Core signals

### Metrics

- HTTP request count
- HTTP latency
- HTTP error rate
- worker job count
- queue depth
- workflow executions
- workflow failures
- OCR jobs
- OCR failures
- search indexing jobs
- storage usage
- backup success/failure
- AI request latency/usage, if enabled

### Logs

Use structured logs with:

- timestamp
- service
- level
- correlationId
- workspaceId when safe
- actorId when safe
- eventId when relevant

Do not log:

- file contents
- OCR text
- AI prompts/responses by default
- tokens
- secrets
- raw credentials

### Traces

Add later when services/workers become more distributed.

Trace important flows:

- upload -> OCR -> workflow -> notification
- page update -> indexing -> search
- workflow execution -> step workers

### Audit records

Audit logs are product/security data, not a replacement for technical logs.

Persist audit records in PostgreSQL.

## Health endpoints

API and workers should expose:

- `/health`
- `/ready`
- `/metrics`

## Local MVP profile

The Docker Compose observability stack is optional. Start the base app with:

```bash
make up
```

Start observability only when needed:

```bash
make obs-up
make obs-ps
```

Direct equivalent:

```bash
docker compose --env-file .env -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.observability.yml --profile observability up -d --build
```

Local observability endpoints:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3001`
- Loki: `http://localhost:3100`
- API metrics: `http://localhost:8080/metrics`
- Worker metrics: `worker:8081/metrics` inside the Compose network

Prometheus, Grafana, Loki, and the OpenTelemetry Collector are attached to both the public and internal Compose networks. Their browser/debug ports are published on `LOCAL_BIND_ADDRESS` from `.env`, which defaults to `127.0.0.1`.

Provisioned dashboards:

- `Open Ecosystem / Platform Overview`
- `Open Ecosystem / Automation Pipeline`

Current limitations:

- Loki is available as a Grafana datasource, but Docker log shipping is deferred until Alloy/Promtail or OTLP log export is added.
- The OpenTelemetry Collector is a placeholder receiver/exporter; no tracing is emitted yet.
- RabbitMQ queue depth, DLQ, MinIO storage, and container host metrics require exporters before dashboards and alerts can display those signals.
- `/metrics` is an operational endpoint and must be protected by network policy or equivalent controls in production-like deployments.

## Dashboards to create

### Platform Overview

- uptime
- API latency
- error rate
- service health
- queue health
- storage usage
- failed jobs

### Workflow Automation

- executions per minute
- success/failure rate
- retries
- DLQ size
- slowest workflows
- failed node types

### Media/OCR

- OCR jobs over time
- queue delay
- processing duration
- failure reasons
- worker saturation

### Search and AI

- search latency
- indexing volume
- zero-result searches
- AI requests
- AI failure rate

### Security

- failed logins
- permission denials
- API key changes
- suspicious sessions
- admin actions

## Alert candidates

- API down
- worker down
- queue depth above threshold
- DLQ growing
- backup failed
- disk/object storage close to full
- high error rate
- repeated failed logins
- OCR queue stuck

Avoid alerting on isolated low-impact failures.
