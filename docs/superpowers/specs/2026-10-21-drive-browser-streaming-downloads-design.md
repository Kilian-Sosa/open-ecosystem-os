# Drive browser streaming downloads

**Status:** Implementation authorized and verified on 2026-10-21 after the user
approved executing the linked plan. Route, deployment-origin and private-only
constraints are implemented; see the plan's execution evidence and limitations.
**Scope:** MVP Core folder ZIP downloads, web transport and its deployment configuration.
**Parent:** [Drive lifecycle](2026-10-18-mvp-core-drive-design.md).

## Intent and verified behavior

The user requests full streaming downloads now, replacing the temporary
25 MiB compressed-response browser cap. Every archive permitted by the existing
server limits must be downloadable without collecting it into a JavaScript
Blob, ArrayBuffer, string or chunk array in the browser or web server.

The API already prepares a complete ZIP in controlled temporary storage,
authorizes every descendant and streams that file through its servlet response.
The web client currently fetches the ZIP with seeded actor/workspace headers,
collects up to 25 MiB into memory, and triggers a Blob URL download.
Native browser navigation cannot attach those custom headers.

The Core uses a fixed seeded private development session; real multiuser
authentication remains outside this change. This design must not claim to
introduce public authentication or authorize public deployment.
The proxy itself provides no authentication: a public caller could cause it to
use the fixed seeded identity. It is acceptable only in trusted private MVP Core
deployments and must remain behind the same private access boundary as the apps.

## Decision and alternatives

Use a narrow same-origin Next.js download route. It sends the existing seeded
identity headers to the configured API and streams the response to the browser's
native download manager with backpressure and cancellation.

Direct navigation to the API is smaller but relies on implicit backend identity
fallback and exposes cross-origin deployment details to the download action.
Browser filesystem APIs require browser-specific capabilities and a second
fallback path. Download tickets would add bearer credentials, retention and
cleanup state. None are needed for the current Core contract.

## Web transport contract

- Add `GET /downloads/drive/folders/{folderId}` to the **Next.js web host**.
  Proxy the existing Spring Boot endpoint
  `GET /api/drive/folders/{folderId}/download`. Keep the two public paths distinct:
  Kubernetes Ingress routes the entire `/api` prefix directly to Spring Boot,
  while `/downloads` reaches the web service through its existing `/` rule.
  Do not change Ingress routing or add special download exceptions.
- Resolve the upstream origin only from server-only `API_BASE_URL`. Require an
  explicit absolute HTTP(S) URL with a hostname, no userinfo, query or fragment,
  and no path other than `/`. Reject missing, empty, whitespace-only, relative
  and non-HTTP(S) values with a sanitized 502 before attempting fetch. Do not
  fall back to `NEXT_PUBLIC_API_BASE_URL`, which can legitimately be empty for
  same-origin browser calls. An unreachable valid origin also returns a
  sanitized 502. Never accept an upstream URL from requests.
- Validate the folder ID as one opaque path segment and encode it. No arbitrary
  proxy path, query/header forwarding, credentials in URLs or redirects to
  unvalidated origins. Upstream fetch uses `redirect: error` and `cache: no-store`.
- Supply the same fixed seeded identity headers used by the existing web client.
  Do not let caller headers/query parameters override the identity. Future real
  authentication must replace this adapter with verified request identity;
  never silently fall back to the seeded user in that future mode.
- Return the upstream byte stream without materializing the archive. Preserve
  Content-Length when available, application/zip and the API's attachment
  Content-Disposition. Require the successful response to be a ZIP attachment.
  Request identity content encoding and reject an unexpectedly encoded upstream
  attachment rather than forwarding a length that no longer matches decoded bytes.
  Do not forward cookies, internal headers or upstream diagnostic bodies.
- Apply no-store, nosniff, no-referrer and a restrictive CSP to web responses.
  Forward only explicitly allowed download headers. Do not log names, content,
  upstream bodies or storage details.
- Preserve relevant API failure statuses with short constant error text;
  connection/preparation transport failures return a sanitized 502. Failures
  before transfer must not become successful ZIP attachments.
- Propagate downstream abort/cancellation to the upstream fetch and reader,
  releasing stream resources. After headers are sent, transfer failure aborts
  the response; never append an error message to ZIP bytes. Mid-transfer errors
  may produce a failed/truncated download in the browser's download manager.

