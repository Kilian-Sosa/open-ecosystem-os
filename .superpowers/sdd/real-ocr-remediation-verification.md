# Real OCR remediation Task 6 verification

Date: 2026-07-29

## Scope and commits

- Source branch: `feat/real-ocr-extraction`.
- Detached correction commit: `d0b8bd76950055d405f6773ea17819f1178678ef`.
- Source cherry-pick: `7e1303641a4eed21359d0d82aae37f72b309018d`.
- Change: `apps/worker/pdf-helper/pom.xml` explicitly resolves `jackson-databind` and matching `jackson-core` at 2.21.4. The initial databind-only resolution retained managed `jackson-core:2.21.2`; the effective tree proved the companion core declaration necessary. No BOM or unrelated worker dependency changed.

## Jackson correction evidence

- RED: before editing, the helper resolved `jackson-databind:2.21.2` and `jackson-core:2.21.2`.
- GREEN: source helper tests and package passed; the effective tree resolves `jackson-databind:2.21.4` and `jackson-core:2.21.4`.
- The packaged helper JAR contains `jackson-databind-2.21.4.jar`, `jackson-core-2.21.4.jar`, and compatible release-line `jackson-annotations-2.21.jar`.

## Trivy evidence

- Scanner: `aquasec/trivy:0.72.0`.
- Cache volume: `open-ecosystem-os-trivy-cache`; vulnerability DB updated 2026-07-29 00:49:41 UTC and Java DB updated 2026-07-29 01:21:07 UTC. Image scanning used `--skip-db-update --skip-java-db-update`.
- Detached worker image: `sha256:6afc59cf4c3251b0348252cdd84da9834f947b4cf182a0d901664ac25f911721`.
- Source-built worker image: `sha256:4674818bf88cdcb1430c46f135c8db76af881ffbe18bf8f014b7e9e08d63cad1`.
- Source image JSON: `.superpowers/sdd/real-ocr-remediation-trivy-image.json`.
- Result: the three branch-introduced Jackson 2.x HIGH findings are absent. The 13 remaining image HIGH findings exactly match the accepted baseline: three libexpat, p11-kit, p11-kit-trust, four Netty, PostgreSQL JDBC, and three Jackson 3.x findings. No new image HIGH finding appeared.
- Kubernetes JSON: `.superpowers/sdd/real-ocr-remediation-trivy-iac.json`; it contains exactly 15 HIGH findings, all in `infra/k8s/overlays/dev/data-stack.yaml`, matching the accepted IaC baseline.
- The supplied feature and baseline filesystem JSON files were absent. A current full filesystem scan was started with the cache and Java DB updates disabled, but Trivy continued traversing the full mounted source tree without emitting a report; the four scanner containers were stopped and no filesystem evidence is claimed.

## Passed checks

- `apps/worker/pdf-helper`: focused test and package in detached and source checkout.
- Full worker tests and `spotless:check` in detached checkout.
- Existing focused lineage, duplicate-delivery, claim, and stale-claim tests; these run against H2.
- Full API tests and `spotless:check` in detached checkout.
- Web source checks: format, lint, typecheck, 50/50 Vitest tests, and production build.
- Docker Compose configuration validation.
- Kubernetes base, dev, and prod validation through `scripts/k8s-validate.ps1`.
- POSIX smoke syntax: `sh -n scripts/smoke-real-ocr.sh`.
- Real PowerShell OCR smoke against the source-built Compose stack; it printed its success assertion for the synthetic invoice and real Tesseract provider.
- Exact range whitespace check: `git diff --check 993b3479cb2401b8a9d831695a4244b8fa947bfe...HEAD`.

## Unavailable checks and blockers

