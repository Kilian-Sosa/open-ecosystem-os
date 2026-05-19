# Screen Catalog
This catalog links every designed screen to its implementation priority, product area, mockup reference, and required component patterns.
Priority tags: `P0` first vertical slice, `P1` MVP, `P2` post-MVP, `P3` long-term/showcase.

## Dashboard

- Priority: `P0`
- Area: Workspace
- Mockup: `docs/design/mockups/workspace/dashboard-light-desktop-mobile.png` (yes)
- Purpose: Central authenticated workspace overview
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## First-time Setup / Onboarding

- Priority: `P0`
- Area: Workspace
- Mockup: `docs/design/mockups/workspace/first-time-setup-onboarding-desktop-mobile.png` (yes)
- Purpose: Configure a self-hosted instance from zero to usable workspace
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Global Search Results

- Priority: `P1`
- Area: Workspace
- Mockup: `docs/design/mockups/workspace/global-search-results-desktop-mobile.png` (yes)
- Purpose: Search across files, pages, OCR text, forms, tasks, APIs, events, and ADRs
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Notification Center

- Priority: `P0`
- Area: Workspace
- Mockup: `docs/design/mockups/workspace/notification-center-desktop-mobile.png` (yes)
- Purpose: Review alerts, mentions, approvals, automation failures, and system updates
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Sharing and Permissions Modal

- Priority: `P1`
- Area: Workspace
- Mockup: `docs/design/mockups/workspace/sharing-permissions-modal-desktop-mobile.png` (yes)
- Purpose: Invite users/teams, manage access, and configure link permissions
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Error / Empty / Loading States

- Priority: `P0`
- Area: Workspace
- Mockup: `docs/design/mockups/workspace/error-empty-loading-states-desktop-mobile.png` (yes)
- Purpose: Reference states for resilient UX across the platform
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Drive / File Manager

- Priority: `P0`
- Area: Apps
- Mockup: `docs/design/mockups/apps/drive-file-manager-desktop-mobile.png` (yes)
- Purpose: Upload, browse, preview, tag, and manage files
- Key components: AppShell, UploadDropzone, FilePreview, FileTable, FileInspectorPanel, StatusChip, ProgressBar, MobileBottomSheet

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Media / OCR

- Priority: `P0`
- Area: Apps
- Mockup: `docs/design/mockups/apps/media-ocr-desktop-mobile.png` (yes)
- Purpose: Process uploads through OCR, metadata extraction, indexing, and downstream automation
- Key components: AppShell, UploadDropzone, FilePreview, FileTable, FileInspectorPanel, StatusChip, ProgressBar, MobileBottomSheet

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Open Ecosystem Flows

- Priority: `P0`
- Area: Apps
- Mockup: `docs/design/mockups/apps/open-ecosystem-flows-desktop-mobile.png` (yes)
- Purpose: Build, run, observe, and debug ecosystem-native automations
- Key components: AppShell, WorkflowCanvas, NodeCatalog, NodeConfigPanel, RunTimeline, StatusChip, DataTable, MobileBottomSheet

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Activity / Audit Logs

- Priority: `P0`
- Area: Platform
- Mockup: `docs/design/mockups/platform/activity-audit-logs-desktop-mobile.png` (yes)
- Purpose: Trace user actions, system events, security records, and automation activity
- Key components: AdminShell, MetricCard, StatusChip, DataTable, ActivityFeed, RightInspectorPanel, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## PDF Editor

- Priority: `P1`
- Area: Apps
- Mockup: `docs/design/mockups/apps/pdf-editor-desktop-mobile.png` (yes)
- Purpose: Preview, watermark, redact, OCR, clean metadata, split/merge, and export PDFs
- Key components: AppShell, UploadDropzone, FilePreview, FileTable, FileInspectorPanel, StatusChip, ProgressBar, MobileBottomSheet

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Open Pages

- Priority: `P1`
- Area: Apps
- Mockup: `docs/design/mockups/apps/open-pages-desktop-mobile.png` (yes)
- Purpose: Block-based workspace for pages, embedded resources, databases, knowledge, and collaboration
- Key components: AppShell, PageTree, BlockEditor, SlashCommandMenu, CollaboratorPresence, RightInspectorPanel, MobileBottomSheet

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Docs Editor / Collaboration

