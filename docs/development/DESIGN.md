---
name: Open Ecosystem OS
version: 0.1.0
description: Self-hosted open-source productivity ecosystem with a minimal, calm, AI-understandable interface.

colors:
  background: "#F8FAFC"
  surface: "#FFFFFF"
  surfaceMuted: "#F1F5F9"
  surfaceElevated: "#FFFFFF"
  border: "#E2E8F0"
  borderStrong: "#CBD5E1"
  textPrimary: "#0F172A"
  textSecondary: "#475569"
  textMuted: "#94A3B8"
  primary: "#7C3AED"
  primarySoft: "#EDE9FE"
  primaryHover: "#6D28D9"
  primaryForeground: "#FFFFFF"
  success: "#16A34A"
  successSoft: "#DCFCE7"
  warning: "#D97706"
  warningSoft: "#FEF3C7"
  danger: "#DC2626"
  dangerSoft: "#FEE2E2"
  info: "#2563EB"
  infoSoft: "#DBEAFE"

typography:
  sans: "Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif"
  mono: "JetBrains Mono, ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace"

layout:
  desktop:
    sidebarWidth: "280px"
    rightPanelWidth: "360px"
    maxWidth: "1440px"
  mobile:
    breakpoint: "768px"
    navigation: "top header + bottom navigation + bottom sheets"
---

# Design System Contract

This file is the design contract for humans and coding agents.

Every interface must be:

- minimal
- responsive
- accessible
- calm and technical
- explicit in hierarchy
- AI-understandable
- consistent with the Open Ecosystem OS shell

## Visual principles

### Minimal but not empty

Use whitespace, clear grouping, restrained accents, soft borders, and purposeful cards. Avoid decorative UI that does not clarify the task.

### AI-understandable structure

Prefer clear section titles, semantic labels, obvious status chips, predictable layout regions, and descriptive buttons. Avoid clever labels that hide meaning.

### Productive density

Desktop may use multi-panel layouts. Mobile should focus on quick actions, review, search, approvals, capture, and status monitoring.

### One ecosystem, many apps

Each app has its own purpose, but all apps must feel like part of the same system.

## App shell rules

### Desktop internal apps

Use:

- left sidebar
- top command/search bar
- main content area
- optional right-side inspector/details panel
- page header with title, description, and primary action

### Mobile internal apps

Use:

- top header
- search/action row
- stacked cards
- bottom navigation
- bottom sheets for details/actions
- sticky primary actions where appropriate

## Component rules

### PageHeader

Must include:

- title
- short subtitle
- primary action if relevant
- optional secondary action
- optional status/filter chips

### Cards

Cards use:

- white/light surface
- subtle border
- soft shadow
- 16-24px padding
- rounded corners
- clear title and content hierarchy

### Tables

Use tables on desktop for dense data. Convert rows into stacked cards on mobile.

Each table should include:

- column labels
- row actions
- status chips
- empty state

### Side panels

Use right-side panels for selected item details, previews, metadata, configuration, and contextual actions.

On mobile, convert side panels into bottom sheets.

### Status chips

Use status chips for:

- active
- disabled
- processing
- failed
- completed
- draft
- submitted
- approved
- rejected
- installed
- update available
- incompatible
- queued
- retrying

Never communicate state with color alone.

### AI surfaces

AI surfaces must show:

- prompt input
- source/evidence panel
- tool/action preview
- confirmation for destructive or external actions
- visible data-access context
- audit trail link where relevant

## Required states

For every major data-driven page, implement:

- loading
- empty
- error
- permission denied, where relevant
- normal state

## Accessibility requirements

- Use semantic HTML.
- Add aria-labels to icon-only buttons.
- Preserve keyboard navigation.
- Use visible focus states.
- Meet readable contrast.
- Do not rely on color alone for status.
- Prefer reduced motion for users who request it.

## Theming rules

Themes must be token-based.

Do not allow arbitrary CSS from users.

Use semantic tokens:

- background
- surface
- text-primary
- text-secondary
- border
- primary
- success
- warning
- danger

Future custom theme builder must validate contrast and export/import JSON.

## Do

- Reuse shared components.
- Use semantic design tokens.
- Design desktop and mobile behavior together.
- Include realistic data.
- Include status and edge states.
- Keep actions explicit.

## Don't

- Do not create one-off visual styles.
- Do not use random hardcoded colors.
- Do not hide important actions behind icons only.
- Do not create desktop-only experiences.
- Do not build pages without loading/empty/error states.
