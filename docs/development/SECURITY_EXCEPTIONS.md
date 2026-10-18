# Temporary Security Exceptions

Exceptions are temporary risk acceptances. They must be narrowly scoped to one
advisory, have an owner and review date, and be removed when the tracked
remediation is complete.

## braces 3.0.3 — CVE-2026-93687

- Advisory: [GHSA-vfj7-8cjw-p6xm](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm).
- Severity: high; deeply nested brace patterns can exhaust the Node.js stack.
- Affected dependency path: `eslint-config-next@16.4.2` →
  `@next/eslint-plugin-next@16.4.2` → `fast-glob@3.3.1` →
  `micromatch@4.0.8` → `braces@3.0.3`.
- Scope: frontend development/lint tooling. The production dependency audit
  does not include this dependency. Lint patterns and source paths come from
  the repository; hostile patterns in untrusted contributions can still
  terminate the lint process. This exception accepts that availability risk.
- Reason: no patched braces release or compatible upstream dependency chain
  is available as of 2026-10-18. The repository owner explicitly authorized
  this temporary exception so this advisory does not block pull requests.
- Mitigation: retain all lint rules and security checks; suppress only this
  advisory through `pnpm.auditConfig.ignoreCves` in `apps/web/package.json`.
  Do not process user-supplied glob patterns with this development toolchain.
  All other advisories, including the original CVE-2026-14257, remain checked.
- Follow-up: [issue #36](https://github.com/Kilian-Sosa/open-ecosystem-os/issues/36)
  tracks removal of this exception when a compatible upstream fix is available.
  Keep the issue open while this exception remains active.
- Owner: Kilian-Sosa.
- Accepted: 2026-10-18.
- Review date: 2026-11-17.
- Removal: upgrade the affected dependency chain, remove the single CVE entry,
  and verify both production and full audits pass without it. The review date
  requires owner reassessment; pnpm does not expire this setting automatically.