## Browser interaction

Use a real, keyboard-accessible link styled with existing Drive action styles.
The link targets the same-origin web route and opens a separate browsing context
with noopener/noreferrer. Successful attachments go to the native download
manager; failure responses remain visible separately without replacing Drive.
Preparation/transfer progress and cancellation belong to the browser. Do not
show an application success state claiming the download completed.

Remove the ZIP Blob helper and its 25 MiB cap. Preserve bounded previews and
the existing individual-file download behavior. No service worker, filesystem
picker, new dependency, new design-system component or mobile-only branch.

## Existing invariants and deployment

Server archive limits remain 1,000 entries, 512 MiB uncompressed and a two-minute
cooperative preparation budget by default. The complete preflight authorization,
private spool, portable name escaping, byte integrity and cleanup remain intact.
No schema, Java API, worker, event or permission grant changes.

The web server must be able to reach the configured API origin. Existing
reverse proxies must permit streaming attachments and appropriate response
durations. Local development explicitly supplies `API_BASE_URL=http://localhost:8080`
or the actual host API port. In `infra/docker/docker-compose.yml`, change only
the web service's server-side `API_BASE_URL` to `http://api:8080`, using Compose
service DNS rather than the web container's localhost. Do not reuse the
host-side `.env` API origin override for this internal destination. Preserve
`NEXT_PUBLIC_API_BASE_URL` and the existing browser build configuration.
Kubernetes already configures an absolute internal API service URL; preserve it
and the existing Ingress rules. No new infrastructure service or global
configuration is required.
Constant-memory claims concern application code; browser/network/proxy buffers
and disk availability are platform controlled.

## Acceptance and verification

1. A browser downloads a synthetic ZIP above 25 MiB through the real web route;
   saved size/content match the upstream fixture. Verify desktop and mobile
   browser download initiation, and keyboard activation.
2. Client ZIP actions never fetch response bytes or create Blob URLs. Existing
   original downloads and preview bounds retain their tests.
3. Route tests prove correct encoded upstream path, configured origin and fixed
   identity headers, no caching/redirect following and safe response headers.
   Cover missing/empty/relative/invalid schemes and unreachable server origins;
   an empty public API base never becomes the server upstream. Caller-supplied
   identity headers, URLs and queries cannot affect the fixed destination.
4. A gated upstream stream proves the first chunk reaches the downstream before
   the upstream finishes; the implementation does not prebuffer the response.
5. Cancellation and upstream errors release/cancel the reader and fetch. Denied,
   oversized, unavailable and malformed upstream responses expose no sensitive
   body or downloadable partial archive before transfer begins.
6. API ZIP tests continue to verify complete authorization, bytes, portable names
   and cleanup. HTTP tests observe eventual filesystem cleanup within a bounded
   deadline because receiving Content-Length bytes can precede servlet return.
   Service tests retain immediate cleanup assertions after close returns.
7. Web tests, lint, typecheck, formatting and production build pass. Update the
   Drive lifecycle spec, archive contract, route map and execution record to
   supersede the temporary client cap. Record browser proof and any limits.
8. Exercise the streaming route in both local development and a private Docker
   Compose stack with the real web/API services. Compose web must reach
   `http://api:8080` while host browser calls retain their existing destination.
   Verify a complete ZIP above 25 MiB with exact entry bytes in both environments.
   Verify the configured Kubernetes rules send `/downloads/...` to web and
   `/api/...` to API without special exceptions; distinguish static routing
   checks from any live cluster proof.

Implementation sequencing and concrete checks are in the
[implementation plan](../plans/2026-10-21-drive-browser-streaming-downloads.md),
which records completed execution and acceptance evidence.

## Spec impact and exclusions

**Update existing spec**: replace the temporary browser cap with
native streamed full downloads, retaining all server bounds and security rules.
This focused design defines transport details without duplicating lifecycle
requirements. Resumable/range downloads, archive jobs/tickets, public auth,
progress events, ZIP generation changes and streaming original-file downloads
are excluded.

## Independent test timing correction

The requested cleanup test correction is bounded and does not change product
behavior: poll the actual private temporary directory until empty, at 25 ms
intervals with a five-second deadline, closing each directory stream. A real
leak still fails the assertion. The controller suite passes 27 tests with this
change. No production cleanup defect is asserted.