- PostgreSQL 16 Testcontainers migration, composite-lineage, concurrent-claim, and stale-claim proof are unavailable: the planned `RealOcrPostgresMigrationTest` and `OcrJobRepositoryPostgresTest` classes and their Testcontainers dependencies are absent from this branch. Existing H2 tests do not substitute.
- Real POSIX smoke is unavailable on this host because `jq` is missing; the syntax check passed.
- `pnpm audit --audit-level high` was not run because the execution safety policy rejected the external dependency-metadata disclosure. OWASP Maven security profiles were likewise not run in this resume.
- CV-04 remains unresolved and merge-blocking by explicit task instruction. This task did not implement, close, or weaken it.

## Result

The Jackson 2.x regression is corrected and verified, but Task 6 is not merge-ready: the unavailable PostgreSQL/Testcontainers and POSIX proof remain, CV-04 remains merge-blocking, and the filesystem scan has no valid current JSON evidence.

## 2026-07-29 remaining production-equivalent verification

- Fresh detached verification worktree: `C:\Users\kilia\AppData\Local\Temp\open-ecosystem-os-real-ocr-production-verification-20260729` at source `7e1303641a4eed21359d0d82aae37f72b309018d`.
- Added PostgreSQL 16 Testcontainers harnesses using Testcontainers `1.21.4`; this version was required because `1.20.6` could not negotiate with Docker Desktop Engine `29.6.1` / API `1.55`. The focused API proof passed: `RealOcrPostgresMigrationTest` migrates representative V6 rows through V7/V8, preserves compatible data, and rejects mismatched job/file workspace, result/job workspace, and extraction/job/result lineage. `WorkflowExecutionServiceTest` now executes its duplicate `OcrCompleted` delivery proof on PostgreSQL and passed with one execution, extraction, notification, audit, search document, consumption record, and indexing outbox record. The focused worker `OcrJobRepositoryPostgresTest` passed with two concurrent claim attempts producing exactly one owner and with strict stale-boundary behavior.
- POSIX execution was attempted in WSL Ubuntu. WSL has no Docker Desktop integration and no installed `jq`; non-interactive `sudo apt-get install jq` is unavailable. A temporary Linux jq image (`ghcr.io/jqlang/jq:latest`, `jq-1.8.2`) was available, but Docker Compose invoked from WSL cannot translate the repository's `/mnt/c/...` Compose path to the Windows Docker CLI. The tracked script is canonical LF (`i/lf`); the initial CRLF failure was a Windows working-tree conversion artifact. CV-03 remains unavailable, not passed.
- Fresh filesystem evidence: `docker run --rm -v open-ecosystem-os-trivy-cache:/root/.cache/trivy -v "${PWD}:/repo:ro" -v "${PWD}/.superpowers/sdd:/evidence" aquasec/trivy:0.72.0 fs --scanners vuln,misconfig --severity HIGH,CRITICAL --exit-code 1 --skip-db-update --skip-java-db-update --skip-dirs infra/k8s/overlays/dev --format json --output /evidence/real-ocr-remediation-trivy-filesystem.json /repo` exited `0`. JSON: `.superpowers/sdd/real-ocr-remediation-trivy-filesystem.json`. Trivy reported existing cached vulnerability and misconfiguration policies; detailed finding/baseline disposition remains to be counted before a clean claim.
- Safety-policy audit diagnosis: the report identifies `pnpm audit --audit-level high` and says it was blocked for external dependency-metadata disclosure. No report contains the exact policy response, so no exact-response claim is made. No repository-approved equivalent was identified in the inspected records.

## 2026-07-29 CV-01 PostgreSQL proof closed

- Detached-worktree commit: `34662b0d720de0cbdd1b6307583bdddde482a2d3` (`test(ocr): verify PostgreSQL migrations and claim concurrency`).
- Source cherry-pick on `feat/real-ocr-extraction`: `783a9f275047b2448a5a3428bb70d903effbba13`.
- Scope: only test-scoped Testcontainers `1.21.4` POM dependencies and PostgreSQL verification tests. It does not override a production runtime dependency.
- Proof: PostgreSQL 16 Testcontainers covers V6-to-V8 migration and lineage constraints; duplicate `OcrCompleted` delivery creates exactly one side-effect set; competing workers produce exactly one claim owner; and the stale-claim boundary is strict.
- Source verification passed: `apps/api/.\\mvnw.cmd -q "-Dtest=RealOcrPostgresMigrationTest,WorkflowExecutionServiceTest" test`, `apps/worker/.\\mvnw.cmd -q "-Dtest=OcrJobRepositoryPostgresTest" test`, and `git diff --check`.
- CV-01 is closed. CV-03 POSIX real-OCR smoke, current pnpm-audit evidence, and CV-04 `file:view` authorization architecture remain open. The exact historical safety-policy response is unavailable and was not reconstructed. Task 6 remains incomplete.

