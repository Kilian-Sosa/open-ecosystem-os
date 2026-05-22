# Local Development Checklist

Use this checklist before opening a pull request.

## General

- [ ] I read the relevant docs before editing.
- [ ] The change is scoped to one coherent task.
- [ ] No secrets or real personal data were committed.
- [ ] `.env.example` is updated if new env vars were added.
- [ ] Docs were updated if behavior, architecture, commands, events, or permissions changed.

## Frontend

- [ ] UI follows `DESIGN.md`.
- [ ] Desktop and mobile behavior are considered.
- [ ] Loading/empty/error states exist where relevant.
- [ ] Icon-only buttons have labels.
- [ ] Component tests or E2E tests were added where relevant.
- [ ] Format/lint/typecheck/tests pass.

## Backend

- [ ] Domain logic has unit tests.
- [ ] API endpoints validate input.
- [ ] Security/permission checks are tested.
- [ ] New events use the standard envelope.
- [ ] New database changes are documented.
- [ ] Unit/slice/integration tests pass as applicable.

## Workers/events

- [ ] Consumer is idempotent.
- [ ] Retry behavior is defined.
- [ ] DLQ behavior is defined if relevant.
- [ ] Event schemas/examples are updated.
- [ ] Logs include correlation IDs.

## Infrastructure

- [ ] Docker Compose still validates.
- [ ] Kubernetes manifests still validate.
- [ ] Health/readiness endpoints are considered.
- [ ] Resource/secrets/config assumptions are documented.

## Security

- [ ] No high/critical vulnerability introduced knowingly.
- [ ] New dependencies have a reason.
- [ ] File upload or AI changes consider privacy/security.
- [ ] PDF redaction/metadata behavior is safe if touched.

## PR summary template

```md
## What changed

-

## Why

-

## Tests/checks run

-

## Screenshots/videos

-

## Architecture impact

- Events:
- Permissions:
- Data/storage:
- Infrastructure:

## Risks/follow-ups

-
```
