# Drive Browser Streaming Downloads Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Use one inline production-code owner; no automatic review or test-runner agent.

**Goal:** Download every server-permitted folder ZIP through the browser download manager without buffering the archive in application JavaScript or imposing a 25 MiB client cap.

**Architecture:** A Next.js Node route at `/downloads/drive/folders/{folderId}` streams the existing Spring Boot attachment at `/api/drive/folders/{folderId}/download`. It supplies the fixed Core seeded identity, validates a server-only API origin, and propagates cancellation. Compose supplies the API service DNS origin; Kubernetes routing remains unchanged.

**Tech Stack:** Existing Next.js App Router, TypeScript, React, Web Streams, Vitest/Testing Library, PowerShell, Docker Compose and Spring Boot verification. No new runtime dependencies.

**Spec:** [Approved streaming design](../specs/2026-10-21-drive-browser-streaming-downloads-design.md), extending [Drive lifecycle](../specs/2026-10-18-mvp-core-drive-design.md).

**Status:** User authorized implementation on 2026-10-21; executed inline on
`feat/drive-folder-explorer`. See execution evidence below. No commit/push/merge.

## Global Constraints

- Trusted private MVP Core only. This proxy does not authenticate requests; public exposure would allow callers to use its fixed seeded identity.
- Preserve server limits: 1,000 entries, 512 MiB uncompressed, two-minute cooperative preparation budget by default. Preserve authorization, spool cleanup, portable names and exact original bytes.
- No browser or web-server archive Blob, ArrayBuffer, text conversion or accumulated chunk array. Stream with backpressure; bounded per-chunk buffering is acceptable.
- Require an explicit absolute HTTP(S) server `API_BASE_URL` with hostname, no credentials/query/fragment and no path beyond `/`. Never use the public API base as a fallback.
- Compose web uses `http://api:8080`, regardless of host `.env` API origin. Preserve browser-facing API configuration and Kubernetes Ingress rules.
- Preserve existing previews and individual-file downloads. No Java endpoint, schema, permission grant, worker or event change.
- Before editing, inspect `git status --short --branch`. Current related branch is `feat/drive-folder-explorer`; preserve the uncommitted private-name, ZIP-escaping and cleanup-test work already present. Do not create a new base or discard those edits.
- No commit/push/merge is authorized by this plan. If commits are later requested, use scoped conventional messages and exact file selections.
- Read repository AGENTS.md, the linked design, relevant development/security/test instructions and files before execution. Use PowerShell and installed pinned Node entry points if Corepack fails.

## File and Interface Map

- Create `apps/web/src/app/downloads/drive/folders/[folderId]/route.ts`: validated fixed-destination Node streaming handler; no general-purpose proxy.
- Create `apps/web/src/app/downloads/drive/folders/[folderId]/route.test.ts`: Node-environment route contract, backpressure/error/cancellation tests.
- Modify `apps/web/src/features/drive/drive-folder-actions.tsx`: native same-origin download link using existing `driveButton` style.
- Modify `apps/web/src/lib/drive-api.ts`: remove folder ZIP Blob path and `downloadDriveFolderZip`; retain file downloads and preview limits.
- Update `apps/web/src/lib/drive-api.test.ts`, `apps/web/src/features/drive/drive-api.test.ts` and `apps/web/src/features/drive/drive-folder-explorer.test.tsx`: replace temporary ZIP cap/fetch assertions with native-link behavior and retain original-file coverage. Do not discard unrelated tests.
- Modify `infra/docker/docker-compose.yml`: web service's server-side API origin only.
- Modify `.env.example`, `docs/development/ROUTES.md`, `docs/development/DRIVE_FOLDER_ARCHIVES.md`, `docs/development/TEST_COMMANDS.md`, `docs/development/LOCAL_DEVELOPMENT_CHECKLIST.md` and governing Drive spec: deployment contract, verification commands and cap supersession.
- Create `scripts/smoke-drive-streaming-download.ps1`: synthetic end-to-end HTTP download/integrity smoke accepting separate API and web base URLs; persist downloads only in task-owned temporary storage.
- Record execution in this plan and link from `docs/superpowers/plans/2026-10-18-mvp-core-drive-organization.md`.

## Review Focus