## 2026-07-29 CV-03 Git Bash Docker Desktop execution

- The paths requested as `scripts/ocr-smoke.sh` and `scripts/ocr-smoke.ps1` do not exist on this branch. The tracked canonical scripts are `scripts/smoke-real-ocr.sh` and `scripts/smoke-real-ocr.ps1`; only the POSIX canonical script was executed.
- Git Bash executable: `C:\Program Files\Git\bin\bash.exe`; `GNU bash, version 5.3.9(1)-release (x86_64-pc-cygwin)`. From that shell, `docker version` succeeded against Docker Desktop context `desktop-linux`: client/server Engine `29.6.1`, API `1.55`, Docker Desktop `4.80.0`. `docker compose version` reported `v5.3.0`. `bash`, `curl`, `docker`, `grep`, `head`, `date`, and `sleep` resolved; `jq` did not.
- `jq` was supplied only for validation as official pinned `jq-windows-amd64.exe` version `jq-1.8.1`, downloaded to `C:\Users\kilia\AppData\Local\Temp\real-ocr-cv03-20260729\jq.exe` and prepended to `PATH`. It was deleted with the temporary directory after the run; neither jq nor a wrapper was added to the repository or worker image. An initial pinned Docker-wrapper attempt failed because `jqlang/jq:1.8.1` is not available on Docker Hub; the temporary binary replaced it.
- POSIX syntax validation: `& 'C:\Program Files\Git\bin\bash.exe' --noprofile --norc -lc 'sh -n scripts/smoke-real-ocr.sh'`; exit `0`.
- The first stack-start command was `& 'C:\Program Files\Git\bin\bash.exe' --noprofile --norc -lc 'export COMPOSE_PROJECT_NAME=real-ocr-cv03-20260729; export PATH=/c/Users/kilia/AppData/Local/Temp/real-ocr-cv03-20260729:$PATH; ./scripts/smoke-real-ocr.sh --start-stack --timeout-seconds 240'`. It reached the real isolated stack but exited `1` at Git Bash's native MinGW curl boundary: `curl: (26) Failed to open/read local data from file/application`. The fixture existed at the POSIX path; direct native curl accepted the equivalent `C:/...` path and reached its target. A temporary external `curl` wrapper therefore converted only `file=@/c/...` form arguments to the accepted Windows path, without modifying the tracked smoke script.
- Successful actual smoke command: `& 'C:\Program Files\Git\bin\bash.exe' --noprofile --norc -lc 'export COMPOSE_PROJECT_NAME=real-ocr-cv03-20260729; export PATH=/c/Users/kilia/AppData/Local/Temp/real-ocr-cv03-20260729:$PATH; ./scripts/smoke-real-ocr.sh --timeout-seconds 240'`; exit `0`, output: `Real Tesseract OCR smoke passed for the clearly labelled synthetic test invoice.` The isolated worker image ID was `sha256:79082590fe1ec9b9469344c57e015dc31c60e370c638d3d6bf31c84021b1a8c9`.
- Persisted smoke result `ocr_e05ff8d3ae5b4cd4ad59c75079c02b30`: `status=completed`, provider and nested OCR provider `tesseract`, persisted provider version `5.5.1` matching `tesseract 5.5.1` in the worker, `wordCount=34`, and extraction `review_required=true`.
- Downstream aggregate checks for the smoke correlation ID found one workflow execution, one notification, five audit records, one search document, 15 outbox events, and four consumed events. Audit attributes, event payload/envelope, and notification title/body contained none of the synthetic invoice's normalized field values. The search document did contain those values in its title, summary, content, and metadata JSON, so this run does **not** establish a metadata-only search-document side effect; no production correction was made because that is out of scope.
- Cleanup passed: `docker compose ... down --volumes --remove-orphans` removed the isolated containers, networks, and project volumes; `docker compose ... ps --all` returned only the header. The three `real-ocr-cv03-20260729` image tags and the temporary validation directory were removed. CV-04 remains open and separate.

