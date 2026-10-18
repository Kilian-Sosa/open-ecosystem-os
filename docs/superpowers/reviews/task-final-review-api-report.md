# Final review remediation Task 3 — API report

Date: 2026-07-13

## Scope

Implemented I-07, I-08, I-09, and I-13 from the approved final-review amendment.

## Changes

- The heuristic now normalizes every valid explicitly labelled candidate before selection. Multiple distinct normalized values produce only the fixed `ambiguous_<field>` warning, retain no field, and require review. Equivalent normalized evidence is selected by geometry followed by stable page/reading-order tie breakers.
- Currency accepts valid explicit ISO codes plus only `€`, `£`, `US$`, `CA$`, `C$`, `AU$`, `A$`, and `NZ$`. The bare dollar marker produces value-free `ambiguous_currency`; no locale, supplier, or other value is used to infer currency.
- OCR lists now make one workspace-scoped result-presence projection and one workspace-scoped extraction-summary projection for all authorized listed job IDs. The projection SQL reads IDs/status metadata only; detail loaders remain unchanged.
- Generic workflow audit records add only `extractionId`, `status`, and `reviewRequired` after extraction. The audit lookup is metadata-only and does not load OCR text, fields, warnings, or provenance.

## TDD evidence

- RED: `apps\api\.\mvnw.cmd -q "-Dtest=HeuristicInvoiceExtractionAdapterTest" test` failed on conflicting totals, bare-dollar fabrication, and unsupported marker mappings before the adapter change.
- RED: `apps\api\.\mvnw.cmd -q "-Dtest=JdbcOcrResultRepositoryTest,JdbcInvoiceExtractionRepositoryTest,OcrJobQueryServiceTest,WorkflowExecutionServiceTest" test` failed on the missing metadata projection APIs and summary type before the repository/service change.
- GREEN: the focused API matrix passed after implementation.

## Verification

- `apps\api\.\mvnw.cmd -q "-Dtest=HeuristicInvoiceExtractionAdapterTest,JdbcOcrResultRepositoryTest,JdbcInvoiceExtractionRepositoryTest,OcrJobQueryServiceTest,OcrJobControllerTest,WorkflowExecutionServiceTest" test` — passed.
- `apps\api\.\mvnw.cmd -q test` — passed.
- `apps\api\.\mvnw.cmd -q spotless:check` — passed.
- `git diff --check` — passed.

The test runtime emitted existing H2/Flyway, RabbitMQ-unavailable test-profile, and Mockito dynamic-agent warnings; Maven exited successfully.

## Contract impact

No REST routes, event payloads, database migrations, worker contracts, or authorization behavior changed. List response summary fields remain `ocrResultPresent`, `extractionPresent`, `extractionStatus`, and `reviewRequired`; detail reads retain rich content behavior.
