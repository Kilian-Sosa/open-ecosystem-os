# MVP — shared platform and distribution design

**Status:** Proposed written design for user review
**Parent:** [MVP milestones](2026-10-18-mvp-milestones-design.md)

## Purpose

Keep the existing modular/event-driven platform coherent while delivering a
small, genuinely usable private **Core**, then harden it for safe installation
and public demonstration in **Complete**. This document does not authorize
implementation of unrelated roadmap apps.

## Verified platform baseline

- Next.js/React/Tailwind app shell has routes for dashboard, Drive, OCR, Flows,
  search, notifications and audit. Dashboard is mock-backed; settings and
  system status are route placeholders; root redirects to the dashboard.
- Spring Boot 4 / Java 25 API is a modular monolith with Drive, identity,
  workspace, media, invoice, flows, search, notifications, audit and admin
  packages. Flyway migrations `V1` through `V9` implement current tables.
- Java worker processes OCR and search indexing. PostgreSQL, MinIO, RabbitMQ
  and Meilisearch are wired through the current Compose stack. Redis is in
  the infrastructure catalog but is not proof of an active Core feature.
- Events/outbox, consumed-event deduplication, retries, notifications,
  workflow execution records and audit metadata are already represented in
  code. Existing Flows editor and API need not be expanded to meet Core.
- Seeded session mode `seeded_dev` uses a dev actor/workspace and optional
  `X-Actor-Id`/`X-Workspace-Id` headers. This is not public authentication.
- Health/readiness and metrics endpoints, Docker Compose, Kubernetes
  Kustomize base/overlays and optional observability infrastructure exist.
  CI runs component quality/security checks. This does not prove a production
  rollout or full end-to-end disaster recovery.

## Core platform contract

- **One trusted owner, private environment:** allow the existing seeded
  identity only in explicitly local/private configurations. Never describe
  it as secure public login or permit an Internet-facing demo to rely on it.
- **Drive-first:** built-in OCR, image-to-PDF and invoice extraction launch
  directly from Drive. Reuse backend modules and existing workflow
  execution/queue contracts; a user-facing arbitrary workflow editor is not
  required for Core. Existing notification/audit screens may remain useful
  auxiliary views but are not separate product-completeness milestones.
- **Operationally useful:** configure via documented local environment,
  run a repeatable Compose startup, inspect job failures and service health,
  restart without losing completed files/results, and have a documented
  smoke test for all five user journeys.
- **Privacy and authorization:** default private encrypted storage,
  workspace and `file:view` checks on original bytes, derivatives, OCR,
  drafts and search; mutation permissions are distinct from view. Events,
  logs and audit never contain document content or extracted values.
- **Quality:** add independent API, worker and frontend acceptance tests,
  including retries, deletion/index races, data leakage, concurrency and
  review gating. Close the existing placeholder `make test-e2e` gap with a
  working Core journey test before declaring Core complete.
- **Incremental delivery:** no new microservice or marketplace infrastructure
  unless a demonstrated Core need requires it; do not overbuild dashboard,
  user settings, Open Pages or general PDF editing.

## MVP Complete distribution contract

1. **Identity:** real registration/invitation or instance-admin provisioning,
   secure sessions, logout/revocation, CSRF protections as appropriate, safe
   credential recovery, and removal of seeded-header identity outside dev.
2. **Tenancy and security:** real workspace memberships, protected object
   sharing, permissions enforced on all existing/new API endpoints and jobs,
   documented external exposure defaults, abuse/rate limiting, and a security
   deployment review.
3. **Install and operate:** first-time setup, documented configuration
   validation and upgrades/migrations, durable volume/key management,
   health/metrics, backup/restore plus restore test, upgrade/rollback notes
   and actionable error reporting.
4. **Public demo:** separate demonstration deployment containing **only
   synthetic/sample documents**, strong isolation from real user data,
   resource/usage controls, reproducible demo reset and restrictions on
   expensive or destructive operations. A public portfolio/docs site can
   showcase the product without exposing a private workspace.
5. **Release quality:** installation tests, relevant e2e tests, security
   scanning, license/dependency checks, deploy docs, responsive/accessibility
   review and clear differentiation of optional/experimental capabilities.

These items define **readiness criteria**, not an already approved detailed
authentication, tenancy, public-demo or backup architecture. Each such
subsystem gets a focused SDD design and review before implementation.

## Exclusions and relationship to prior roadmap

The previous MVP product catalog includes Open Pages, sophisticated PDF
editing, a full public portfolio, generalized settings/admin and other
screens. They remain part of the larger product **vision** but do not block
the five-feature Core. They are not silently required by Complete either,
unless a future separately approved distribution spec establishes a real
dependency. Open Ledger remains post-MVP.

## Acceptance criteria

- Core: private Compose stack runs user journeys with durable data and
  observable errors; seeded identity remains confined to trusted use;
  permissions and events do not leak source content.
- Complete: a new operator can install and configure an instance and real
  users can sign in safely; independent visitors can use an isolated,
  resettable demonstration without accessing private data.
- Both: regressions in upload/processing/indexing/confirmation and
  cross-workspace access fail automated verification before release.
