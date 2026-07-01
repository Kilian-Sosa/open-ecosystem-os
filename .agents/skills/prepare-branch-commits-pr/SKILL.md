---
name: prepare-branch-commits-pr
description: Use when preparing an Open Ecosystem OS branch name, commit plan, commit messages, files per commit, pull request title, pull request description, validation plan, or PR safety notes from the current workspace state.
---

# Prepare Branch, Commits, And Pull Request

Use the global repo-agnostic `prepare-branch-commits-pr` workflow first, then apply this repository context. Work as a planner only.

## Repo Context

Inspect only what is relevant:

- `git status --short --branch`, `git diff --stat`, relevant path-scoped diffs, and `git log --oneline -20`
- `AGENTS.md`
- `docs/development/DEVELOPMENT_WORKFLOW.md`
- `docs/development/QUALITY_GATES.md`
- `docs/development/CI_CD.md`
- `docs/development/TEST_COMMANDS.md`
- `docs/development/SECURITY_AND_VULNERABILITY_CHECKS.md`
- product, architecture, event, and permissions docs when relevant
- relevant repo skills under `.agents/skills/`

## Repo Guardrails

- Do not stage, commit, push, rebase, merge, delete, move, or edit files.
- Use branch names like `feat/<scope>`, `fix/<scope>`, `refactor/<scope>`, `chore/<scope>`, or `test/<scope>`.
- Use commit messages like `type(scope): imperative summary`.
- If base branch is unclear, ask or list it as an open question.
- Keep unrelated user work, generated output, local artifacts, secrets, `.codegraph/`, and ignored runtime files out of the plan.
- Do not plan P2/P3 screen implementation unless explicitly requested.

## Artifact

Create or update `artifacts/COMMIT_PLAN.md` unless the user provides another path. Treat it as planner output and do not include it in proposed commits unless explicitly asked.

## Output

Report the artifact path, affected deployables, proposed branch name, commit count, excluded files, validation plan, and open questions.
