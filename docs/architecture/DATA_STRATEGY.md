# Data Strategy

## Guiding principle

Use the simplest storage model that honestly matches the data shape and access patterns.

Do not add databases only to look modern. Every storage technology must have a clear responsibility.

## Storage responsibilities

### PostgreSQL

Primary source of truth.

Use for:

- users
- workspaces
- roles
- permissions
- file metadata
- workflow executions
- notifications
- audit logs
- settings
- integration metadata
- API keys metadata
- backup metadata

### PostgreSQL JSONB

Use for flexible but still transactional structures:

- Open Pages block trees
- workflow definitions
- form schemas
- theme definitions
- plugin manifests
- AI extraction schemas
- imported metadata

This is the initial recommendation instead of MongoDB because it keeps the MVP simpler while still demonstrating flexible document modeling.

### MongoDB, optional later

Consider only if flexible document workloads grow large or become operationally independent.

Candidates:

- high-volume raw OCR/AI extraction documents
- page/block version snapshots
- plugin metadata catalog
- large workflow definitions/history

### MinIO / S3-compatible object storage

Use for binary and large assets:

- uploaded files
- PDFs
- images
- audio/video
- generated thumbnails
- OCR output artifacts
- exports
- backups
- plugin packages

### Redis

Use for transient operational state:

- idempotency keys
- rate limits
- short-lived locks
- worker coordination
- cache
- session state, if needed
- presence later

### Meilisearch

Use for search indexing:

- file names
- OCR text
- Open Pages
- Knowledge items
- forms/submissions
- events/catalog docs
- public docs

## Data ownership

Users must be able to export:

- files
- pages
- OCR text
- workflows
- audit logs, if permitted
- settings/themes
- workspace metadata

## Sensitive data rules

Do not log:

- file contents
- OCR extracted text by default
- AI prompts/responses by default
- secrets
- credentials
- raw tokens
- private user data unless explicitly required for audit/security

## PDF-specific data strategy

PDF editor should support:

- watermark application
- metadata removal
- AI-assisted redaction
- real permanent redaction, not visual overlays only
- version history
- original preservation
- audit record for each transformation

Generated versions should be stored as new objects in MinIO, with metadata in PostgreSQL.

## Test data tools

Add later under Developer/Test Data Tools:

- Test IBAN generator
- Test NIF generator
- fake invoice generator
- fake document generator

Generated values must be clearly labeled as fake/test data.
