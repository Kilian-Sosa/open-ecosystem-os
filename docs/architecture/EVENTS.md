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

#### `OcrRequested` v1

Produced by the Media/OCR module after consuming `FileUploaded` v1 for OCR-eligible
content types: `application/pdf`, `image/png`, and `image/jpeg`.

Payload:

```json
{
  "jobId": "ocr_123",
  "fileId": "file_123",
  "contentType": "application/pdf",
  "storageKey": "workspaces/wrk_123/drive/file_123/original",
  "attemptCount": 0,
  "maxAttempts": 3,
  "requestedAt": "2026-05-22T10:00:05Z"
}
```

Notes:

- `source` is `media`.
- `causationId` is the consumed `FileUploaded` event ID.
- `idempotencyKey` is `media:ocr:{jobId}:requested:v1`.
- Payload does not include filenames, file content, or OCR text.

#### `OcrStarted` v1

Produced by the OCR worker when it claims a queued job.

Payload:

```json
{
  "jobId": "ocr_123",
  "fileId": "file_123",
  "provider": "mock",
  "attemptCount": 1,
  "maxAttempts": 3,
  "startedAt": "2026-05-22T10:00:10Z"
}
```

#### `OcrCompleted` v1

Produced by the OCR worker after the provider returns extracted text and the job
is persisted as completed.

Payload:

```json
{
  "jobId": "ocr_123",
  "fileId": "file_123",
  "provider": "mock",
  "attemptCount": 1,
  "extractedTextLength": 2048,
  "completedAt": "2026-05-22T10:00:20Z"
}
```

Notes:

- Extracted text is stored in PostgreSQL for job detail preview.
- Event payloads intentionally include only text length, never raw OCR text.

#### `OcrFailed` v1

Produced by the OCR worker after the final configured attempt fails.

Payload:

```json
{
  "jobId": "ocr_123",
  "fileId": "file_123",
  "provider": "mock",
  "attemptCount": 3,
  "maxAttempts": 3,
  "errorCode": "MOCK_OCR_FAILED",
  "errorMessage": "Mock OCR provider failed",
  "failedAt": "2026-05-22T10:02:20Z"
}
```

Notes:

- Error details must be sanitized and must not include file content or OCR text.
- The MVP retry policy uses `OCR_MAX_ATTEMPTS` and `OCR_RETRY_DELAY_SECONDS`.
- RabbitMQ primary queues dead-letter to retry queues. Final worker failures are
  also published to the OCR requested DLQ for inspection.

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

#### Flows MVP execution events v1

The first automation engine MVP persists workflow state in PostgreSQL and emits
execution events through the transactional outbox. Workflow definitions are
stored as JSON and support:

- trigger type `manual`
- event trigger `OcrCompleted`
- ordered actions `create_notification`, `create_audit_entry`, and
  `create_knowledge_item_placeholder`

Execution event payloads intentionally carry workflow, version, execution,
step, trigger, status, retry, and failure metadata. They must not include raw
OCR text, document content, AI prompts, or user file content.

`WorkflowTriggered` v1 payload:

```json
{
  "workflowId": "flow_123",
  "workflowVersionId": "wfv_123",
  "executionId": "wfe_123",
  "triggerType": "event",
  "sourceEventType": "OcrCompleted",
  "sourceEventId": "evt_ocr_completed",
  "triggeredAt": "2026-05-23T10:00:00Z"
}
```

`WorkflowExecutionStarted`, `WorkflowStepCompleted`,
`WorkflowStepFailed`, `WorkflowExecutionCompleted`, and
`WorkflowExecutionFailed` follow the same envelope and include the relevant
workflow/execution IDs, step key/action type where applicable, timestamps, and
sanitized failure reason for failed records.

### Search

- `IndexingRequested`
- `IndexingCompleted`
- `IndexingFailed`

### Notifications

- `NotificationCreated`
- `NotificationSent`
- `NotificationRead`
- `NotificationFailed`

#### `NotificationCreated` v1

Produced by the Notifications module when a workflow action creates an MVP
notification record.

Payload:

```json
{
  "notificationId": "ntf_123",
  "title": "OCR completed for invoice file",
  "severity": "info",
  "sourceType": "workflow_execution",
  "sourceId": "wfe_123",
  "createdAt": "2026-05-23T10:00:01Z"
}
```

Notification payloads should remain metadata-oriented and avoid embedding
document content or OCR text.

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
