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
- For critical workflows, introduce outbox pattern.

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
