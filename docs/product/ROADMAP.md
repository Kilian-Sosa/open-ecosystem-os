# Roadmap

## Phase 0 — Blueprint and repository foundation

Deliverables:

- monorepo created
- docs and decision files added
- DESIGN.md and AGENTS.md added
- Docker Compose scaffold
- Kubernetes scaffold
- initial CI skeleton

## Phase 1 — UI and backend foundations

Deliverables:

- Next.js app shell
- design tokens
- shared UI components
- dashboard page
- Spring Boot API skeleton
- PostgreSQL connection
- health/readiness endpoints
- initial auth placeholder
- Dockerized web/api/worker

## Phase 2 — Drive and object storage

Deliverables:

- file upload
- file metadata
- MinIO storage
- Drive file list
- file detail panel
- FileUploaded event
- audit record for upload

## Phase 3 — Media/OCR pipeline

Deliverables:

- OCR job entity
- queue-based worker
- job status lifecycle
- extracted text storage
- OcrCompleted/OcrFailed events
- Media/OCR UI
- search indexing of OCR text

## Phase 4 — Open Ecosystem Flows MVP

Deliverables:

- workflow definition model
- workflow versions
- manual trigger
- file-upload/OCR-completed trigger
- basic action nodes
- execution history
- retries
- dead-letter handling
- workflow run detail UI

## Phase 5 — Flagship invoice automation demo

Deliverables:

- seeded invoice PDF
- OCR processing
- AI/mock structured extraction
- workflow automation
- notification
- audit log
- search result
- case study page
- demo reset command

## Phase 6 — Open Pages MVP

Deliverables:

- nested pages
- block editor using existing editor framework
- comments-lite
- search indexing
- AI summary action
- page events

## Phase 7 — Security, permissions, and admin hardening

Deliverables:

- RBAC model
- resource permissions
- sharing modal logic
- security settings
- activity/audit log filtering
- backup/restore MVP

## Phase 8 — Kubernetes and observability

Deliverables:

- Kubernetes manifests or Helm chart
- dev and prod overlays
- Prometheus/Grafana/Loki profile
- metrics dashboards
- worker metrics
- alert rules

## Phase 9 — Extensibility

Deliverables:

- App management
- Event catalog
- API explorer
- Plugin developer portal
- Plugin review queue
- Marketplace MVP

## Phase 10 — Later apps

Candidates:

- music player
- video/Plex-like media app
- advanced analytics
- community forum
- billing/subscriptions for hosted/support plans
