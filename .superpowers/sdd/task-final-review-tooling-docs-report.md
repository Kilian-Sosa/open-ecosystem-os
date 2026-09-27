# Task 5 tooling, product documentation, and evidence remediation

**Date:** 2026-07-15

## Scope and authorization status

This task remediates I-11, M-03, and M-04 only. CV-04 is closed by the integrated
source-file `file:view` implementation. The closure report is
`.superpowers/sdd/task-final-review-file-view-report.md`; it records the owner-only
private-file decision, non-enumerating denial, and focused decision, Media/OCR, and Drive test
coverage. This task does not redesign or reimplement authorization.

`docs/architecture/PERMISSIONS.md` now describes that implemented decision accurately: matching
workspace and Drive-file owner are required for the current private-file model. Workspace
membership and roles do not independently grant file access, and persisted workspace-visible or
specific-user/group sharing remains future work.

## I-11: retired runtime demo tooling

- Deleted `scripts/seed-demo-data.ps1`, `scripts/seed-demo-data.sh`,
  `scripts/reset-demo-data.ps1`, and `scripts/reset-demo-data.sh`.
- Removed the corresponding Makefile variables, `seed`/`reset` targets, and `.PHONY` entries.
- Retained `make smoke-real-ocr` as the only supported runtime smoke target. It uses the committed,
  clearly labelled synthetic fixture through the real upload/OCR path; no fake runtime lifecycle
  replacement or compatibility alias was introduced.

## M-03: current product lifecycle

The documented implemented lifecycle is OCR, structured extraction, `review_required` where
needed, the Knowledge placeholder and approved-field search, plus generic notification and audit
metadata. Kanban-task and approval-request creation are explicitly future/P2 work.

## M-04: evidence status

Historical reports remain unchanged. They are historical evidence and are superseded for this
remediation by this report; no historical command is claimed as newly run.

| Check                                              | Exact command                                                                                                                                                                          | Result                                                                                                                                                                                  |
| -------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Active obsolete demo references                    | PowerShell `Select-String` search over `Makefile`, `scripts`, and current architecture/development/product docs for `/api/demo/invoice-automation/runs`, `seed-demo`, and `reset-demo` | Passed, exit code 0; no active references. Historical planning material was excluded.                                                                                                   |
| Product/permission consistency                     | PowerShell assertions over `USER_JOURNEYS.md`, `MVP_SCOPE.md`, and `PERMISSIONS.md`                                                                                                    | Passed, exit code 0; lifecycle/P2 and implemented `file:view` wording present.                                                                                                          |
| CV-04 closure evidence                             | PowerShell existence check for the closure report, `ResourcePermissionDecision`, and focused API test sources                                                                          | Passed, exit code 0.                                                                                                                                                                    |
| Kubernetes validator                               | `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\\k8s-validate.ps1 -KubeconformImage ghcr.io/yannh/kubeconform:latest`                                                     | Wrapper exit code 0, but unavailable as validation evidence: Docker’s Linux engine pipe was unavailable and each Dockerized kubeconform invocation failed. Do not treat this as passed. |
| POSIX smoke parsing/runtime                        | Not run on this Windows host                                                                                                                                                           | Unavailable; no POSIX pass is claimed.                                                                                                                                                  |
| Filesystem, IaC, and authenticated container scans | Not run on this host                                                                                                                                                                   | Unavailable; local `trivy`/authenticated container scanning evidence is not claimed.                                                                                                    |
| Branch-range whitespace check                      | `git diff --check 993b3479cb2401b8a9d831695a4244b8fa947bfe...HEAD`                                                                                                                     | Passed, exit code 0, after correcting the two new-blank-line defects.                                                                                                                   |

The former trailing spaces in the original real-OCR design’s status/date lines were removed so
the branch-range check can succeed without an intentional Markdown-whitespace exception.
