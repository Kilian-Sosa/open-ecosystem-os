---
name: prepare-branch-commits-pr
description: Use when preparing an Open Ecosystem OS branch name, commit plan, commit messages, files per commit, pull request title, pull request description, validation plan, or PR safety notes from the current workspace state.
---

# Prepare Branch, Commits, And Pull Request

Prepare a GitHub-ready branch, commit, and pull request plan for the current
Open Ecosystem OS repository state. Work as a planner only.

## Inputs

Use the user's request as the main argument. Optional details may include a
target base branch, changed-file focus, desired PR title, or commit grouping
preference. If an input is omitted, infer only when safe and list unresolved
items under Open Questions.

## Safety Rules

- Do not stage, commit, push, rebase, merge, delete, move, or edit files.
- Do not inspect or print real secret values from `.env` files, local config,
  database dumps, logs, key material, ignored runtime files, or credentials.
- Do not include generated outputs, local artifacts, CodeGraph indexes, IDE
  metadata, build outputs, lockfiles, or unrelated files in the commit plan
  unless there is a clear repository reason.
- Keep existing user work separate unless it is directly part of the requested
  change.

## Inspect First

- `git status --short --branch` from the repository root.
- `git diff --stat`.
- Relevant `git diff -- <file>` output, avoiding secret values and generated
  output.
- `git log --oneline -20` for branch and commit-message style.
- `AGENTS.md`, `docs/development/DEVELOPMENT_WORKFLOW.md`,
  `docs/development/QUALITY_GATES.md`, `docs/development/CI_CD.md`,
  `docs/development/TEST_COMMANDS.md`, and
  `docs/development/SECURITY_AND_VULNERABILITY_CHECKS.md`.
- `docs/product/MVP_SCOPE.md`, `docs/architecture/EVENTS.md`, and
  `docs/architecture/PERMISSIONS.md` when scope, events, security, or
  permissions are relevant.
- Relevant repo skills under `.agents/skills/` when focused planning or review
  guidance is needed.
- `codegraph status` when structural impact is relevant. If CodeGraph is not
  initialized, note that and do not create or commit `.codegraph/`.

## Repository Facts

- This is a single GitHub-hosted monorepo.
- Main deployables are `apps/web`, `apps/api`, `apps/worker`, Docker Compose,
  Kubernetes manifests, documentation, and GitHub Actions workflows.
- Active workflows live under `.github/workflows/`; deferred templates live
  under `docs/templates/github-workflows/`.
- `main` is stable integration and `develop` is active while the repo uses it.
  If the correct base branch is unclear, ask or list it as an open question.
- Branch names should use `feat/<scope>`, `fix/<scope>`,
  `refactor/<scope>`, `chore/<scope>`, or `test/<scope>`.
- Commit messages should use `type(scope): imperative summary`.
- Prioritize the first MVP vertical slice:
  Drive upload -> OCR worker -> event -> workflow -> notification -> audit log.
- Do not plan P2/P3 screen implementation unless explicitly requested.

## Avoid Including

- `node_modules/`, `.pnpm-store/`, `.next/`, `out/`, `target/`, `coverage/`,
  `playwright-report/`, `test-results/`, `*.tsbuildinfo`, `.codegraph/`,
  `.idea/`, `.vscode/`, local Docker volumes, local logs, and `artifacts/`.
- `.env`, `.env.*` except `.env.example`, real credentials, API keys,
  database passwords, AI provider keys, SMTP credentials, MinIO secrets,
  Kubernetes kubeconfig, private keys, real personal documents, raw OCR text,
  AI prompts/responses, and production tokens.
- Generated changelogs, reports, or lockfiles unless a dependency or workflow
  change requires them.

## Artifact

Create or update one local Markdown artifact at `artifacts/COMMIT_PLAN.md`
unless the user provides another path. Treat it as planner output: do not
include it in proposed commits unless explicitly asked.

## Required Artifact Sections

- `## Scope`: repository, current branch, proposed base branch, affected
  deployables, affected bounded contexts, local-change summary, excluded files.
- `## Branch`: proposed branch name, alternative if useful, rationale.
- `## Commit Plan`: for each commit, message, files to include, files excluded,
  rationale, validation expected.
- `## Pull Request`: title, target branch, summary, testing, risk/rollback,
  compatibility, migration, security/privacy, events/permissions,
  infrastructure/CI, follow-ups.
- `## Validation Plan`: narrow checks, broader checks, CI coverage, deferrals.
- `## Open Questions`: only questions that materially affect branch naming,
  commit grouping, PR wording, base branch choice, or safety.

## Final Response

Report the path written, affected deployables, proposed branch name, number of
commits, excluded files, and open questions.