## 2026-07-29 current pnpm audit evidence

- Documented command and frontend working directory: `C:\Users\kilia\Documents\open-ecosystem-os\apps\web`, `pnpm audit --audit-level high`.
- The Codex sandbox blocked the current attempt before `pnpm --version` or the audit command could start. Exact current error: `Failed to create unified exec process: runner failed during SpawnChild: CreateProcessAsUserW failed: 5 (Acceso denegado.)`, for `pnpm --version` in that working directory. Therefore the actual pnpm version, current audit findings, and audit exit code are unavailable; this gate is not passed.
- Non-destructive command for normal PowerShell: `Set-Location 'C:\Users\kilia\Documents\open-ecosystem-os\apps\web'; pnpm --version; pnpm audit --audit-level high`. Record that command's version, findings, and exit code before claiming the frontend audit gate. No historical safety-policy response was reconstructed, and Trivy evidence is not used as a substitute.

### User-supplied normal-PowerShell audit output

- The supplied output records `pnpm audit --audit-level high` and reports `17 vulnerabilities found`: five moderate and 12 high. The high findings are two `brace-expansion` advisories through ESLint/minimatch paths, `js-yaml@4.2.0`, `sharp@0.34.5` via Next.js, four Next.js advisories for `next@16.2.6`, and `postcss@8.5.14`; patched versions are present in the supplied output.
- This is current audit evidence and it does not pass the repository's high-severity gate. The supplied text does not include the executed working directory, pnpm version, or process exit code, so those values are not inferred. Its Node `DEP0169` line is a deprecation warning, not a reported dependency vulnerability.

## 2026-07-29 corrected CV-03 search-projection conclusion

- The approved design distinguishes the projection from metadata-only side effects: `OcrStarted`, `OcrCompleted`, and `OcrFailed`, audit attributes, notification bodies, and outbox envelopes must exclude OCR text, TSV, extracted values, warnings, and source coordinates. Search may index approved structured invoice fields, but never raw OCR text or TSV.
- The actual projection allowlist in `WorkflowRunner.searchDocument` is exactly `invoice_number`, `supplier_name`, `total_amount`, `currency`, `issue_date`, and `due_date`. It reads only `InvoiceExtractionField.normalizedValue` for those keys; no warnings, provenance, OCR words, OCR text, display values, or other extraction keys enter the search document. Title, summary, content, and metadata are all constructed only from that same list.
- The isolated smoke result had the five populated synthetic fields `invoice_number`, `supplier_name`, `total_amount`, `currency`, and `issue_date`; each is allowlisted. `due_date` was absent. The earlier aggregate check therefore correctly observed those normalized values in the search projection but incorrectly treated their presence as a metadata-only side-effect failure. It also proved the audit attributes, outbox payload/envelope, and notification title/body excluded those values.
- No raw OCR text, warnings, provenance, or non-allowlisted extraction content is reachable through the implemented search projection. The search assertion passes, and CV-03 POSIX real-OCR smoke is closed. CV-04 remains open and merge-blocking.

## 2026-07-29 reproducible differential pnpm audit

