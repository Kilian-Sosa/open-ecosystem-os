# Testing Strategy

## Goal

Use tests to prove the flagship journey and protect architecture-critical behavior.

Do not chase coverage numbers before the architecture stabilizes. Prioritize meaningful tests.

## Frontend tests

Recommended:

- Vitest
- Testing Library
- Playwright
- Storybook
- axe/accessibility checks later

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

Never commit real personal documents.
