# Testing Strategy

## Goal

Use tests to prove the flagship journey and protect architecture-critical behavior.

Do not chase coverage numbers before the architecture stabilizes. Prioritize meaningful tests.

## Frontend tests

Recommended:

- Vitest
- Testing Library
- axe/accessibility checks later

Deferred until the matching configuration and smoke suites exist:

- Playwright
- Storybook

Test:

- app shell renders
- mobile navigation works
- dashboard renders normal/loading/empty/error states
- upload flow starts
- notification center renders
- audit log filters work
- theme switching works

## Backend tests

Recommended:

- JUnit
- Spring Boot Test
- Testcontainers later
- WireMock or MockWebServer for external providers later

Test:

- domain rules
- permissions
- event envelope validation
- idempotency
- worker retry behavior
- audit log creation
- outbox publisher later

## E2E tests

First flagship E2E:

```txt
User uploads invoice PDF
  -> file appears in Drive
  -> OCR job is created
  -> OCR completes
  -> workflow execution starts
  -> notification is created
  -> audit log contains events
  -> search returns extracted content
```

The real OCR smoke uses `apps/worker/src/test/resources/fixtures/fake-scanned-invoice.png`. It is a clearly labelled synthetic, incomplete invoice image and is never runtime seed data. The Compose smoke uploads it through Drive, verifies the worker's containerized Tesseract binary and English language data, then asserts persisted structured words, observed fields, and the required review state. It does not accept a runtime mock provider.

## Visual/system tests

Later:

- Storybook visual regression
- Playwright screenshots
- responsive layout checks
- dark/light theme snapshots

## Test data

Use seeded fake data only:

- fake users
- fake invoices
- fake IBAN/NIF values
- fake OCR text
- fake workflows
- fake notifications
- clearly labelled synthetic scanned-invoice fixtures

Never commit real personal documents.
