# apps/worker

Async workers for Open Ecosystem OS.

Responsibilities:

- OCR jobs
- workflow execution
- search indexing
- notification dispatch
- backup jobs
- AI/mock extraction jobs

This will start as a Spring Boot worker app sharing common contracts with the API. It can later split into specialized workers if needed.

Local checks:

```bash
./mvnw test
./mvnw spotless:check
./mvnw spotless:apply
./mvnw verify
./mvnw verify -P security-scan
```

`verify` runs Spotless with Google Java Format. The `security-scan` profile runs
OWASP Dependency-Check and blocks high/critical dependency findings. It reads
`NVD_API_KEY` from the repository root `.env` file when present.

Available operational endpoints:

- `GET /health`
- `GET /ready`
- `GET /metrics`

Logs include `service` and `correlationId` in the console pattern. HTTP requests
echo `X-Correlation-Id`; RabbitMQ consumers copy event correlation IDs into MDC
while processing. Worker metrics currently cover OCR and search-indexing
outcomes and durations.