- Priority: `P1`
- Area: Apps
- Mockup: `docs/design/mockups/apps/docs-editor-collaboration-desktop-mobile.png` (yes)
- Purpose: Collaborative rich document editing with comments and user presence
- Key components: AppShell, PageTree, BlockEditor, SlashCommandMenu, CollaboratorPresence, RightInspectorPanel, MobileBottomSheet

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Security Settings

- Priority: `P1`
- Area: Platform
- Mockup: `docs/design/mockups/platform/security-settings-desktop-mobile.png` (yes)
- Purpose: 2FA, sessions, connected apps, API keys, login history, and admin policies
- Key components: AdminShell, MetricCard, StatusChip, DataTable, ActivityFeed, RightInspectorPanel, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## System Status

- Priority: `P1`
- Area: Platform
- Mockup: `docs/design/mockups/platform/system-status-desktop-mobile.png` (yes)
- Purpose: Service health, incidents, maintenance, infrastructure status, and queue health
- Key components: AdminShell, MetricCard, StatusChip, DataTable, ActivityFeed, RightInspectorPanel, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Backup and Restore

- Priority: `P1`
- Area: Platform
- Mockup: `docs/design/mockups/platform/backup-restore-desktop-mobile.png` (yes)
- Purpose: Backup destinations, schedules, restore points, verification, and retention
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Self-hosting Installation

- Priority: `P1`
- Area: Public
- Mockup: `docs/design/mockups/public/self-hosting-installation-desktop-mobile.png` (yes)
- Purpose: Docker Compose/Kubernetes/Helm installation and configuration guide
- Key components: PublicShell, PageHeader, SectionCard, MetricCard, CTAGroup, ResponsivePageContainer

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Architecture Overview

- Priority: `P1`
- Area: Public / Portfolio
- Route: /architecture
- Mockup: docs/design/mockups/public/architecture-overview.png
- Purpose: Explain the high-level structure of Open Ecosystem OS in a clear, approachable way.
- Audience: Visitors, recruiters, product-minded engineers, contributors.
- Focus:
  Product architecture, main layers, core modules, request/event flow, and design principles.

## Architecture & Infrastructure Blueprint
  
- Priority: `P1`
- Area: Technical / Developer Documentation
- Route: /architecture/infrastructure
- Mockup: docs/design/mockups/public/architecture-infrastructure-blueprint.png
- Purpose: Explain the actual implementation infrastructure, deployment model, runtime layers, observability stack, and technology decision areas.
- Audience: Technical interviewers, maintainers, platform/backend engineers, AI implementation workflow.
- Focus: Runtime architecture, Kubernetes, async workers, storage, queues, observability, security, deployment topology, and implementation priorities.

## Design System Page

- Priority: `P1`
- Area: Public
- Mockup: `docs/design/mockups/public/design-system-page-desktop-mobile.png` (yes)
- Purpose: Foundations, tokens, components, states, navigation, accessibility, and theme preview
- Key components: AdminShell, MetricCard, StatusChip, DataTable, ActivityFeed, RightInspectorPanel, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Forms and Approvals

- Priority: `P2`
- Area: Apps
- Mockup: `docs/design/mockups/apps/forms-approvals-desktop-mobile.png` (yes)
- Purpose: Create forms, collect submissions, and manage approval workflows
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Kanban / Task Board

- Priority: `P2`
- Area: Apps
- Mockup: `docs/design/mockups/apps/kanban-task-board-desktop-mobile.png` (yes)
- Purpose: Trello-like boards, task details, checklists, members, labels, and automations
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Admin Dashboard

- Priority: `P2`
- Area: Platform
- Mockup: `docs/design/mockups/platform/admin-dashboard-desktop-mobile.png` (yes)
- Purpose: Manage users, apps, health, security, quick actions, and operational summary
- Key components: AdminShell, MetricCard, StatusChip, DataTable, ActivityFeed, RightInspectorPanel, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Integrations

- Priority: `P2`
- Area: Platform
- Mockup: `docs/design/mockups/platform/integrations-desktop-mobile.png` (yes)
- Purpose: Connect infrastructure providers, AI providers, storage, messaging, auth, webhooks, and email
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Theme Builder