1. Kubernetes `/api` bypasses Next.js: browser links must use `/downloads/...`; verify existing prefix routing in Task 3.
2. Compose host `.env` defines localhost: ensure web's internal API origin remains `http://api:8080` and browser configuration is preserved; Task 1/3.
3. Missing/empty/relative or caller-controlled upstream: fail closed before fetch, including an empty public base; Task 1.
4. Cancellation before headers or during a pending stream read: stop upstream work and release listeners/readers, rather than leaving an orphaned transfer; Task 1.
5. A successful small unit test hides whole-body buffering: gated chunks and a real ZIP over 25 MiB prove incremental delivery and full browser download; Tasks 1/3.

---

### Task 1: Secure streaming web transport and container origin

**Files:** Create the route and route test above; modify Compose, `.env.example`, route map and local development checklist.

**Interfaces:** Export `runtime = "nodejs"`, `dynamic = "force-dynamic"`, and `GET(request: Request, context: { params: Promise<{ folderId: string }> }): Promise<Response>`. The response is a native attachment stream or a sanitized text error. Use existing `seededWorkspaceHeaders()` defaults; do not forward request identity headers.

- [x] **Step 1: Write failing Node-environment route tests.** Import the route and use `vi.stubEnv` plus mocked fetch, restoring both after tests. Cover: `usesConfiguredOriginAndFixedIdentity`, `rejectsInvalidOriginBeforeFetch`, `ignoresPublicAndCallerControlledOrigins`, `rejectsInvalidFolderSegments`, `sanitizesUpstreamFailures`, `requiresZipAttachment`, `streamsBeforeUpstreamCompletes`, `cancelBeforeHeadersAbortsFetch`, `cancelDuringReadReleasesUpstream`, and `upstreamFailureErrorsDownstream`. Expected path is exactly `/api/drive/folders/folder_synthetic/download` on the configured host; supplied actor/workspace headers, query URLs and unknown headers never reach upstream. Invalid origin cases include undefined, empty, whitespace, `/api`, `api:8080`, `ftp://api`, userinfo, query, fragment and a base subpath. Set the public base to empty and verify it cannot rescue missing server configuration.
- [x] **Step 2: Verify RED.** From `apps/web`, run `node node_modules/vitest/vitest.mjs run 'src/app/downloads/drive/folders'`; route import fails because implementation is absent. Once the module exists, assertions must fail for missing behavior rather than fixture errors.
- [x] **Step 3: Implement the route.** Validate one decoded opaque folder segment: nonempty, not `.`/`..`, no slashes, backslashes or controls; encode it when building the fixed backend path. Validate server configuration per the spec and use `URL` to construct the destination. Fetch with seeded headers, `Accept-Encoding: identity`, `redirect: "error"`, `cache: "no-store"`, and an owned abort signal linked to the incoming request. Accept only status 200, `application/zip`, an attachment disposition, an available body and absent/identity content encoding. Cancel invalid/error bodies without reading diagnostics. Preserve a valid nonnegative integer Content-Length only. Allowlist Content-Type, Content-Disposition and Content-Length; supply no-store, nosniff, no-referrer and sandbox CSP locally. Invalid IDs return constant 400 text; configuration/network/redirect/malformed-success failures return constant 502 text. Preserve 401/403/404/409/413/422/429/503 with constant status-specific text; other upstream failures become 502. Never attach Content-Disposition to errors.
- [x] **Step 4: Implement lifecycle handling.** Bridge the upstream reader through a pull-based ReadableStream. Read only on downstream demand; forward one chunk per pull, without accumulating bytes. Downstream cancellation or request abort cancels the reader and aborts fetch. Completion, failure and cancellation release the reader and remove abort listeners, including abort-before-fetch and pending-read races. Stream errors terminate the download without appending text. The gated-stream test must receive chunk one while chunk two is still withheld; use explicit gates rather than timing sleeps. Test unwanted cookie/internal/content-encoding headers are not forwarded.
- [x] **Step 5: Correct deployment configuration.** Set Compose web `API_BASE_URL: http://api:8080` explicitly; do not interpolate host `API_BASE_URL` there. Keep the API service's other environment and `NEXT_PUBLIC_API_BASE_URL`/Dockerfile build settings unchanged. Document host-local startup with process-only `$env:API_BASE_URL='http://localhost:8080'` or the actual isolated port. Explain the host `.env.example` value is not the container upstream. Add the `/downloads/drive/folders/{folderId}` web route to ROUTES.md. No Ingress changes.
- [x] **Step 6: Verify GREEN.** Run route tests, lint and typecheck using the installed Node entry points. Inspect only selected Compose fields using `docker compose -f infra/docker/docker-compose.yml config --format json`; project `{webApi, browserApi}` rather than printing configuration or secrets. With a synthetic process-only host override `API_BASE_URL=http://localhost:19087`, webApi must still equal `http://api:8080`; browserApi must retain its existing configured value. Never read real `.env` files. Run `powershell -File scripts/k8s-validate.ps1` if it supports the existing validation environment; report any concrete unavailable tooling without installing it speculatively.

