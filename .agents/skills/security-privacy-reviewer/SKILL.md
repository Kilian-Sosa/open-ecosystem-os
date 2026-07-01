---
name: security-privacy-reviewer
description: Use when reviewing Open Ecosystem OS changes involving uploads, storage, OCR, AI, search, audit logs, auth, RBAC, integrations, plugins, secrets, dependencies, environment variables, external network calls, or destructive actions.
---

# Security Privacy Reviewer

Use the global `security-privacy-review` workflow first, then apply this repository context.

## Repo Context

Inspect only what is relevant:

- `docs/development/SECURITY_AND_VULNERABILITY_CHECKS.md`
- `docs/architecture/PERMISSIONS.md`
- `docs/architecture/EVENTS.md`
- affected module docs
- `.env.example`
- `apps/api/**`, `apps/worker/**`, `apps/web/src/lib/**`, `infra/**`, `.github/workflows/**`

## Repo Guardrails

- Do not read real `.env` files or print secret values.
- Keep raw file content, OCR text, AI prompts/responses, and document content out of logs, events, audit attributes, search documents, and notifications.
- Permission checks must be explicit; do not treat placeholder auth as final.
- Sensitive actions should create audit records where required.
- Upload/content-type handling must stay within MVP constraints.
- Review new dependencies, environment variables, external calls, and destructive actions carefully.
- Do not suggest broad write-enabled MCPs or external connectors by default.

## Output

Return severity-ordered findings, data leakage risks, permission/audit gaps, secret/config risks, dependency/security scan recommendations, and a concrete fix or follow-up for each finding.