- Priority: `P2`
- Area: Platform
- Mockup: `docs/design/mockups/platform/theme-builder-desktop-mobile.png` (yes)
- Purpose: Create, validate, import/export, and apply personal/workspace themes
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Import / Export & Migration

- Priority: `P2`
- Area: Platform
- Mockup: `docs/design/mockups/platform/import-export-migration-flows-desktop-mobile.png` (yes)
- Purpose: Move data in and out through imports, exports, jobs, mappings, and migration history
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## API Explorer

- Priority: `P2`
- Area: Developer
- Mockup: `docs/design/mockups/developer/api-explorer-desktop-mobile.png` (yes)
- Purpose: Inspect endpoints, build requests, view schemas, and test API calls
- Key components: DeveloperShell, DataTable, StatusChip, CodeBlock, RightInspectorPanel, TabsNav, MobileBottomSheet

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Event Catalog

- Priority: `P2`
- Area: Developer
- Mockup: `docs/design/mockups/developer/event-catalog-desktop-mobile.png` (yes)
- Purpose: Browse events, producers, consumers, schemas, delivery status, and DLQ behavior
- Key components: DeveloperShell, DataTable, StatusChip, CodeBlock, RightInspectorPanel, TabsNav, MobileBottomSheet

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## ADR / Technical Decisions

- Priority: `P2`
- Area: Developer
- Mockup: `docs/design/mockups/developer/adr-technical-decisions-desktop-mobile.png` (yes)
- Purpose: Browse architecture decision records, consequences, alternatives, and related records
- Key components: DeveloperShell, DataTable, StatusChip, CodeBlock, RightInspectorPanel, TabsNav, MobileBottomSheet

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Case Study: Open Ecosystem Flows

- Priority: `P2`
- Area: Public
- Mockup: `docs/design/mockups/case-studies/case-study-open-ecosystem-flows-desktop-mobile.png` (yes)
- Purpose: Public technical case study for the automation engine
- Key components: AppShell, WorkflowCanvas, NodeCatalog, NodeConfigPanel, RunTimeline, StatusChip, DataTable, MobileBottomSheet

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Case Study: Open Pages

- Priority: `P2`
- Area: Public
- Mockup: `docs/design/mockups/case-studies/case-study-open-pages-desktop-mobile.png` (yes)
- Purpose: Public technical case study for the block workspace
- Key components: AppShell, PageTree, BlockEditor, SlashCommandMenu, CollaboratorPresence, RightInspectorPanel, MobileBottomSheet

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Case Study: Media/OCR Pipeline

- Priority: `P2`
- Area: Public
- Mockup: `docs/design/mockups/case-studies/case-study-media-ocr-pipeline-desktop-mobile.png` (yes)
- Purpose: Public technical case study for async OCR and indexing
- Key components: AppShell, UploadDropzone, FilePreview, FileTable, FileInspectorPanel, StatusChip, ProgressBar, MobileBottomSheet

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Marketplace

- Priority: `P3`
- Area: Developer
- Mockup: `docs/design/mockups/developer/marketplace-desktop-mobile.png` (yes)
- Purpose: Discover, install, update, and inspect apps/plugins/themes
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Plugin Developer Portal

- Priority: `P3`
- Area: Developer
- Mockup: `docs/design/mockups/developer/plugin-developer-portal-desktop-mobile.png` (yes)
- Purpose: Create, validate, sandbox, package, and submit plugins
- Key components: DeveloperShell, DataTable, StatusChip, CodeBlock, RightInspectorPanel, TabsNav, MobileBottomSheet

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Plugin Review / Admin Flow

- Priority: `P3`
- Area: Developer
- Mockup: `docs/design/mockups/developer/plugin-review-admin-flow-desktop-mobile.png` (yes)
- Purpose: Review plugin submissions, validate scopes, and approve/reject marketplace publication
- Key components: AppShell, WorkflowCanvas, NodeCatalog, NodeConfigPanel, RunTimeline, StatusChip, DataTable, MobileBottomSheet

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Community Forum

- Priority: `P3`
- Area: Developer
- Mockup: `docs/design/mockups/developer/community-forum-desktop-mobile.png` (yes)
- Purpose: Open-source discussion hub connected to GitHub, docs, roadmap, and proposals
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Billing / Subscriptions