### Task 2: Native browser ZIP action and cap removal

**Files:** Modify folder actions, drive-api.ts and the three listed client/UI tests.

**Interfaces:** The folder action renders an anchor to `/downloads/drive/folders/${encodeURIComponent(folder.folderId)}`, `target="_blank"`, `rel="noopener noreferrer"`, accessible name `Download ${folder.name} as ZIP`, and existing `driveButton` styles. Folder ZIP actions no longer expose an asynchronous fetch/download helper.

- [x] **Step 1: Write failing UI tests.** Assert the download control is a link with the exact same-origin path, safe target/rel and accessible name. Test an encoded opaque ID. Rendering and activating the action must not fetch ZIP bytes or call `URL.createObjectURL`. Preserve rename/move tests. Replace obsolete cap assertions, preserving separate tests proving original downloads still send workspace headers and revoke their Blob URLs. Preview oversize tests remain unchanged.
- [x] **Step 2: Verify RED.** Run `node node_modules/vitest/vitest.mjs run src/features/drive/drive-folder-explorer.test.tsx src/features/drive/drive-api.test.ts src/lib/drive-api.test.ts`; the old button/fetch path fails the native-link assertions.
- [x] **Step 3: Implement native navigation.** Replace the download mutation/button/error state with the link. Remove the ZIP Blob helper, archive flag in saveDownload and `downloadDriveFolderZip`; retain file-only saveDownload. Remove stale imports, ZIP network mocks and obsolete preparing/success/error claims. The browser handles transfer progress and errors in a separate context; do not claim completion inside Drive. Preserve mobile wrapping, focus styles and semantic link keyboard behavior.
- [x] **Step 4: Verify GREEN.** Run the affected tests, then the complete web suite, lint, typecheck, formatting and production build. Confirm all ZIP helper consumers have been removed with a focused source search, while original download and preview tests remain.

### Task 3: Local and Compose full-download acceptance and SDD reconciliation

**Files:** Create smoke script; update archive contract, TEST_COMMANDS.md, governing Drive spec, original execution record and this plan.

**Interfaces:** `scripts/smoke-drive-streaming-download.ps1 -ApiBaseUrl <absolute URL> -WebBaseUrl <absolute URL> -OutputDirectory <task-owned temporary directory>` creates a clearly labelled synthetic folder via existing API endpoints, uploads three supported text files, downloads through `/downloads/...`, and validates ZIP entries against source digests. It reports the synthetic folder ID and byte count for browser verification; no permanent test artifacts or real documents.

