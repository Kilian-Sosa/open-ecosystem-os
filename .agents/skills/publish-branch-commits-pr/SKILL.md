---
name: publish-branch-commits-pr
description: Use when the user asks to publish current Open Ecosystem OS workspace changes to GitHub, including branch creation, commits, push, or opening a pull request against develop.
---

# Publish Branch, Commits, And Pull Request

Use the global repo-agnostic `publish-branch-commits-pr` workflow first, then apply this repository context. This is a mutating workflow; use only when the user explicitly asks to stage, commit, push, or open/update a PR.

## Repo Context

Inspect only what is relevant:

- `git status --short --branch`, `git diff --stat`, relevant path-scoped diffs, recent commits, and remotes
- `docs/development/DEVELOPMENT_WORKFLOW.md`
- `docs/development/QUALITY_GATES.md`
- `docs/development/CI_CD.md`
- `docs/development/TEST_COMMANDS.md`
- `docs/development/SECURITY_AND_VULNERABILITY_CHECKS.md`
- relevant repo skills under `.agents/skills/`

## Repo Guardrails

- Default PR target is `develop`; ask before using another base.
- If on `main`, `develop`, detached HEAD, or an unrelated branch, create or switch to a correctly named work branch before committing.
- Use branch names like `feat/<scope>`, `fix/<scope>`, `refactor/<scope>`, `chore/<scope>`, or `test/<scope>`.
- Use Conventional Commits: `type(scope): imperative summary`.
- Stage only approved files with explicit paths; do not use `git add .` unless every included path was inspected.
- Never force-push, rebase, amend, reset, delete branches, or merge unless explicitly requested.
- Default to a draft PR unless the user asks for ready-for-review and relevant checks passed.

## Avoid Including

Keep generated output, local artifacts, CodeGraph indexes, IDE metadata, build outputs, logs, real documents, raw OCR text, AI prompts/responses, secrets, unrelated user work, and `.env` files out of commits.

## Output

Report branch, commit hashes/messages, pushed remote, PR URL, target base, checks run, excluded files, and assumptions or tradeoffs.