- Priority: `P3`
- Area: Platform
- Mockup: `docs/design/mockups/platform/billing-subscriptions-desktop-mobile.png` (yes)
- Purpose: Optional hosted/support plans, invoices, usage, seats, AI credits, and support
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Music Player

- Priority: `P3`
- Area: Apps
- Mockup: `docs/design/mockups/apps/music-player-desktop-mobile.png` (yes)
- Purpose: Spotify-like self-hosted music library and player
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Video Player

- Priority: `P3`
- Area: Apps
- Mockup: `docs/design/mockups/apps/video-player-desktop-mobile.png` (yes)
- Purpose: Plex-like self-hosted video library and player
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## App Shell

- Priority: `P0`
- Area: Workspace
- Mockup: `docs/design/mockups/workspace/app-shell-desktop-mobile.png` (yes)
- Purpose: Shared authenticated layout foundation for workspace apps
- Key components: AppShell, Sidebar, TopCommandBar, MobileShell, MobileBottomNav, AppSwitcher

Desktop guidance:
- Use the workspace sidebar, top command/search bar, main content region, and optional right inspector panel.
- Keep navigation labels consistent with `docs/development/ROUTES.md`.

Mobile guidance:
- Use top header, concise app identity, stacked content, and bottom navigation.
- Move inspectors and contextual actions into bottom sheets.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the shell exposes protected navigation

## Command Palette

- Priority: `P0`
- Area: Workspace
- Mockup: `docs/design/mockups/workspace/command-palette-desktop-mobile.png` (yes)
- Purpose: Search, navigation, quick actions, and contextual commands
- Key components: CommandPalette, SearchInput, StatusChip, TabsNav, MobileBottomSheet

Desktop guidance:
- Keep command groups scannable and keyboard friendly.
- Show command type, destination, and permission context where relevant.

Mobile guidance:
- Present the command palette as a full-height sheet or focused search surface.
- Keep touch targets large and action labels explicit.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when commands are unavailable

## Navigation Patterns

- Priority: `P0`
- Area: Workspace
- Mockup: `docs/design/mockups/workspace/navigation-patterns-desktop-mobile.png` (yes)
- Purpose: Reference for sidebar, bottom nav, breadcrumbs, tabs, filters, and inspectors
- Key components: Sidebar, MobileBottomNav, Breadcrumbs, FilterChips, TabsNav, RightInspectorPanel

Desktop guidance:
- Prefer persistent navigation and visible hierarchy for internal apps.
- Keep dense controls predictable across apps.

Mobile guidance:
- Use top-level bottom navigation, horizontal chips/tabs, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when navigation exposes protected areas

## Dashboard Dark Theme

- Priority: `P1`
- Area: Workspace
- Mockup: `docs/design/mockups/workspace/dashboard-dark-desktop-mobile.png` (yes)
- Purpose: Dark theme reference for the workspace dashboard
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, MobileCardList

Desktop guidance:
- Use the same layout as the light dashboard and swap only semantic theme tokens.
- Preserve contrast, focus states, and status labels.

Mobile guidance:
- Preserve the light dashboard mobile hierarchy with dark theme tokens.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Landing Page

- Priority: `P1`
- Area: Public
- Mockup: `docs/design/mockups/public/landing-page-desktop-mobile.png` (yes)
- Purpose: Public entry point for the product, demo, architecture, and self-hosting path
- Key components: PublicShell, ResponsivePageContainer, CTAGroup, SectionCard, MetricCard

Desktop guidance:
- Use public navigation and a clear first-viewport product signal.
- Keep the hero tied to the actual ecosystem rather than generic marketing copy.

Mobile guidance:
- Keep calls to action visible and stack sections in a clear reading order.

States to consider:
- Normal
- Loading
- Error

## About Page

- Priority: `P2`
- Area: Public
- Mockup: `docs/design/mockups/public/about-page-desktop-mobile.png` (yes)
- Purpose: Explain the mission, principles, and ecosystem model
- Key components: PublicShell, ResponsivePageContainer, SectionCard, Timeline, CTAGroup

Desktop guidance:
- Organize long-form content into clear sections with restrained visual emphasis.

Mobile guidance:
- Stack sections and keep paragraphs short enough for scanning.

States to consider:
- Normal
- Loading
- Error

