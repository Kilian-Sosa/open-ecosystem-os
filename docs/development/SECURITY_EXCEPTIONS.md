# Temporary Security Exceptions

Exceptions are temporary risk acceptances. They must be narrowly scoped to one
advisory, have an owner and review date, and be removed when the tracked
remediation is complete.

## GHSA-mh99-v99m-4gvg / CVE-2026-14257

- Status: temporary accepted risk
- Owner: Kilian-Sosa
- Review date: 2026-08-29
- Follow-up: [#36](https://github.com/Kilian-Sosa/open-ecosystem-os/issues/36)
- Scope: `apps/web` development dependency audit only

### Affected resolution

`eslint@9.39.1` → `minimatch@3.1.5` → `brace-expansion@1.1.17`

This is lint tooling. The application source does not import `minimatch` or
`brace-expansion`, and no runtime feature accepts attacker-controlled glob or
brace patterns for this dependency.

### Rationale and mitigation

The advisory requires `brace-expansion@5.0.8`. Forcing that version through
the ESLint 9 dependency path breaks linting because the installed minimatch
version expects the older CommonJS function export. ESLint 10 is not currently
compatible with the Next lint-plugin family used by this project. The exception
is therefore limited to this single GHSA until [#36](https://github.com/Kilian-Sosa/open-ecosystem-os/issues/36) completes the compatible toolchain migration.

The project continues to block all other high and critical advisories. The
production-only audit is clean, and the production Docker/standalone deployment
does not install development dependencies.

### Verification evidence

- `pnpm audit --prod --json` reports no findings.
- `pnpm lint`, `pnpm typecheck`, `pnpm test`, and `pnpm build` pass.
- The production image-optimization smoke test passes with `sharp@0.35.3`.
- The full audit exception is scoped in `apps/web/package.json` to only
  `GHSA-mh99-v99m-4gvg`.
