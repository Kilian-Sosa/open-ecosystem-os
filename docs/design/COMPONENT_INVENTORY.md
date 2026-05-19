# Component Inventory

This file defines the reusable UI building blocks Codex should use before creating new components.

## Layout components

- `PublicShell`: public marketing/docs/case-study layout.
- `AppShell`: authenticated workspace layout with sidebar, top command bar, and main content.
- `AdminShell`: admin/platform variant of the authenticated shell.
- `DeveloperShell`: developer/API/events/plugin documentation variant.
- `MobileShell`: mobile header, content stack, and bottom navigation.
- `ResponsivePageContainer`: consistent page padding, max width, and responsive grid behavior.
- `RightInspectorPanel`: desktop contextual detail panel.
- `MobileBottomSheet`: mobile replacement for right inspector panels and modal details.

## Navigation components

- `Sidebar`
- `TopCommandBar`
- `MobileBottomNav`
- `Breadcrumbs`
- `AppSwitcher`
- `CommandPalette`
- `FilterChips`
- `TabsNav`

## Data display components

- `MetricCard`
- `StatusChip`
- `SectionCard`
- `ResourceCard`
- `DataTable`
- `MobileCardList`
- `Timeline`
- `ActivityFeed`
- `ProgressBar`
- `UsageBar`
- `MiniChart`

## Feedback/state components

- `EmptyState`
- `ErrorState`
- `LoadingState`
- `SkeletonTable`
- `Toast`
- `AlertBanner`
- `ConfirmationDialog`
- `PermissionDeniedState`

## Theme components

- `ThemeProvider`: resolves `light`, `dark`, and `system` preferences, currently persisted browser-local under `open-ecosystem-os:theme-preference`.
- `ThemeSwitcher`: accessible segmented theme control using Sun, Moon, and Monitor icons.
- `ThemeScript`: pre-hydration root theme script to avoid light/dark first-paint flash.

## File/media components

- `UploadDropzone`
- `FileCard`
- `FileTableRow`
- `FilePreview`
- `FileInspectorPanel`
- `PermissionModal`
- `VersionHistoryPanel`

## AI components

- `AssistantChat`
- `PromptInput`
- `SourceCard`
- `ToolActionPreview`
- `AiConfirmationCard`
- `AiResultCard`
- `AiPermissionNotice`

## Workflow components

- `WorkflowNode`
- `WorkflowCanvas`
- `WorkflowStepList`
- `NodeCatalog`
- `NodeConfigPanel`
- `RunTimeline`
- `ExecutionLog`
- `DeadLetterQueueItem`
- `WorkflowTemplateCard`

## Open Pages components

- `PageTree`
- `BlockEditor`
- `SlashCommandMenu`
- `BlockToolbar`
- `CollaboratorPresence`
- `CommentThread`
- `DatabaseView`
- `EmbeddedResourceCard`

## Platform/admin components

- `HealthCard`
- `ServiceStatusRow`
- `AuditLogRow`
- `SecurityEventCard`
- `BackupStatusCard`
- `IntegrationStatusRow`
- `AppManagementRow`
- `PluginReviewPanel`

## Rules

If a screen needs a UI pattern not listed here, must either:

1. reuse the nearest existing component, or
2. propose the new component in its summary and update this inventory in the same task.

## Open Ledger components

Finance-specific components should reuse generic cards, tables, filters, bottom sheets, and status chips before creating app-specific variants.

Suggested components:

- `FinanceMetricCard`
- `TransactionTypeIcon`
- `TransactionTable`
- `TransactionCard`
- `TransactionDetailPanel`
- `ReceiptPreviewCard`
- `ReceiptReviewPanel`
- `ReceiptConfidenceBadge`
- `ReceiptLineItemsTable`
- `BudgetProgressRow`
- `RuleStatusCard`
- `RuleAlertCard`
- `ProductPriceRow`
- `StoreRankingList`
- `UnitPriceBadge`
- `FinanceReportChartCard`
- `AiFinanceSummaryCard`
- `FinanceSettingsGroup`

Status chips:

- `income`
- `expense`
- `needs review`
- `confirmed`
- `processing`
- `failed`
- `on track`
- `near limit`
- `exceeded`
- `at risk`
- `cheaper elsewhere`
- `price up`
- `stable`