## Apps Overview

- Priority: `P2`
- Area: Public
- Mockup: `docs/design/mockups/public/apps-overview-desktop-mobile.png` (yes)
- Purpose: Explain available and planned ecosystem apps
- Key components: PublicShell, ResourceCard, StatusChip, FilterChips, ResponsivePageContainer

Desktop guidance:
- Separate MVP-ready apps from post-MVP and long-term apps using visible status labels.

Mobile guidance:
- Use stacked app cards with compact metadata and explicit status labels.

States to consider:
- Normal
- Loading
- Empty
- Error

## Roadmap

- Priority: `P2`
- Area: Public
- Mockup: `docs/design/mockups/public/roadmap-desktop-mobile.png` (yes)
- Purpose: Communicate phases, priorities, and delivery sequencing
- Key components: PublicShell, Timeline, StatusChip, SectionCard, FilterChips

Desktop guidance:
- Present roadmap phases with clear sequencing and priority labels.

Mobile guidance:
- Use timeline cards and avoid dense multi-column phase layouts.

States to consider:
- Normal
- Loading
- Empty
- Error

## Changelog

- Priority: `P2`
- Area: Public
- Mockup: `docs/design/mockups/public/changelog-desktop-mobile.png` (yes)
- Purpose: Track notable releases, changes, and architectural milestones
- Key components: PublicShell, Timeline, StatusChip, SectionCard, FilterChips

Desktop guidance:
- Keep entries grouped by release/date and label breaking or security-relevant changes.

Mobile guidance:
- Use stacked release cards and concise entry summaries.

States to consider:
- Normal
- Loading
- Empty
- Error

## App Management

- Priority: `P2`
- Area: Platform
- Mockup: `docs/design/mockups/platform/app-management-desktop-mobile.png` (yes)
- Purpose: Manage enabled apps, availability, compatibility, and workspace access
- Key components: AdminShell, AppManagementRow, StatusChip, DataTable, RightInspectorPanel, MobileCardList

Desktop guidance:
- Use tables for dense app metadata and a right inspector for selected app details.

Mobile guidance:
- Use app cards and bottom sheets for details and configuration.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when app management requires admin access

## Core User Journey Flowcharts

- Priority: `P2`
- Area: Flows
- Mockup: `docs/design/mockups/flows/core-user-journey-flowcharts.png` (yes)
- Purpose: End-to-end journeys for setup, invoice automation, collaboration, plugin publishing, and security response
- Key components: AppShell, WorkflowCanvas, NodeCatalog, NodeConfigPanel, RunTimeline, StatusChip, DataTable, MobileBottomSheet

Desktop guidance:
- Use the appropriate shell for the area.
- Preserve clear section hierarchy, action hierarchy, and right-side detail panels where shown.
- Prefer tables/grids for dense desktop data.

Mobile guidance:
- Use top header, search/filter row where relevant, stacked cards, sticky primary actions, and bottom sheets for detail panels.

States to consider:
- Normal
- Loading
- Empty
- Error
- Permission denied when the screen exposes protected data

## Open Ledger Dashboard

- Priority: `P2`
- Area: Apps / Finance
- Mockup: `docs/design/mockups/apps/open-ledger/open-ledger-dashboard-desktop-mobile.png` (yes)
- Purpose: Summarize private household finance health, receipt review status, budget rules, product-price opportunities, and recent transactions.
- Key components: AppShell, PageHeader, MetricCard, SectionCard, StatusChip, FinanceMetricCard, ReceiptReviewCard, ProductPriceRow, AiFinanceSummaryCard, MobileCardList

Desktop guidance:
- Use Open Ledger sub-navigation in the authenticated app shell.
- Show income, expenses, net balance, and savings rate as primary metrics.
- Surface receipt review and rule alerts without requiring navigation.

Mobile guidance:
- Prioritize add expense, add income, and scan receipt actions.
- Use a central scan action in bottom navigation.
- Stack metrics, chart, recent transactions, and active rule cards.

States to consider:
- Normal
- Loading
- Empty finance data
- OCR unavailable
- Permission denied for shared household data

## Open Ledger Transactions

