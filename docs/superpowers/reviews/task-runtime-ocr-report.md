# Task 7 runtime OCR report

## Scope

Implemented only Task 7 in detached worktree
`C:\Users\kilia\AppData\Local\Temp\open-ecosystem-os-task7-runtime-ocr` from source commit
`17ba9622a365b1ed23518f5536ece5147a658fc7`.

- Worker image installs `tesseract-ocr` and `tesseract-ocr-data-eng`, then runs as UID/GID 10001.
- Compose, `.env.example`, and Kubernetes default to `OCR_PROVIDER=tesseract` and pass every
  existing `WorkerOcrProperties` bound: command, languages, concurrency, timeout, page count,
  DPI, input, rendered-pixel, process-output, and stale-processing limits.
- The worker's read-only root filesystem has only a bounded `/tmp`: 128 MiB in Compose and the
  Kubernetes `emptyDir`. `JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=/tmp` maps the worker's real
  `DriveOcrSourceReader` temporary directory to that volume. The capacity covers the single
  25 MiB input limit plus one rendered page capped by `OCR_MAX_RENDERED_PIXELS`.
- Added a clearly marked synthetic test-only scanned image and matching PowerShell/POSIX smoke
  scripts. They upload through Drive and require actual Tesseract/`eng`, structured OCR words,
  observed persisted fields, and a review-required extraction.
- Updated the Task 7 event, permission, data, deployment, journey, route, and test command
  documentation. No migration, API contract, domain extraction heuristic, or UI behavior changed.

## Smoke evidence

The initial pre-runtime `-StartStack` run was attempted against the old Compose defaults before
runtime changes. Its full build output was truncated by the terminal and no service container was
created, so it is not represented as a successful assertion-level RED result. The old configuration
was independently confirmed to default `OCR_PROVIDER` to `mock` and the old worker image did not
install Tesseract. The resulting implementation replaces both conditions.

The first real smoke run exposed two test-harness/fixture defects, not runtime defects:

1. PowerShell scriptblock scope discarded the polled detail object. The script now writes the
   result to script scope before asserting it.
2. A prose line containing `invoice must` and an `Invoice Date` label were valid closer generic
   invoice-number candidates for the already-committed heuristic. The synthetic image now uses
   `Issue Date` and no ambiguous prose, while retaining an explicit incomplete-review state.

Fresh GREEN command:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-real-ocr.ps1 -TimeoutSeconds 240
```

Result: exit 0. The output reported Tesseract 5.5.1 and `Real Tesseract OCR smoke passed for the
clearly labelled synthetic test invoice.`

## Verification

| Check                                                              | Result                                                                     |
| ------------------------------------------------------------------ | -------------------------------------------------------------------------- |
| `docker compose -f infra/docker/docker-compose.yml config --quiet` | Passed                                                                     |
| `docker compose -f infra/docker/docker-compose.yml build worker`   | Passed; Alpine installed Tesseract 5.5.1 and `tesseract-ocr-data-eng`      |
| Isolated worker image `id; tesseract --list-langs`                 | Passed; UID/GID 10001 and `eng` available                                  |
| `make k8s-validate`                                                | Passed; kubeconform validated base (11), dev (24), and prod (11) resources |
| Real OCR smoke after Compose restart                               | Passed                                                                     |
| PowerShell parser check for `scripts/smoke-real-ocr.ps1`           | Passed                                                                     |
| `apps/worker/.\mvnw.cmd -q test`                                   | Passed                                                                     |
| `apps/worker/.\mvnw.cmd -q spotless:check`                         | Passed                                                                     |
| `apps/worker/.\mvnw.cmd -q verify -P security-scan`                | Passed                                                                     |
| Pinned Prettier check for changed YAML/Markdown                    | Passed                                                                     |
| `git diff --check`                                                 | Passed                                                                     |

## Security and scanner limitations

- The local `trivy` executable is unavailable, so filesystem and IaC Trivy scans were not run.
- Docker Scout is installed but refused the worker-image scan without Docker authentication. No
  login was attempted.
- WSL Bash and Git Bash both failed before parsing the POSIX smoke script because this environment
  denied their Windows process setup. The shell script is present and documented, but its local
  syntax check is recorded as unavailable rather than passed.

## Self-review

- No secret values were added; `.env.example` uses only variable names and synthetic local values.
- Tesseract diagnostics and OCR values remain inside the smoke process; no production log/event or
  API contract was changed.
- Kubernetes retains non-root execution, dropped capabilities, seccomp, and read-only root
  filesystem. The only writable mount is the bounded temporary volume.
- Historical migrations were not changed.

## Integration

Detached worktree commit: `afe46408375265f87832c50fe28ec50d224f4734`.

Source-branch cherry-pick: `28d5de382813faa48a5bf222d765d29bf557079d`.

Post-cherry-pick source verification:

```powershell
docker compose -f infra\docker\docker-compose.yml config --quiet
```

Result: exit 0 from `C:\Users\kilia\Documents\open-ecosystem-os`.
