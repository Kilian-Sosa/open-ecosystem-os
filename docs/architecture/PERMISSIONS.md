# Permissions and RBAC

## Goal

Define one permission model for the whole ecosystem. Avoid each app inventing its own access rules.

## Roles

Initial roles:

- Instance Owner
- Workspace Admin
- Developer
- Editor
- Viewer
- Guest
- Auditor

## MVP seeded session

The MVP uses a seeded development session, not production authentication.

- Auth mode: `seeded_dev`
- Seeded actor: `usr_dev_placeholder`
- Seeded workspace: `wrk_dev_placeholder`
- Bootstrap endpoint: `GET /api/session/bootstrap`
- Request headers preserved for MVP API calls: `X-Actor-Id` and
  `X-Workspace-Id`

`identity_users`, `workspaces`, and `workspace_memberships` persist the seeded
actor, workspace, and roles. The API validates supplied actor/workspace headers
against active workspace membership. Missing headers fall back to the seeded
demo actor/workspace so the local MVP and invoice automation flow remain easy
to run.

This is explicitly non-production behavior. Full authentication should replace
the seeded header flow with real session/token handling while preserving the
workspace membership and permission model.

## Resources

- workspace
- user
- role
- file
- folder
- ocr_job
- page
- knowledge_item
- workflow
- workflow_execution
- form
- form_submission
- task_board
- task
- notification
- search_document
- demo_invoice_extraction
- audit_log
- integration
- api_key
- backup
- app
- plugin
- system_setting

## Actions

- view
- create
- edit
- delete
- share
- publish
- execute
- approve
- reject
- export
- configure
- manage
- restore
- revoke

## Suggested permission matrix

| Resource           | Owner  | Admin  | Developer            | Editor             | Viewer | Guest        | Auditor         |
| ------------------ | ------ | ------ | -------------------- | ------------------ | ------ | ------------ | --------------- |
| Workspace settings | manage | manage | view                 | none               | none   | none         | view            |
| Users/roles        | manage | manage | none                 | none               | none   | none         | view            |
| Files/folders      | manage | manage | edit                 | edit               | view   | limited view | view audit only |
| OCR jobs           | manage | manage | view                 | view               | view   | none         | view audit only |
| Pages              | manage | manage | edit                 | edit               | view   | limited view | view audit only |
| Workflows          | manage | manage | edit/execute         | execute if allowed | view   | none         | view audit only |
| Search documents   | manage | manage | view                 | view               | view   | none         | view audit only |
| Demo invoice data  | manage | manage | execute              | execute if allowed | view   | none         | view audit only |
| Integrations       | manage | manage | configure if allowed | none               | none   | none         | view            |
| API keys           | manage | manage | create own           | none               | none   | none         | view audit only |
| Audit logs         | view   | view   | limited own          | limited own        | none   | none         | view            |
| Backups            | manage | manage | none                 | none               | none   | none         | view            |

## Sharing model

Resources should support:

- private
- workspace-visible
- specific users/groups
- public link, optional and disabled by default

## AI permissions

AI assistant must inherit user permissions.

Rules:

- AI cannot access resources the user cannot access.
- AI must show sources used.
- Destructive actions require confirmation.
- AI tool actions must be audit logged.
- Admin/system actions require admin permissions.

## Plugin permissions

Later plugin system should require explicit scopes:

- read files
- write files
- read pages
- write pages
- execute workflows
- send notifications
- access external network
- access secrets/credentials

Plugins must never receive broad access by default.

## Open Ledger permissions

Open Ledger data is sensitive and must be protected by workspace, household, and role permissions.

Resources:

- `finance.transaction`
- `finance.receipt`
- `finance.budget`
- `finance.rule`
- `finance.product`
- `finance.report`
- `finance.settings`
- `finance.export`
- `finance.delete`

Actions:

- view
- create
- edit
- delete
- confirm
- export
- manage
- share

Suggested permissions:

| Role    | Permissions                                                                   |
| ------- | ----------------------------------------------------------------------------- |
| Owner   | Full finance access, export/delete data, manage household/settings            |
| Member  | Create/edit own transactions, view shared household finance data when enabled |
| Viewer  | Read-only access to selected reports/transactions                             |
| Auditor | View audit-safe finance events, not necessarily receipt contents              |

AI/OCR rules:

- AI categorization can suggest fields but must respect user/workspace access.
- Receipt text and parsed content must not be sent to external providers unless AI settings allow it.
- Finance exports and deletes require explicit confirmation and should create audit records.

## Media/OCR permissions

The MVP uses placeholder authentication and workspace scoping. Full RBAC should
map OCR job access to the source file permission:

- viewing OCR job status requires `file:view`
- viewing extracted OCR text requires `file:view` on the source file
- re-running or deleting OCR jobs later should require `file:edit` or `file:manage`
- auditors may view OCR audit metadata but not extracted text by default
- the OCR lifecycle projection inherits the same source-file and OCR-detail
  visibility; it does not grant a separate event or operations permission
- lifecycle rows expose sanitized diagnostic metadata only and never expose
  event payloads, storage keys, OCR text, workflow step input/output, or audit
  attributes