- Priority: `P2`
- Area: Apps / Finance
- Mockup: `docs/design/mockups/apps/open-ledger/open-ledger-transactions-desktop-mobile.png` (yes)
- Purpose: Track, filter, edit, categorize, split, export, and inspect income and expense transactions.
- Key components: AppShell, DataTable, MobileCardList, TransactionDetailPanel, FilterChips, StatusChip, ExportButton

Desktop guidance:
- Use table layout with filters and a right-side detail panel.
- Include add expense, add income, and scan receipt actions.

Mobile guidance:
- Use card list and quick filters.
- Use bottom sheet for selected transaction details.

States to consider:
- Normal
- Empty transactions
- Loading
- Export failed
- Permission denied

## Open Ledger Receipts

- Priority: `P2`
- Area: Apps / Finance
- Mockup: `docs/design/mockups/apps/open-ledger/open-ledger-receipts-desktop-mobile.png` (yes)
- Purpose: Scan, upload, OCR, review, correct, and confirm receipts into transactions.
- Key components: AppShell, ReceiptPreviewCard, ReceiptReviewPanel, ReceiptLineItemsTable, ReceiptConfidenceBadge, StatusChip, MobileBottomSheet

Desktop guidance:
- Show receipt table and selected receipt review panel.
- Never save low-confidence parsed data without user review.

Mobile guidance:
- Use receipt cards and bottom-sheet review.
- Keep confirm/create transaction action prominent.

States to consider:
- Uploaded
- Processing
- Needs review
- Confirmed
- Failed OCR
- Re-run OCR

## Open Ledger Budgets & Rules

- Priority: `P2`
- Area: Apps / Finance
- Mockup: `docs/design/mockups/apps/open-ledger/open-ledger-budgets-rules-desktop-mobile.png` (yes)
- Purpose: Configure budgets, saving goals, spending limits, habits, and alerts.
- Key components: BudgetProgressRow, RuleStatusCard, RuleAlertCard, MetricCard, DataTable, MobileCardList

Desktop guidance:
- Show budgets by category and rules/habits side by side.
- Include trend vs budget chart and alerts/recommendations.

Mobile guidance:
- Use tabs for all, budgets, rules, and alerts.

States to consider:
- On track
- Near limit
- Exceeded
- At risk
- Completed

## Open Ledger Products & Prices

- Priority: `P2`
- Area: Apps / Finance
- Mockup: `docs/design/mockups/apps/open-ledger/open-ledger-products-prices-desktop-mobile.png` (yes)
- Purpose: Compare recurring products across stores, track unit prices, and identify savings opportunities.
- Key components: ProductPriceRow, StoreRankingList, UnitPriceBadge, MiniChart, AlertBanner, DataTable

Desktop guidance:
- Show product comparison table and selected product detail panel.
- Include price history, store ranking, category savings, and basket comparison.

Mobile guidance:
- Focus on top products, store ranking, and alerts.

States to consider:
- No tracked products
- Product alias needs review
- Price opportunity detected
- Insufficient data for comparison

## Open Ledger Reports

- Priority: `P2`
- Area: Apps / Finance
- Mockup: `docs/design/mockups/apps/open-ledger/open-ledger-reports-desktop-mobile.png` (yes)
- Purpose: Explain spending, income, savings, category, merchant, person, and product trends over time.
- Key components: FinanceReportChartCard, AiFinanceSummaryCard, MetricCard, FilterChips, ExportButton

Desktop guidance:
- Use multiple chart cards and AI insight panel.
- Include report presets and export actions.

Mobile guidance:
- Use report tabs and stacked charts.

States to consider:
- No data for selected period
- Report generating
- Export failed

## Open Ledger Settings

- Priority: `P2`
- Area: Apps / Finance
- Mockup: `docs/design/mockups/apps/open-ledger/open-ledger-settings-desktop-mobile.png` (yes)
- Purpose: Configure household, categories, payment methods, recurring items, OCR/AI parsing, budget defaults, notifications, privacy, and localization.
- Key components: FinanceSettingsGroup, SectionCard, StatusChip, Toggle, Select, ConfirmationDialog

Desktop guidance:
- Use categorized settings cards with a privacy-first side panel.

Mobile guidance:
- Use accordion sections and sticky save action.

States to consider:
- Unsaved changes
- Export in progress
- Delete finance data confirmation
- Local AI/OCR unavailable