- Documented project directory: `C:\Users\kilia\Documents\open-ecosystem-os\apps\web`. Feature audit environment: Node `v24.16.0`, pnpm `10.0.0`; command `pnpm audit --audit-level high`; exit `1`; result five moderate and 12 high. Raw feature evidence: `.superpowers/sdd/real-ocr-remediation-pnpm-audit-feature.txt` and `.superpowers/sdd/real-ocr-remediation-pnpm-audit-feature.json`.
- Original final-review merge base: `993b3479cb2401b8a9d831695a4244b8fa947bfe` (`develop`). Baseline used the clean detached worktree `C:\Users\kilia\AppData\Local\Temp\open-ecosystem-os-real-ocr-baseline`; `pnpm install --frozen-lockfile` exited `0`, then Node `v24.16.0` and pnpm `10.0.0` ran the identical audit command from its `apps\web` directory. The audit exited `1` with five moderate and 12 high. Raw baseline evidence: `.superpowers/sdd/real-ocr-remediation-pnpm-audit-merge-base-993b3479.txt` and `.superpowers/sdd/real-ocr-remediation-pnpm-audit-merge-base-993b3479.json`.
- The raw text differs only in the runtime Node process ID. After normalizing that non-finding value, the text is identical. Parsed HIGH advisory records, vulnerable/patched ranges, installed versions, complete dependency-path arrays, and path counts are identical. `git diff --name-status 993b3479cb2401b8a9d831695a4244b8fa947bfe...HEAD -- apps/web/package.json apps/web/pnpm-lock.yaml` produced no change, so every listed lockfile entry is unchanged.

| Advisory            | Package / unchanged lockfile entry                | Vulnerable → patched              | Dependency path comparison                                                | Class |
| ------------------- | ------------------------------------------------- | --------------------------------- | ------------------------------------------------------------------------- | ----- |
| GHSA-3jxr-9vmj-r5cp | `brace-expansion@1.1.14`                          | `<1.1.16` → `>=1.1.16`            | 70 identical paths via `eslint@9.39.1 > minimatch@3.1.5`                  | B     |
| GHSA-3jxr-9vmj-r5cp | `brace-expansion@5.0.6`                           | `>=3.0.0 <5.0.7` → `>=5.0.7`      | 11 identical paths via `eslint-config-next@16.2.6 > … > minimatch@10.2.5` | B     |
| GHSA-52cp-r559-cp3m | `js-yaml@4.2.0`                                   | `>=4.0.0 <4.3.0` → `>=4.3.0`      | 22 identical paths via `eslint@9.39.1 > @eslint/eslintrc@3.3.5`           | B     |
| GHSA-f88m-g3jw-g9cj | `sharp@0.34.5`                                    | `<0.35.0` → `>=0.35.0`            | one identical path: `next@16.2.6 > sharp@0.34.5`                          | B     |
| GHSA-6gpp-xcg3-4w24 | `next@16.2.6`                                     | `>=16.0.0 <16.2.11` → `>=16.2.11` | identical direct path                                                     | B     |
| GHSA-m99w-x7hq-7vfj | `next@16.2.6`                                     | `>=16.0.0 <16.2.11` → `>=16.2.11` | identical direct path                                                     | B     |
| GHSA-89xv-2m56-2m9x | `next@16.2.6`                                     | `>=16.0.0 <16.2.11` → `>=16.2.11` | identical direct path                                                     | B     |
| GHSA-p9j2-gv94-2wf4 | `next@16.2.6`                                     | `>=16.0.0 <16.2.11` → `>=16.2.11` | identical direct path                                                     | B     |
| GHSA-mh99-v99m-4gvg | `brace-expansion@1.1.14`, `brace-expansion@5.0.6` | `<=5.0.7` → `>=5.0.8`             | the same 70 and 11 paths above                                            | B     |
| GHSA-r28c-9q8g-f849 | `postcss@8.5.14`, `postcss@8.5.15`                | `<=8.5.17` → `>=8.5.18`           | six identical paths via Next/Tailwind/direct and Vite/Vitest              | B     |

