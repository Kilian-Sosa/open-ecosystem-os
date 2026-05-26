# ADR 0006 — Use an external observability stack

## Status

Proposed

## Context

Open Ecosystem OS is expected to include a web app, backend API, workers, queues, object storage, search indexing, OCR processing, workflow automation, notifications, and audit logs.

The platform needs observability for:

- API latency and error rates
- worker health
- queue depth
- workflow execution success/failure
- OCR processing duration and failures
- object storage usage
- search indexing failures
- background job retries and dead-letter queues
- security-relevant events
- deployment and infrastructure health

The application should expose health, readiness, metrics, logs, traces, and audit information. However, the application should not become its own Grafana clone.

There is a distinction between:

- **Product-facing status**, such as System Status, Admin Dashboard, Audit Logs, and Advanced Admin Analytics.
- **Infrastructure-facing observability**, such as metrics, logs, traces, alert routing, and incident investigation.

The product should summarize operational state for users/admins. A dedicated observability stack should handle deep technical inspection.

## Decision

Use an **external observability stack** outside the core application runtime.

Recommended stack:

- **Prometheus** for metrics.
- **Grafana** for dashboards.
- **Loki** for logs.
- **OpenTelemetry Collector or Grafana Alloy** for telemetry collection and routing.
- **Tempo** later for distributed tracing when service/worker interactions justify it.
- **Alertmanager** for alert grouping, deduplication, silencing, inhibition, and routing.

Open Ecosystem OS should expose telemetry through standard interfaces:

- `/health`
- `/readiness`
- `/liveness`
- `/metrics`
- structured logs with correlation IDs
- OpenTelemetry instrumentation where useful

The observability stack should be optional for local development, but recommended for self-hosted and portfolio deployments.

## Consequences

### Positive

- Keeps the application focused on product functionality instead of reimplementing observability tooling.
- Demonstrates production-oriented engineering practices.
- Supports debugging of event-driven and worker-based flows.
- Makes the System Status, Admin Dashboard, and Advanced Admin Analytics screens more credible.
- Enables dashboards for API health, worker throughput, queue depth, OCR pipeline performance, workflow failures, and storage usage.
- Allows alerting rules to evolve independently from product code.

### Negative

- Adds infrastructure complexity.
- Requires additional Compose/Kubernetes manifests.
- Requires careful handling of sensitive logs and telemetry.
- Requires dashboard and alert maintenance.
- Requires a clear boundary between product analytics and infrastructure observability.

## Alternatives considered

### Build observability directly into Open Ecosystem OS

Rejected.

The app should expose summarized health and admin information, but detailed metrics/logs/traces should be handled by mature observability tools.

### Use only application logs

Rejected.

Logs alone are not enough for queue depth, latency, throughput, error-rate trends, worker health, and alerting.

### Use a hosted observability platform only

Rejected for the default path.

The project is self-hosted and open-source-first. Hosted observability can be supported later as an optional integration, but the default architecture should work locally and on a private server.

### Add full tracing from day one

Deferred.

Metrics and logs provide faster value for the MVP. Distributed tracing should be added when there are enough asynchronous service/worker interactions to justify the complexity.

## Implementation notes

Local development should support a base stack and an optional observability override:

```bash
docker compose -f infra/docker/docker-compose.yml up -d
docker compose -f infra/docker/docker-compose.yml -f infra/docker/docker-compose.observability.yml up -d
```

Kubernetes should place observability components in a dedicated namespace later:

```txt
namespace: observability

components:
- prometheus
- grafana
- loki
- otel-collector or grafana-alloy
- tempo later
- alertmanager
```

Initial application metrics should include:

```txt
http_requests_total
http_request_duration_seconds
http_errors_total

workflow_executions_total
workflow_execution_failed_total
workflow_execution_duration_seconds
workflow_dead_letter_events_total

ocr_jobs_total
ocr_jobs_failed_total
ocr_job_duration_seconds
ocr_queue_depth

storage_used_bytes
backup_jobs_total
backup_last_success_timestamp

auth_failed_logins_total
permission_denied_total
security_alerts_total
```

Logs must avoid sensitive content:

- no raw file contents
- no OCR extracted text by default
- no AI prompts/responses by default
- no secrets
- no credentials
- no raw tokens
- no private document content

Every request, job, and emitted event should carry a correlation ID where practical.

## Relationship with product screens

Open Ecosystem OS screens:

- System Status: summarized service health and operational state.
- Admin Dashboard: product/admin overview.
- Advanced Admin Analytics: product usage and high-level operational trends.
- Activity/Audit Logs: application-level audit records.

External observability stack:

- Grafana dashboards
- Prometheus metrics
- Loki logs
- Tempo traces
- Alertmanager alert routing

The application should link to Grafana dashboards only when the observability stack is enabled and properly protected.

## References

- OpenTelemetry Collector documentation: https://opentelemetry.io/docs/collector/
- Grafana Alloy documentation: https://grafana.com/docs/opentelemetry/collector/grafana-alloy/
- Prometheus Alertmanager documentation: https://prometheus.io/docs/alerting/latest/alertmanager/
