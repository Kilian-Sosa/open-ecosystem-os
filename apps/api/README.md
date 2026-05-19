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