- [x] **Step 1: Build the reproducible smoke.** Generate three 16 MiB seeded pseudorandom printable text fixtures with expected SHA-256 hashes, below each file's upload limit. Stream the HTTP response to a task-owned ZIP file, inspect ZIP entries with .NET streams and compare hashes and entry count. Assert the ZIP exceeds 25 MiB; do not rely on compressibility assumptions. Test denied/missing IDs return sanitized non-attachment errors. Do not compare large files by base64 or collect archive bytes in the smoke client. Use only an isolated test API/stack because active Core has no cleanup/delete endpoint for these synthetic records.
- [x] **Step 2: Verify local development.** Start task-owned dependencies/API and local Next development server with explicit reachable host `API_BASE_URL`. Run the smoke with separate API and web URLs. Record actual archive byte count, matching digests and status/headers. If any dependency is blocked, record it and keep acceptance open; unit tests do not substitute for this check.
- [x] **Step 3: Verify Docker Compose.** Record initial Docker state; use the documented lifecycle CLI if needed. Use a unique project and localhost-only free ports, ensuring any temporary override affects only this project. Build/run the actual web and API services with their required dependencies; verify the web container's API origin is `http://api:8080`. Run the same smoke against published host URLs. Keep existing browser-facing configuration intact; any disposable port build arguments must match runtime browser verification rather than masking the internal origin defect. Clean only task-created containers, volumes and temporary files after gathering results; leave Docker Desktop running.
- [x] **Step 4: Verify real browser downloads.** Use the existing browser tool workflow (load its skill first), with a synthetic folder from the isolated stack and task-owned download directory. On desktop and mobile viewport, activate the native link and check that the saved archive exceeds 25 MiB and matches the smoke digest; verify keyboard activation and that Drive remains open. Verify a failure displays sanitized text separately. Distinguish mobile emulation from real-device proof. Do not add a permanent Playwright harness or dependencies solely for this acceptance step.
- [x] **Step 5: Verify Kubernetes path ownership statically.** Inspect `infra/k8s/base/ingress.yaml` and assert `/api` continues to select Spring Boot while `/downloads/...` matches the web `/` prefix. Validate manifests with the existing script when available. Do not claim a live cluster test from static validation; no Ingress exception or rewrite is allowed.
- [x] **Step 6: Reconcile SDD and verification.** Replace the temporary client cap requirement in the Drive lifecycle spec with full native streamed downloads subject to existing server bounds. Update archive contract, route map, local/Compose commands, trusted-private-only warning and original review record; mark historical cap evidence as superseded rather than rewriting it as streaming proof. Record spec-to-code/tests and behavior-to-spec checks, cancellation limitations and actual browser/deployment evidence. Retain the bounded cleanup assertion correction and its five-second/25 ms semantics.
- [x] **Step 7: Final checks.** From apps/web run `node node_modules/vitest/vitest.mjs run`, `node node_modules/eslint/bin/eslint.js .`, `node node_modules/typescript/bin/tsc --noEmit`, `node node_modules/prettier/bin/prettier.cjs <changed web files> --check` and `node node_modules/next/dist/bin/next build`. From apps/api run `mvnw.cmd -o verify` with Docker available. Run `git diff --check` and inspect exact changed files. No automatic independent reviewer. Report passed tests, both deployment smoke results, browser evidence, cleanup and any unresolved acceptance criteria.

## Plan Self-review and Handoff

Tasks 1/2 implement the transport/browser contract; Task 3 proves full-size
downloads, local/Compose reachability, Kubernetes routing and SDD alignment.
Origin validation, fixed identity, response-header allowlisting, error handling,
streaming/cancellation and private-deployment limits all have explicit tests or
deployment checks. No existing server export behavior is weakened.

Recommended execution: native inline, one owner, Tasks 1–3 in order. The route
and UI share a small contract; parallel implementation or an extra release-gate
review would add coordination beyond this scoped change. Review this plan
before implementation; existing uncommitted fixes remain preserved.

## Execution evidence — 2026-10-21

Implemented inline on the existing related branch after user authorization;
all pre-existing Java/private-name/portable-name/cleanup edits were preserved.
No commit, push or merge. Spec impact: **update existing spec** — the Drive
lifecycle and focused streaming design now agree with the native-download path.

- Task 1 RED: missing route import, then 45 route tests GREEN. Final owner review
  found URL parser normalization accepting forbidden `/base/..`, `/./`, a
  backslash and embedded newline; four added regressions failed before the raw
  origin validator fixed them. Final route suite: **53 passed**, including
  gated first-chunk/backpressure, before-header/pending-read/late-fetch abort,
  reader unlock, listener removal, sanitized statuses and header allowlisting.
- Task 2 RED: two native-link assertions failed against the button. GREEN:
  real encoded links, target/rel and no ZIP fetch/Blob URL; rename/move,
  original-file workspace headers/bytes/URL revocation and previews retained.
  Existing `driveButton` and wrapping/focus styles reused; no new components.
