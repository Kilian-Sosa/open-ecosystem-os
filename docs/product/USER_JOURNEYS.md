# User Journeys

**Milestone note:** The journeys below originated in the broader product
blueprint. The current, user-approved release boundary is defined by the
[MVP Core/Complete design](../superpowers/specs/2026-10-18-mvp-milestones-design.md).
First-time **public** user setup belongs to MVP Complete; the Core is a
privately operated Drive-first document tool.

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

## Journey 2 — Document OCR and reviewed invoice extraction (MVP Core target)

```txt
Upload or select invoice PDF/image in Drive
  -> FileUploaded event emitted
  -> choose Extract invoice data (built-in action)
  -> OCR job queued or existing completed result reused
  -> OCR worker decrypts and processes the eligible PDF/image with Tesseract when required
  -> OcrCompleted event emitted
  -> local heuristic extracts draft fields and warnings
  -> user reviews, corrects and explicitly confirms the draft
  -> confirmed structured invoice fields are indexed for search
  -> filename is searchable after upload, raw OCR text after OCR completes
  -> safe notification and audit metadata preserve lifecycle traceability
```

Purpose: flagship useful personal workflow and technical demonstration. The
pipeline currently **does not support editing or confirming drafts**, and the
seeded workflow currently requests indexing before confirmation. This
diagram is the **target**, not a statement that those steps already work.
An independent OCR-only action and ordered image-to-PDF action are also part
of Core; see the [modular design](../superpowers/specs/2026-10-18-mvp-milestones-design.md).
Kanban task and approval-request creation remain outside Core.

## Journey 3 — Collaborative project workspace (post-MVP)

```txt
Create Open Pages project page
  -> Add checklist and notes
  -> Embed files/tasks
  -> Mention another user
  -> Notification created
  -> Activity log updated
  -> Page indexed for search
```

An initial post-MVP version can be collaboration-lite without real-time
editing.

## Journey 4 — Security incident response (MVP Complete hardening and later)

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
