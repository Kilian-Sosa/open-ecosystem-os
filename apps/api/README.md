# apps/api

Spring Boot modular API for Open Ecosystem OS.

Responsibilities:

- auth and permissions
- workspace management
- Drive metadata and file operations
- Media/OCR job orchestration
- Open Pages APIs
- Open Ecosystem Flows definitions/executions
- notifications
- audit logs
- search API
- admin/system APIs

Initial structure suggestion:

```txt
src/main/java/.../openecosystem/
  identity/
  workspace/
  drive/
  media/
  pages/
  flows/
  notifications/
  audit/
  search/
  admin/
  common/
```

## Current foundation

The API is a Spring Boot 4.0.6 modular monolith under the base package
`com.openecosystem.os`.

```txt
src/main/java/com/openecosystem/os/
  admin/health/        # health and readiness endpoints
  audit/               # audit record skeleton
  common/errors/       # shared API error response handling
  common/events/       # event envelope contract
  common/security/     # correlation ID and placeholder auth context
  drive/
  flows/
  identity/
  media/
  notifications/
  search/
  workspace/
```

Available foundation endpoints:

- `GET /health`
- `GET /ready`
- Actuator endpoints remain under `/actuator`, with health, info, and metrics exposed.

Local checks:

```bash
mvn -q test
mvn -q verify
```
