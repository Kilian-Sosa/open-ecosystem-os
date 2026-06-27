---
name: security-review
description: Use when performing a security and privacy review for Open Ecosystem OS file, OCR, AI, auth, RBAC, audit, dependency, secret, upload, external call, or destructive-action changes.
---

# Security Review

Perform a security and privacy review for the requested change. Work as a
reviewer: return severity-ranked findings, concrete fixes, checks, and residual
risk. Do not inspect real `.env` values.

## Inputs

- Requested change, plan, diff, or implementation summary.
- Relevant changed files.
- `AGENTS.md`, `docs/development/SECURITY_AND_VULNERABILITY_CHECKS.md`,
  `docs/architecture/PERMISSIONS.md`, and `docs/architecture/EVENTS.md`.

## Review Checklist

- Secrets exposure.
- Unsafe `.env` handling.
- Raw file content, OCR text, AI prompt/response, or document content in logs,
  events, audit attributes, search documents, or notifications.
- Missing permission checks or over-trusting placeholder auth.
- Missing audit records for sensitive actions.
- Unsafe file upload/content-type handling.
- Dependency or container risk.
- Destructive actions without confirmation.

## Output

Return severity-ranked findings, concrete fixes, recommended checks, and any
accepted residual risk.
