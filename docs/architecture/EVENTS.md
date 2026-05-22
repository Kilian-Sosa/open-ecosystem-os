# Event Model

## Purpose

Events connect modules without tight coupling. They are also the backbone of automations, auditability, indexing, notifications, and operational flows.

## Event envelope

All events should use a shared envelope:

```json
{
  "eventId": "evt_01H...",
  "eventType": "FileUploaded",
  "version": 1,
  "occurredAt": "2026-05-13T10:00:00Z",
  "workspaceId": "wrk_123",
  "actorId": "usr_123",
  "correlationId": "corr_123",
  "causationId": "evt_previous_optional",
  "source": "drive",
  "idempotencyKey": "drive:file_123:uploaded:v1",
  "payload": {}
}
```

## Event naming

Use past-tense business facts:

Good:

- `FileUploaded`
- `OcrCompleted`
- `WorkflowExecutionFailed`
- `NotificationCreated`

Avoid command-like event names:

- `UploadFile`
- `RunOcr`
- `SendNotification`

Commands request action. Events record that something happened.

## Initial event catalog

### Drive

- `FileUploaded`
- `FileMoved`
- `FileTagged`
- `FileDeleted`
- `FileVersionCreated`
- `FileShared`

#### `FileUploaded` v1

Produced by the Drive API through the transactional outbox in the same PostgreSQL transaction that persists file metadata and the upload audit record.

Payload:

```json
{
  "fileId": "file_123",
  "contentType": "application/pdf",
  "sizeBytes": 1048576,
  "checksumSha256": "hex_sha256_of_original_bytes",
  "storageKey": "workspaces/wrk_123/drive/file_123/original",
  "encryptionAlgorithm": "AES-256-GCM",
  "encryptionKeyId": "key_2026_05",
  "contentIv": "base64_iv",
  "uploadedAt": "2026-05-13T10:00:00Z"
}
```

Notes:

- `source` is `drive`.
- `version` is `1`.
- `idempotencyKey` is `drive:{fileId}:uploaded:v1`.
- The payload intentionally excludes the plaintext filename. File names are encrypted in PostgreSQL and file content is encrypted before writing to MinIO/S3-compatible object storage.
- Consumers must treat `storageKey` as a private object pointer, not a downloadable URL.

### PDF

- `PdfWatermarkRequested`
- `PdfWatermarkApplied`
- `PdfMetadataRemovalRequested`
- `PdfMetadataRemoved`
- `PdfRedactionSuggested`
- `PdfRedactionApplied`
- `PdfExported`

### Media/OCR

- `OcrRequested`
- `OcrStarted`
- `OcrCompleted`
- `OcrFailed`
- `MediaIndexed`

### Open Pages

- `PageCreated`
- `PageUpdated`
- `PageArchived`
- `CommentAdded`
- `UserMentioned`
- `PageIndexed`

### Open Ecosystem Flows

- `WorkflowCreated`
- `WorkflowPublished`
- `WorkflowPaused`
- `WorkflowTriggered`
- `WorkflowExecutionStarted`
- `WorkflowStepCompleted`
- `WorkflowStepFailed`
- `WorkflowExecutionCompleted`
- `WorkflowExecutionFailed`
- `WorkflowExecutionRetried`
- `WorkflowDeadLettered`

### Search

- `IndexingRequested`
- `IndexingCompleted`
- `IndexingFailed`

### Notifications

- `NotificationCreated`
- `NotificationSent`
- `NotificationRead`
- `NotificationFailed`

### Security/Audit

- `SecurityAlertRaised`
- `SessionRevoked`
- `ApiKeyCreated`
- `ApiKeyRevoked`
- `PermissionDenied`

### Backups

- `BackupStarted`
- `BackupCompleted`
- `BackupFailed`
- `RestoreStarted`
- `RestoreCompleted`
- `RestoreFailed`

## Event reliability

For MVP:

- Start with transactional persistence of business state and queue publish where acceptable.
- Critical workflows such as Drive upload use the outbox pattern from the first slice.

Target outbox flow:

```txt
1. Write domain state and outbox event in same DB transaction
2. Outbox publisher reads unpublished events
3. Publisher publishes to broker
4. Publisher marks outbox row as published
5. Consumers process idempotently
```

## Consumer rules

Every consumer should:

- validate event version
- use idempotency key
- write processing result
- fail clearly
- retry where safe
- dead-letter after max attempts
- include correlation ID in logs

## Audit vs domain events

Domain events are used for system behavior.

Audit records are user/security traceability records.

Do not rely only on broker messages as audit history. Persist audit records in PostgreSQL.

## Open Ledger events

Finance events should follow the standard event envelope and be emitted through the outbox pattern.

Core events:

```txt
FinanceTransactionCreated
FinanceTransactionUpdated
FinanceTransactionDeleted
ReceiptUploaded
ReceiptOcrCompleted
ReceiptParsingSuggested
ReceiptConfirmed
ReceiptRejected
BudgetCreated
BudgetUpdated
BudgetExceeded
FinanceRuleCreated
FinanceRuleUpdated
FinanceRuleEvaluated
FinanceRuleViolated
ProductPriceObserved
ProductAliasMerged
FinanceReportGenerated
FinanceDataExported
FinanceDataDeleted
```

Example `ReceiptConfirmed` payload:

```json
{
  "receiptId": "receipt_123",
  "transactionId": "txn_456",
  "workspaceId": "wrk_123",
  "merchant": "Mercadona",
  "totalAmount": 78.32,
  "currency": "EUR",
  "categoryId": "cat_groceries",
  "personId": "person_ana",
  "lineItemCount": 6,
  "ocrConfidence": 0.96
}
```

Consumers:

- Search indexer indexes transactions and receipt text
- Notification service sends budget/rule alerts and review reminders
- Audit service records sensitive finance changes
- Open Ecosystem Flows triggers receipt and budget workflows
- Reports module updates cached snapshots
