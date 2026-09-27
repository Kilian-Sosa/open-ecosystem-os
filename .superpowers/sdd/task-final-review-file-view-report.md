# CV-04 source-file `file:view` remediation report

## Root-cause findings before production changes

1. `docs/architecture/PERMISSIONS.md` requires `file:view` on the source file for OCR status,
   extracted text, structured OCR words, extraction fields, warnings, confidence, provenance,
   and lifecycle detail. It explicitly does not define workspace membership as universal file
   visibility. The documented sharing model allows private, workspace-visible, and specifically
   shared resources.
2. The only executable authentication path is `PlaceholderAuthenticationContext`. It validates
   an active `workspace_memberships` row and returns `AuthenticatedPrincipal`; it does not
   evaluate any resource/action permission. No existing permission evaluator, policy, port, ACL,
   grant, or sharing service was found in the API sources or tests.
3. `drive_files` persists `owner_id`, which identifies the owner of a private source file.
   `workspace_memberships` persists workspace roles. No table or repository persists file
   visibility, user/group shares, or grants. Consequently, explicitly shared-user access is not
   supported by the current persisted model and will not be improvised with a migration in this
   bounded remediation.
4. `OcrJobQueryService.getJob` first resolves the authenticated principal, then performs a
   workspace-scoped OCR job lookup and a matching workspace-scoped Drive-file lookup. The latter
   confirms only source existence. It then loads `JdbcOcrResultRepository` and
   `JdbcInvoiceExtractionRepository` before serializing text, structured data, warnings,
   confidence, and provenance. This is the CV-04 disclosure path.
   `DriveUploadService.listFiles` and `getFile` likewise resolved Drive files by workspace only,
   exposing a private file's name and metadata to an unshared member.

## Approved minimal boundary

Add one reusable source-file `file:view` decision in the existing `common.security` boundary.
For the currently persisted private-file model, it grants only when the authenticated actor is
the Drive file owner in the same workspace. It will run after the existing workspace-scoped OCR
job/source lookup and before any OCR or extraction repository call. A missing, foreign-workspace,
or denied source produces the existing non-enumerating `NOT_FOUND` response.
The same decision protects Drive list/detail mapping, so source-file access does not retain a
parallel workspace-membership path.

No schema change is authorized: the architectural gap is the absence of persisted
workspace-visible or specific-user/group sharing data. Explicitly shared-user coverage is not
applicable until that model is designed and persisted; this remediation must not replace the
missing model with workspace membership.

## Verification plan

- Decision tests: private owner allow; unshared same-workspace user deny; workspace mismatch deny.
- Query-service tests: denied, foreign-workspace, and missing-source paths invoke neither the OCR
  result nor invoice-extraction repositories; authorized legacy `extractedText` fallback remains
  available.
- Controller tests: owner detail succeeds; an unshared same-workspace actor receives the same
  non-enumerating 404 as foreign-workspace and missing-source requests; list JSON remains
  metadata-only.

## Implementation and verification

- Added `ResourcePermissionDecision` in `common.security`; it makes the persisted private-file
  `file:view` decision from matching workspace and `drive_files.owner_id`.
- Applied that decision before Drive list/detail response mapping and before OCR detail result or
  extraction reads. The denied OCR path returns the existing `NOT_FOUND` error and never invokes
  either protected repository.
- TDD evidence: the new decision test first failed because the boundary was absent; the query
  service test next failed because the boundary was not injected; the Drive controller test then
  failed because private files were still listed. Each passed after the smallest corresponding
  change.
- Passed focused tests:
  `ResourcePermissionDecisionTest`, `OcrJobQueryServiceTest`, `OcrJobControllerTest`, and
  `DriveFileControllerTest`.
- Passed full API test suite: `apps\\api\\mvnw.cmd -q test`.
- Passed formatting verification: `apps\\api\\mvnw.cmd -q spotless:check`.
