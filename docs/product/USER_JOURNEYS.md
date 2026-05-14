# User Journeys

## Journey 1 — First-time self-hosted setup

```txt
Landing page
  -> Self-hosting installation page
  -> Create first admin
  -> Configure storage
  -> Configure email/notifications
  -> Configure AI provider or mock AI
  -> Enable core apps
  -> Check system status
  -> Enter workspace dashboard
```

Purpose: prove the product can be installed and understood from zero.

## Journey 2 — Invoice automation

```txt
Upload invoice PDF to Drive
  -> FileUploaded event emitted
  -> OCR job queued
  -> OCR worker processes document
  -> OcrCompleted event emitted
  -> Open Ecosystem Flows matches workflow trigger
  -> Extract invoice fields using AI/mock extractor
  -> Create Open Pages/Knowledge entry
  -> Send review notification
  -> Audit log records full chain
```

Purpose: flagship technical and product demo.

## Journey 3 — Collaborative project workspace

```txt
Create Open Pages project page
  -> Add checklist and notes
  -> Embed files/tasks
  -> Mention another user
  -> Notification created
  -> Activity log updated
  -> Page indexed for search
```

MVP version can be collaboration-lite without real-time editing.

## Journey 4 — Security incident response

```txt
Suspicious login or API key event
  -> Security notification
  -> Admin opens security settings
  -> Admin reviews active sessions/API keys
  -> Admin revokes session/key
  -> Audit log records action
  -> System status remains healthy
```

Purpose: demonstrate trust and operational maturity.

## Journey 5 — Plugin lifecycle, later

```txt
Developer creates plugin
  -> Defines manifest and permissions
  -> Runs validation sandbox
  -> Submits to marketplace
  -> Admin reviews permissions/security
  -> Plugin approved
  -> Plugin appears in Marketplace
```

Not MVP, but useful for long-term extensibility.
