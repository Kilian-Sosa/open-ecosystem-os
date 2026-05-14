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
