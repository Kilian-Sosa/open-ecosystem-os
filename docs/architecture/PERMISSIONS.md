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

## Resources

- workspace
- user
- role
- file
- folder
- page
- workflow
- workflow_execution
- form
- form_submission
- task_board
- task
- notification
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
| Pages              | manage | manage | edit                 | edit               | view   | limited view | view audit only |
| Workflows          | manage | manage | edit/execute         | execute if allowed | view   | none         | view audit only |
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