- Classification: A introduced = none; B pre-existing and unchanged = all 12 HIGH package-version findings represented by the advisory rows above; C worsened = none; D resolved = none; E cannot determine = none. The full, non-abbreviated dependency paths are preserved in the paired raw JSON files. The historical manual audit remains metadata-incomplete and is not used for Node version, directory, or exit-code claims. No production dependency, lockfile, or allowlist was changed.

## 2026-08-29 Task 5 source-file authorization remediation

### Scope

- Branch: `feat/real-ocr-extraction`.
- Search documents produced by `WorkflowRunner.searchDocument` now persist the source Drive `fileId` in metadata alongside the approved invoice projection.
- `SearchService` converts local and Meilisearch hits to internal candidates, authorizes their source file with the existing `ResourceAuthorizationService` and `file:view`, then constructs public `SearchResultResponse` objects. Authorization is performed independently for each backend before their results are merged; the local fallback uses the same already-filtered candidates.
- A missing, null, blank, or non-string `metadata.fileId` is omitted without calling authorization. No search-specific permission evaluator, authorization bypass, endpoint, schema migration, or dependency upgrade was introduced.

### CV-04 evidence: PASS

- Drive file-view authorization remains persisted and shared through the existing resource-authorization service.
- OCR protected loads and listings remain guarded by that shared policy; the focused matrix covers missing, foreign, denied, owner, workspace-role, and explicit-grant behavior without enumerating unauthorized resources.
- Local Search, Meilisearch Search, merged results, and local fallback all apply source-file `file:view` authorization before exposing results. Denied or invalid-lineage candidates cannot affect result bodies, duplicate choice, counts, or backend labels.
- `SearchServiceTest` covers owner, eligible workspace-role, explicit user grant, revoked grant, private file, foreign workspace, inactive membership, missing/blank/non-string lineage, backend filtering before merge, fallback filtering, a denied remote duplicate, and distinct source-ID batching. `WorkflowExecutionServiceTest` asserts the persisted `fileId` exactly.

### Verification run

- Focused API matrix passed:
  `DriveFileRepositoryTest, DriveFileUserGrantPostgresRepositoryTest, FileViewAuthorizationRepositoryTest, DefaultResourceAuthorizationServiceTest, PlaceholderAuthenticationContextTest, DriveFileControllerTest, DriveFileSharingServiceTest, OcrJobRepositoryTest, OcrJobQueryServiceTest, OcrJobControllerTest, SearchServiceTest, WorkflowExecutionServiceTest, RealOcrPostgresMigrationTest`.
- Full API `mvn -q test` and `mvn -q spotless:check` passed.
- A final `SearchServiceTest` rerun passed after the last internal-only visibility cleanup. The paired `WorkflowExecutionServiceTest` rerun could not start because Docker Desktop's Linux engine became unavailable; its earlier Testcontainers run and the full API suite passed while the engine was available. This is an environmental recheck blocker, not an assertion failure.
- Web `pnpm format:check`, `pnpm lint`, `pnpm typecheck`, `pnpm test` (50 tests), and `pnpm build` passed.
- The synthetic real-Tesseract smoke passed against the Compose API on port `18080`: `Real Tesseract OCR smoke passed for the clearly labelled synthetic test invoice.` The default port `8080` was already owned by an unrelated local Java service, so no source change was made for that environmental conflict.

### Current security evidence

- `pnpm audit --audit-level high` reports 11 findings: five moderate and six high. `pnpm audit --prod --audit-level high` reports three findings: one moderate and two high (`nanoid` through `next@16.2.6 > postcss@8.5.14`). The high-severity dependency gate therefore remains blocked.
- `git diff --name-status develop...HEAD -- apps/web/package.json apps/web/pnpm-lock.yaml` is empty. The current audit findings are classified B (pre-existing and unchanged); Task 5 did not add or modify frontend dependency manifests or lockfile entries. They still require separately approved dependency remediation before merge.
- A current Trivy rerun is unavailable because the local Docker Desktop Linux engine was not reachable. No clean or updated Trivy claim is made; the prior dated Trivy evidence above is retained as historical evidence only.