- Final web suite: **136 tests / 20 files**, ESLint, TypeScript, changed-file
  Prettier and production build pass. The new route is dynamic Node execution.
- API `mvnw.cmd -o verify`: **219 tests**, no failures/errors/skips; PostgreSQL,
  Spotless and packaging pass. No Java behavior was changed for streaming.
- Compose config with synthetic host `API_BASE_URL=http://localhost:19087`:
  `webApi=http://api:8080`, browser origin remains `http://localhost:8080`.
  Actual disposable web container: internal `http://api:8080`, browser
  `http://localhost:18087` matching its temporary build argument and runtime.
- Local Next development server on 13087 and actual Compose web on 13088 each
  passed the smoke against the disposable PostgreSQL/MinIO API on 18087:
  **41,678,188 ZIP bytes**, three 16 MiB printable synthetic entries with exact
  SHA-256 matches. Missing and real foreign-owner roots both return constant
  non-attachment 404 text. No active Core data was used.
- Installed Edge via bundled Playwright: headless 1440×1000 desktop Enter and
  390×844 mobile-emulated touch saved **41,678,188 bytes** in both deployments;
  all three entry digests match the smoke originals. Drive remains open and a
  separate context displays sanitized 404 text. Not real-device proof.
- Existing validator: base/dev/prod **11/24/11 valid resources**. Static Ingress
  review confirms `/api` selects Spring Boot and `/downloads/...` selects the
  web `/` prefix; no rewrite or live-cluster claim.

### Execution rulings and limits

1. Used the existing related checkout and one inline owner; no additional
   reviewer/worktree, per the plan. Owner self-review applied architecture,
   security/privacy and frontend checklists. Exact diffs preserve inherited work;
   this is not an independent release-readiness audit.
2. Used a PowerShell ledger instead of POSIX helper scripts to follow the Windows
   requirement. Durable evidence is retained here because no commits were made.
3. Added optional `-DeniedFolderId` to the smoke: the API has no identity
   provisioning surface. The isolated test setup created a synthetic root via
   API and changed its owner to a synthetic user in only disposable PostgreSQL.
   Both complete acceptance runs supplied it. Without it the result explicitly
   reports denied-fixture coverage as unchecked; it still checks a missing ID.
4. Compared ZIP **entry** hashes and total size across requests. Existing Java
   stamps ZIP entry times, so repeat container hashes differ. Changing that
   would violate this task's Java exclusion; original-byte integrity is proven.
5. Playwright CLI was absent. Used the already-bundled Playwright package and
   installed Edge in temporary scripts; no dependency or permanent harness added.
6. Sandbox Vite cache and Docker pipe access failed; narrow outside-sandbox
   reruns succeeded. No cache/global configuration changes were required.

Cancellation tests prove the web adapter lifecycle; there is no live browser
cancellation/load or physical memory-profile claim. Java preparation and external
storage operations remain cooperatively cancellable. Network/proxy buffers and
browser disk limits remain platform controlled. The seeded route is private-only
and provides no authentication. No API contract, schema, permission or event change.
The five-second/25 ms HTTP cleanup correction remains intact.

Spec-to-code/tests: explicit origin/fixed identity, streaming/abort/header/error
requirements map to route tests; native link maps to explorer tests; full-size
bytes/deployment behavior maps to HTTP smoke and Edge evidence. Behavior-to-spec:
only web transport, native action and Compose internal origin changed, all covered
by the focused design and updated lifecycle. No unresolved acceptance criteria or
deferred minor findings remain within the stated scope.

Final origin edge case: two further RED regressions showed empty userinfo markers (http://@api, http://:@api) normalize away in URL parsing. Raw authority validation now rejects every @ marker; the final 53-route/136-web-test gates include this correction.

### Cleanup

Task-owned local development servers and Edge contexts are closed. The isolated
`drive-streaming-20261021` containers, networks and five volumes were removed;
Compose lists no remaining projects and Docker Engine 29.8.2 remains running.
Synthetic sources, downloaded ZIPs, screenshots, fixture results, scratch scripts
and test logs are removed from this plan's ignored scratch directory. The
Next-generated untracked web AGENTS.md is removed; production build restored
next-env.d.ts to its original content. All durable evidence is recorded above.
