# Real OCR Final Review Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Do not dispatch subagents unless the user later explicitly changes the no-subagent instruction. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remediate every Important real-OCR review finding, close the worker/API/web/runtime verification gaps, and preserve the completed real-OCR contracts while enforcing the approved source-file `file:view` authorization gate.

**Architecture:** Move all untrusted PDF loading, native extraction, raster analysis, and rendering into a lightweight helper executable packaged inside the existing worker image. The Spring worker owns document/page deadlines, process termination, output/word/pixel/temp limits, claim invariants, persistence, and sanitized failures; the API and web receive focused correctness, projection, polling, and accessibility changes without route, event, or schema changes.

**Tech Stack:** Java 25, Spring Boot 4, Maven, Apache PDFBox 3.0.4, Tesseract CLI, PostgreSQL 16/Testcontainers, Docker Compose, Kubernetes/Kustomize, Next.js 16, React 19, TanStack Query, Vitest/Testing Library, GitHub Actions, Trivy.

## Global Constraints

- Work only on `feat/real-ocr-extraction`; run `git status --short --branch` before implementation and do not overwrite the existing untracked `.superpowers/` or `docs/superpowers/plans/` content.
- Treat `docs/superpowers/specs/2026-07-13-real-ocr-final-review-amendment.md` as the approved remediation requirement.
- Do not rewrite Flyway V1-V8 and do not add a migration for claim expiry.
- Do not add a heartbeat or renewable claim lease; enforce `staleProcessingTimeout > documentTimeout + persistenceCleanupMargin` at startup.
- Keep `OcrStarted`, `OcrCompleted`, `OcrFailed`, workflow, indexing, and notification event versions and payloads unchanged.
- Keep the existing OCR/extraction tables, REST routes, nested detail response, metadata-only list response, workflow step order, and duplicate-delivery fences.
- A PDF page persists either native words or Tesseract words, never a merge of both.
- Keep OCR text, helper JSON, TSV, values, filenames, paths, and diagnostics out of events, logs, notifications, and audit attributes.
- Preserve V7/V8 idempotency and workspace lineage. Fake invoice data remains test-only.
- Do not weaken `docs/architecture/PERMISSIONS.md`. CV-04 requires the approved source-file
  `file:view` decision and remains merge-blocking until it is implemented and tested.
- Preserve historical verification reports. Create a new remediation verification report instead of editing old claims.
- Use PowerShell/CMD commands locally. Run POSIX syntax/runtime checks inside Linux or Linux CI.

---

### Task 1: PDF isolation, native extraction, and claim-budget correctness

**Findings:** I-01, I-02, I-03, I-04

**Files:**

- Create: `apps/worker/pdf-helper/pom.xml`
- Create: `apps/worker/pdf-helper/src/main/java/com/openecosystem/os/pdfhelper/PdfHelperMain.java`
- Create: `apps/worker/pdf-helper/src/main/java/com/openecosystem/os/pdfhelper/PdfHelperRequest.java`
- Create: `apps/worker/pdf-helper/src/main/java/com/openecosystem/os/pdfhelper/PdfHelperResponse.java`
- Create: `apps/worker/pdf-helper/src/main/java/com/openecosystem/os/pdfhelper/PdfPageAnalyzer.java`
- Create: `apps/worker/pdf-helper/src/main/java/com/openecosystem/os/pdfhelper/NativePdfWordExtractor.java`
- Create: `apps/worker/pdf-helper/src/main/java/com/openecosystem/os/pdfhelper/RasterCoverageAnalyzer.java`
- Test: `apps/worker/pdf-helper/src/test/java/com/openecosystem/os/pdfhelper/PdfPageAnalyzerTest.java`
- Test fixture: `apps/worker/pdf-helper/src/test/resources/fixtures/born-digital-multiline.pdf`
- Test fixture: `apps/worker/pdf-helper/src/test/resources/fixtures/scanned-with-footer.pdf`
- Test fixture: `apps/worker/pdf-helper/src/test/resources/fixtures/partial-native-overlay.pdf`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/OcrExecutionDeadline.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/OcrExecutionBudgetValidator.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/PdfHelperClient.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/PdfHelperPage.java`
- Modify: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/OcrProvider.java`
- Modify: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/OcrJobProcessor.java`
- Modify: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/TesseractOcrProvider.java`
- Modify: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/BoundedProcessRunner.java`
- Modify: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/WorkerOcrProperties.java`
- Modify: `apps/worker/pom.xml`
- Test: `apps/worker/src/test/java/com/openecosystem/os/worker/ocr/WorkerOcrPropertiesTest.java`
- Test: `apps/worker/src/test/java/com/openecosystem/os/worker/ocr/BoundedProcessRunnerTest.java`
- Test: `apps/worker/src/test/java/com/openecosystem/os/worker/ocr/TesseractOcrProviderTest.java`
- Test: `apps/worker/src/test/java/com/openecosystem/os/worker/ocr/OcrJobProcessorTest.java`

**Interfaces:**

- `OcrProvider.extract(OcrJob job, OcrExecutionDeadline deadline): OcrDocumentResult` replaces the unbounded one-argument call.
- `OcrExecutionDeadline.start(Clock clock, Instant claimStartedAt, Duration documentTimeout)` creates the whole-document deadline; `child(Duration pageTimeout)` creates a page deadline capped by the document deadline.
- `PdfHelperClient.inspect(Path pdf, OcrExecutionDeadline deadline): int` returns only a positive page count.
- `PdfHelperClient.processPage(Path pdf, Path renderedOutput, int pageNumber, OcrExecutionDeadline pageDeadline): PdfHelperPage` returns protocol v1 data.
- `PdfHelperPage` returns exactly one decision: `NATIVE` with grouped words, or `TESSERACT` with the validated parent-owned rendered path.
- `PdfHelperResponse` contains the helper-side `PageDecision` enum and `NativeWord` record; `PdfHelperClient` validates and maps them into worker-side `PdfHelperPage`/`OcrWord` values.
- `BoundedProcessRunner.run(List<String> command, Duration timeout, int maxStdoutBytes, int maxStderrBytes)` caps helper/Tesseract stdout at 10 MiB and stderr at 4 KiB without retaining diagnostics.
- Helper defaults: native minimum 20 alphanumeric characters/3 usable tokens/2% malformed maximum; substantial-raster native minimum 80 alphanumeric characters/12 usable tokens/3 lines/20% vertical span; substantial raster is one image at 25% or aggregate at 40% of crop-box area.

- [ ] **Step 1: Write helper RED tests for source selection and native structure**

  Add fixture-driven tests that assert the exact amendment thresholds and structure:

  ```java
  @Test
  void scannedPageWithSubstantialRasterAndSparseFooterChoosesTesseract() {
    PdfHelperResponse page = analyzer.analyze(
        fixture("scanned-with-footer.pdf"), 1, temporaryRoot.resolve("page-1.png"));
    assertThat(page.decision()).isEqualTo(PageDecision.TESSERACT);
    assertThat(page.words()).isEmpty();
  }

  @Test
  void bornDigitalPagePreservesMultiWordLinesAndPixelBoxes() {
    PdfHelperResponse page = analyzer.analyze(
        fixture("born-digital-multiline.pdf"), 1, temporaryRoot.resolve("page-1.png"));
    assertThat(page.decision()).isEqualTo(PageDecision.NATIVE);
    assertThat(page.words()).extracting(NativeWord::text)
        .containsSequence("Invoice", "Number", "INV-2026-42");
    assertThat(page.words().stream().map(NativeWord::lineNumber).distinct()).hasSizeGreaterThan(1);
    assertThat(page.words()).allMatch(word -> word.boundingBox() != null);
  }
  ```

- [ ] **Step 2: Run helper tests and confirm the module/types are missing**

  Run from the repository root:

  ```powershell
  mvn -q -f apps\worker\pdf-helper\pom.xml test
  ```

  Expected: FAIL because the helper module and production classes do not exist.

- [ ] **Step 3: Implement the lightweight helper and versioned protocol**

  Use only PDFBox, Jackson, and JUnit/AssertJ in the helper POM. `PdfHelperMain` accepts fixed `inspect` and `page` subcommands, validates every numeric/path argument, writes one JSON response to stdout, and returns non-zero without printing source details on failure.

  Implement the core decision exactly:

  ```java
  boolean minimallyUsable =
      alphanumericCharacters >= 20 && usableTokens >= 3 && malformedRatio <= 0.02d;
  boolean completeBesideRaster =
      alphanumericCharacters >= 80
          && usableTokens >= 12
          && nonEmptyLines >= 3
          && nativeVerticalSpan >= 0.20d
          && malformedRatio <= 0.02d;
  boolean useNative = minimallyUsable && (!substantialRaster || completeBesideRaster);
  ```

  Use PDFBox position sorting and callbacks for block/paragraph/line boundaries. Convert word boxes to top-left render-DPI pixels with floor origins and ceil extents. For `TESSERACT`, omit native words and render only the requested page after checked pixel validation.

- [ ] **Step 4: Write worker RED tests for hard process boundaries and deadlines**

  Add controlled-clock/process tests proving inspection and each page have separate process starts; a blocked helper is forcibly terminated; Tesseract receives only the remaining page/document duration; timeout releases the permit and removes the source workspace.

  ```java
  assertThatThrownBy(() -> provider.extract(job(), deadlineAt("2026-07-13T10:00:01Z")))
      .isInstanceOf(OcrProviderException.class)
      .extracting(error -> ((OcrProviderException) error).code())
      .isEqualTo("OCR_DOCUMENT_TIMEOUT");
  assertThat(process.wasForciblyTerminated()).isTrue();
  assertThat(temporaryRoot).isEmptyDirectory();
  ```

- [ ] **Step 5: Run the focused worker RED tests**

  ```powershell
  apps\worker\mvnw.cmd -q "-Dtest=WorkerOcrPropertiesTest,BoundedProcessRunnerTest,TesseractOcrProviderTest,OcrJobProcessorTest" test
  ```

  Expected: FAIL on missing deadline/helper interfaces and missing startup validation.

- [ ] **Step 6: Implement parent-side deadlines, helper control, and process-tree termination**

  Start the deadline from the persisted claim timestamp in `OcrJobProcessor` and pass it through `OcrProvider`. Replace every PDFBox import/use in the Spring worker with `PdfHelperClient`. Build commands as literal argument lists:

  ```java
  List<String> command = List.of(
      properties.pdfHelperCommand(), "-jar", properties.pdfHelperJar(), "page",
      "--input", pdf.toString(), "--output", rendered.toString(),
      "--page", Integer.toString(pageNumber), "--dpi", Integer.toString(properties.renderDpi()),
      "--max-rendered-pixels", Long.toString(properties.maxRenderedPixels()),
      "--protocol-version", "1");
  ```

  In `BoundedProcessRunner`, cap stdout and stderr independently, terminate descendants
  before the parent on timeout/output overflow/interruption, wait only the existing bounded
  grace, then force-kill remaining handles. Never include command arguments or captured
  diagnostics in failures.

- [ ] **Step 7: Add and validate the claim invariant**

  Extend `WorkerOcrProperties` with `documentTimeout`, `persistenceCleanupMargin`, `pdfHelperCommand`, and `pdfHelperJar`, and align the record fallback for `staleProcessingTimeout` to 15 minutes. `OcrExecutionBudgetValidator` must reject equality as well as inversion:

  ```java
  if (properties.staleProcessingTimeout()
      .compareTo(properties.documentTimeout().plus(properties.persistenceCleanupMargin())) <= 0) {
    throw new IllegalStateException(
        "OCR stale processing timeout must exceed the document budget and cleanup margin");
  }
  ```

  Bind a Spring context using `application.yml` defaults and deployment-equivalent overrides. Assert `15m > 10m + 2m`, `60s <= 10m`, and rejection of `12m == 10m + 2m`.

- [ ] **Step 8: Add born-digital worker/API contract proof**

  Persist helper-native words in the existing `OcrWord` model and run the existing API extractor against the resulting page. Assert stable page/block/paragraph/line/word IDs, boxes, multi-word label matching, and extraction of invoice number/date/amount without invoking Tesseract.

- [ ] **Step 9: Run Task 1 verification**

  ```powershell
  mvn -q -f apps\worker\pdf-helper\pom.xml test
  apps\worker\mvnw.cmd -q "-Dtest=WorkerOcrPropertiesTest,BoundedProcessRunnerTest,TesseractOcrProviderTest,OcrJobProcessorTest" test
  apps\worker\mvnw.cmd -q spotless:check
  ```

  Expected: all commands exit 0; the worker main source contains no `org.apache.pdfbox` import.

- [ ] **Step 10: Commit the independently reviewable PDF/claim boundary**

  ```powershell
  git add -- apps/worker/pdf-helper apps/worker/pom.xml apps/worker/src/main apps/worker/src/test
  git commit -m "fix(ocr): isolate PDF processing and enforce claim budget"
  ```

---

### Task 2: Worker image, word, version, and cleanup bounds

**Findings:** I-05, I-06, I-12, M-01, M-02

**Files:**

- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/ImageMetadataValidator.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/TesseractVersionProbe.java`
- Create: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/OcrTemporaryWorkspaceCleaner.java`
- Modify: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/TesseractOcrProvider.java`
- Modify: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/TesseractTsvParser.java`
- Modify: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/WorkerOcrProperties.java`
- Modify: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/OcrSource.java`
- Modify: `apps/worker/src/main/java/com/openecosystem/os/worker/ocr/DriveOcrSourceReader.java`
- Modify: `apps/worker/src/main/java/com/openecosystem/os/worker/metrics/WorkerMetrics.java`
- Test: `apps/worker/src/test/java/com/openecosystem/os/worker/ocr/ImageMetadataValidatorTest.java`
- Test: `apps/worker/src/test/java/com/openecosystem/os/worker/ocr/TesseractVersionProbeTest.java`
- Test: `apps/worker/src/test/java/com/openecosystem/os/worker/ocr/TesseractTsvParserTest.java`
- Test: `apps/worker/src/test/java/com/openecosystem/os/worker/ocr/TesseractOcrProviderTest.java`
- Test: `apps/worker/src/test/java/com/openecosystem/os/worker/ocr/DriveOcrSourceReaderTest.java`

**Interfaces:**

- `ImageMetadataValidator.validate(Path path, String contentType, long maxPixels): ImageMetadataValidator.ImageDimensions` reads metadata without full decode; `ImageDimensions` is a nested record.
- `TesseractTsvParser(int maxBytes, int maxWordsPerPage)` rejects the next word before appending beyond the cap.
- `TesseractVersionProbe.version(): String` caches a normalized semantic version from a bounded startup probe.
- Defaults: 10,000 words/page, 100,000 words/document, 5-second version probe, 4 KiB probe output, three cleanup deletion attempts separated by 50 ms, and a startup scan of at most 100 direct `ocr-` directories older than 24 hours.
- Ciphertext maximum is checked `maxInputBytes + 16` for AES-GCM; plaintext remains capped at `maxInputBytes`.

- [ ] **Step 1: Write RED tests for image dimensions and word caps**

  Use PNG/JPEG headers with tiny file size but declared dimensions over the pixel limit. Assert Tesseract never starts. Add a valid TSV below the byte cap containing `maxWordsPerPage + 1` word rows and assert `OCR_WORD_LIMIT` before the extra append.

  ```java
  assertFailure(() -> validator.validate(bomb, "image/png", 20_000_000),
      "OCR_IMAGE_PIXEL_LIMIT");
  assertThat(processStarts).hasValue(0);
  assertFailure(() -> parser(1_024_000, 2).parse(threeWords, 1), "OCR_WORD_LIMIT");
  ```

- [ ] **Step 2: Write RED tests for version and cleanup behavior**

  Cover `tesseract 5.5.1`, suffix-bearing semantic versions, malformed output, timeout,
  output overflow, one transient delete failure followed by success, and three failed
  deletes producing one content-free metric with no path in the captured log.

- [ ] **Step 3: Write AES-GCM boundary RED tests**

  Add exact-limit plaintext, one-byte-over plaintext, truncated tag, and ciphertext above
  `checkedAdd(maxInputBytes, 16)` cases. Exact-limit valid plaintext must succeed; the other
  cases must return fixed sanitized codes and leave no temporary entry.

- [ ] **Step 4: Run the focused RED matrix**

  ```powershell
  apps\worker\mvnw.cmd -q "-Dtest=ImageMetadataValidatorTest,TesseractVersionProbeTest,TesseractTsvParserTest,TesseractOcrProviderTest,DriveOcrSourceReaderTest" test
  ```

  Expected: FAIL on missing classes, constructor parameters, and boundary behavior.

- [ ] **Step 5: Implement pre-decode image validation and both word caps**

  Select an `ImageReader` from the content type, read width/height from index 0, reject
  non-positive dimensions or format mismatch, and use `Math.multiplyExact`. Enforce the
  per-page cap in both helper response parsing and TSV parsing. Before adding each page to
  the document, use checked addition against `maxWordsPerDocument`; never construct an
  oversized persistence result.

- [ ] **Step 6: Implement real provider version persistence**

  Probe once during startup with:

  ```java
  BoundedProcessResult result = runner.run(
      List.of(properties.command(), "--version"),
      properties.versionProbeTimeout(),
      properties.versionProbeMaxOutputBytes());
  Matcher matcher = Pattern.compile("(?i)^tesseract\\s+(\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?)")
      .matcher(result.stdoutUtf8().lines().findFirst().orElse(""));
  ```

  Inject the cached version into `TesseractOcrProvider` and pass it to
  `OcrDocumentResult.of`; remove the `tesseract-cli` label.

- [ ] **Step 7: Implement bounded observable cleanup and the ciphertext allowance**

  Make `OcrSource.close()` use an injected/delegated cleaner. Retry only worker-owned
  paths three times with 50 ms between attempts, then increment
  `openecosystem.worker.ocr.cleanup.failures` tagged only by safe stage. On startup, remove
  only direct `ocr-` directories older than 24 hours under the configured temp root and
  inspect at most 100 entries. Use `Math.addExact(maxInputBytes, 16L)` for the encrypted
  stream cap and retain the independent plaintext counter.

- [ ] **Step 8: Run Task 2 verification**

  ```powershell
  apps\worker\mvnw.cmd -q "-Dtest=ImageMetadataValidatorTest,TesseractVersionProbeTest,TesseractTsvParserTest,TesseractOcrProviderTest,DriveOcrSourceReaderTest" test
  apps\worker\mvnw.cmd -q test
  apps\worker\mvnw.cmd -q spotless:check
  ```

  Expected: all commands exit 0.

- [ ] **Step 9: Commit the independent worker bounds**

  ```powershell
  git add -- apps/worker/src/main apps/worker/src/test
  git commit -m "fix(ocr): bound image words version and cleanup"
  ```

---

### Task 3: API extraction correctness and metadata-only list performance

**Findings:** I-07, I-08, I-09, I-13

**Files:**

- Create: `apps/api/src/main/java/com/openecosystem/os/invoice/InvoiceExtractionSummary.java`
- Modify: `apps/api/src/main/java/com/openecosystem/os/invoice/HeuristicInvoiceExtractionAdapter.java`
- Modify: `apps/api/src/main/java/com/openecosystem/os/media/JdbcOcrResultRepository.java`
- Modify: `apps/api/src/main/java/com/openecosystem/os/invoice/JdbcInvoiceExtractionRepository.java`
- Modify: `apps/api/src/main/java/com/openecosystem/os/media/OcrJobQueryService.java`
- Modify: `apps/api/src/main/java/com/openecosystem/os/flows/WorkflowRunner.java`
- Test: `apps/api/src/test/java/com/openecosystem/os/invoice/HeuristicInvoiceExtractionAdapterTest.java`
- Test: `apps/api/src/test/java/com/openecosystem/os/media/JdbcOcrResultRepositoryTest.java`
- Test: `apps/api/src/test/java/com/openecosystem/os/invoice/JdbcInvoiceExtractionRepositoryTest.java`
- Create: `apps/api/src/test/java/com/openecosystem/os/media/OcrJobQueryServiceTest.java`
- Test: `apps/api/src/test/java/com/openecosystem/os/media/OcrJobControllerTest.java`
- Test: `apps/api/src/test/java/com/openecosystem/os/flows/WorkflowExecutionServiceTest.java`

**Interfaces:**

- `JdbcOcrResultRepository.findPresentJobIdsForWorkspace(String workspaceId, List<String> jobIds): Set<String>` selects IDs only.
- `JdbcInvoiceExtractionRepository.findSummariesByOcrJobIdsForWorkspace(String workspaceId, List<String> jobIds): Map<String, InvoiceExtractionSummary>` selects job ID, extraction ID, status, and review flag only.
- Currency marker map is exactly `€→EUR`, `£→GBP`, `US$→USD`, `CA$/C$→CAD`, `AU$/A$→AUD`, and `NZ$→NZD`; bare `$` and unlisted symbols are ambiguous/invalid evidence.
- Distinct normalized values determine ambiguity before score; score selects provenance only among equivalent normalized values.

- [ ] **Step 1: Write RED extraction tests for distinct values and currency evidence**

  Add a differently spaced pair of labelled totals and table-driven currency cases:

  ```java
  @Test
  void differentValidTotalsAreAmbiguousRegardlessOfDistance() {
    InvoiceExtraction extraction = new HeuristicInvoiceExtractionAdapter().extract(
        request(), document(List.of(
            List.of(
                word("label-1", 1, 0, 10, "Total"),
                word("value-1", 1, 1, 80, "121.00")),
            List.of(
                word("label-2", 2, 0, 10, "Total"),
                word("value-2", 2, 1, 800, "122.00")))));
    assertThat(extraction.fields()).noneMatch(field -> field.fieldKey().equals("total_amount"));
    assertThat(extraction.warnings()).extracting(InvoiceExtractionWarning::code)
        .contains("ambiguous_total_amount");
  }

  @ParameterizedTest
  @CsvSource({"USD,USD", "US$,USD", "CA$,CAD", "C$,CAD", "AU$,AUD", "A$,AUD", "NZ$,NZD"})
  void acceptsExplicitCurrencyEvidence(String evidence, String expected) {
    InvoiceExtraction extraction = new HeuristicInvoiceExtractionAdapter().extract(
        request(), document(List.of(words(1, "Currency", evidence))));
    assertThat(field(extraction, "currency").normalizedValue()).isEqualTo(expected);
  }
  ```

  Add separate assertions that labelled `$` creates `ambiguous_currency`, persists no
  currency field, and sets `review_required`; `¥` and unsupported markers never map.

- [ ] **Step 2: Write RED metadata projection tests**

  Populate two jobs with large page/word/field content, call `listJobs`, and spy/mocking-gate
  the rich repository methods. Assert exactly the two summary projection methods are used,
  no detail loader is called, and JSON contains no document text, words, display/normalized
  values, warnings, or sources.

- [ ] **Step 3: Write RED audit metadata tests**

  Run completed and review-required workflow fixtures. Assert the audit attributes contain
  `extractionId`, `status`, and `reviewRequired`, and do not contain OCR text, values,
  warnings, filenames, or source words.

- [ ] **Step 4: Run API RED tests**

  ```powershell
  apps\api\mvnw.cmd -q "-Dtest=HeuristicInvoiceExtractionAdapterTest,JdbcOcrResultRepositoryTest,JdbcInvoiceExtractionRepositoryTest,OcrJobQueryServiceTest,OcrJobControllerTest,WorkflowExecutionServiceTest" test
  ```

  Expected: FAIL on old score-tie ambiguity, bare-dollar mapping, missing projection APIs,
  and missing audit attributes.

- [ ] **Step 5: Implement normalized-value ambiguity and explicit currency mapping**

  Group valid candidates by `normalizedValue(key, candidate.text())`. Return no candidate
  when the map has more than one key. For one key, choose minimum score, then minimum page
  and reading order. Detect a labelled bare dollar before general invalid handling so it
  produces `ambiguous_currency` rather than fabricated USD or a value-bearing warning.

- [ ] **Step 6: Implement batched metadata-only list projections**

  Load authorized jobs first, collect job IDs, and issue at most two content-free projection
  queries for the workspace. Map summaries from the resulting set/map. Leave
  `toDetailResponse` and the rich repositories unchanged for authorized detail reads.

  Projection SQL must name columns rather than use `select *`:

  ```sql
  select job_id from ocr_results where workspace_id = ? and job_id in (...)
  select job_id, extraction_id, status
  from invoice_extractions
  where workspace_id = ? and job_id in (...)
  order by job_id, created_at desc
  ```

- [ ] **Step 7: Add safe extraction audit metadata**

  In the generic audit action, look up the extraction by workflow execution. When present,
  add only:

  ```java
  attributes.put("extractionId", extraction.extractionId());
  attributes.put("status", extraction.status().value());
  attributes.put("reviewRequired", Boolean.toString(
      extraction.status() == InvoiceExtractionStatus.REVIEW_REQUIRED));
  ```

- [ ] **Step 8: Run Task 3 verification**

  ```powershell
  apps\api\mvnw.cmd -q "-Dtest=HeuristicInvoiceExtractionAdapterTest,JdbcOcrResultRepositoryTest,JdbcInvoiceExtractionRepositoryTest,OcrJobQueryServiceTest,OcrJobControllerTest,WorkflowExecutionServiceTest" test
  apps\api\mvnw.cmd -q test
  apps\api\mvnw.cmd -q spotless:check
  ```

  Expected: all commands exit 0; list projection tests prove no sensitive mapper executes.

- [ ] **Step 9: Commit API corrections**

  ```powershell
  git add -- apps/api/src/main apps/api/src/test
  git commit -m "fix(invoice): enforce safe extraction evidence"
  ```

---

### Task 4: Web polling and accessibility

**Findings:** I-10, I-14

**Files:**

- Create: `apps/web/src/features/media/media-polling.ts`
- Create: `apps/web/src/features/media/media-polling.test.ts`
- Modify: `apps/web/src/features/media/media-screen.tsx`
- Modify: `apps/web/src/features/media/use-ocr-jobs.ts`
- Modify: `apps/web/src/lib/media-api.ts`
- Modify: `apps/web/src/features/media/media-screen.test.tsx`

**Interfaces:**

- `PollingPhase` is `"discovery" | "active-job" | "awaiting-extraction"`.
- `PollingWindow` stores `phase`, `startedAtMs`, and `attempts`.
- `nextPollingState(PollingWindow window, OcrJobSummary[], number nowMs): { poll: boolean; reason: PollingExpiryReason | null }` applies both elapsed-time and attempt caps.
- Discovery window: 30 seconds and 20 attempts at 1.5 seconds.
- Active queued/processing window: 5 minutes and 200 attempts at 1.5 seconds.
- Completed-awaiting-extraction window: 30 seconds and 20 attempts at 1.5 seconds.
- Expiry stops automatic polling, retains tracked context, and offers manual refresh.
- Informational transitions use one `role="status"`, `aria-live="polite"`,
  `aria-atomic="true"` region. Rejection/failure/expiry use `role="alert"`.

- [ ] **Step 1: Write polling state-machine RED tests with fake timers**

  Cover repeated empty lists after upload, matching job arrival on the last legal attempt,
  discovery expiry, an active job reaching five minutes, completed extraction arrival, and
  manual refresh after expiry.

  ```ts
  expect(
    nextPollingState(
      { phase: "discovery", startedAtMs: 0, attempts: 19 },
      emptyJobs,
      29_000,
    ),
  ).toMatchObject({
    poll: true,
  });
  expect(
    nextPollingState(
      { phase: "discovery", startedAtMs: 0, attempts: 20 },
      emptyJobs,
      30_000,
    ),
  ).toMatchObject({
    poll: false,
    reason: "discovery-expired",
  });
  ```

- [ ] **Step 2: Write accessibility RED tests**

  Add Testing Library assertions for upload success/job created/completion through
  `getByRole("status")`, and unsupported upload/upload failure/poll expiry through
  `getByRole("alert")`. Re-render the same message and assert only one live-region instance
  exists. Retain mobile dialog/focus behavior tests.

- [ ] **Step 3: Run focused web RED tests**

  ```powershell
  apps\web\node_modules\.bin\vitest.CMD run src\features\media\media-polling.test.ts src\features\media\media-screen.test.tsx
  ```

  Expected: FAIL because polling sessions and live-region roles do not exist.

- [ ] **Step 4: Implement explicit polling sessions**

  Keep start time and attempt count in component state keyed by pending file/job and polling
  phase. The refetch callback consults that state rather than only response status. Do not
  clear `pendingUpload` on expiry. Replace its feedback with a recoverable expired state and
  Refresh action that resets the relevant window and calls `refetch()`.

- [ ] **Step 5: Implement stable status/alert regions**

  Use a stable wrapper and message key:

  ```tsx
  <div
    role={feedback.tone === "info" ? "status" : "alert"}
    aria-live={feedback.tone === "info" ? "polite" : undefined}
    aria-atomic="true"
  >
    {feedback.message}
  </div>
  ```

  Give the upload mutation error the alert role. Do not create simultaneous desktop/mobile
  copies of the same live message.

- [ ] **Step 6: Run Task 4 verification**

  ```powershell
  apps\web\node_modules\.bin\vitest.CMD run src\features\media\media-polling.test.ts src\features\media\media-screen.test.tsx
  Push-Location apps\web
  corepack pnpm format:check
  corepack pnpm lint
  corepack pnpm typecheck
  corepack pnpm test
  corepack pnpm build
  Pop-Location
  ```

  Expected: focused and full web checks exit 0 with no lint warnings.

- [ ] **Step 7: Commit web corrections**

  ```powershell
  git add -- apps/web/src/features/media apps/web/src/lib/media-api.ts
  git commit -m "fix(media): bound OCR polling and announce updates"
  ```

---

### Task 5: Runtime tooling, permission gate, and documentation corrections

**Findings:** I-11, M-03, M-04, CV-04; deployment alignment for I-01, I-02, I-06, I-12

**Files:**

- Modify: `apps/worker/Dockerfile`
- Modify: `apps/worker/src/main/resources/application.yml`
- Modify: `apps/worker/src/test/resources/application.yml`
- Modify: `infra/docker/docker-compose.yml`
- Modify: `infra/k8s/base/configmap.yaml`
- Modify: `infra/k8s/base/worker-deployment.yaml`
- Modify: `infra/k8s/overlays/prod/deployment-patch.yaml`
- Modify: `.env.example`
- Modify: `Makefile`
- Delete: `scripts/seed-demo-data.ps1`
- Delete: `scripts/seed-demo-data.sh`
- Delete: `scripts/reset-demo-data.ps1`
- Delete: `scripts/reset-demo-data.sh`
- Modify: `docs/architecture/DEPLOYMENT.md`
- Modify: `docs/architecture/DATA_STRATEGY.md`
- Review only: `docs/architecture/PERMISSIONS.md`
- Modify: `apps/api/src/main/java/com/openecosystem/os/media/OcrJobQueryService.java`
- Modify: `apps/api/src/test/java/com/openecosystem/os/media/OcrJobQueryServiceTest.java`
- Modify: `apps/api/src/test/java/com/openecosystem/os/media/OcrJobControllerTest.java`
- Conditionally create only if inspection confirms no executable permission mechanism:
  `apps/api/src/main/java/com/openecosystem/os/common/security/ResourcePermissionDecision.java`
- Conditionally create only with that boundary:
  `apps/api/src/test/java/com/openecosystem/os/common/security/ResourcePermissionDecisionTest.java`
- Modify: `docs/product/USER_JOURNEYS.md`
- Modify: `docs/development/TEST_COMMANDS.md`
- Modify formatting only: `docs/superpowers/specs/2026-07-10-real-ocr-invoice-extraction-design.md`
- Preserve unchanged: `.superpowers/sdd/real-ocr-integration-verification.md`
- Preserve unchanged: `.superpowers/sdd/real-ocr-final-review.md`

**Interfaces:**

- Worker image contains `/app/app.jar`, `/app/pdf-helper.jar`, Tesseract, and `eng`; the
  helper remains a child process in the same read-only, non-root container.
- Runtime defaults: document 10m, page 60s, persistence/cleanup margin 2m, stale 15m,
  10,000 words/page, 100,000 words/document, version probe 5s/4096 bytes.
- `make smoke-real-ocr` remains supported. Obsolete `seed`/`reset` fake-invoice targets are
  removed.
- OCR/extraction detail uses the repository's existing source-file `file:view` decision
  before any structured result, text, field, warning, or provenance repository load.
- If no executable decision mechanism exists, add the smallest reusable resource/action
  decision boundary and record the gap; do not create a parallel permission model.
- Denied and missing source files preserve non-enumerating repository conventions.

- [ ] **Step 1: Add runtime binding RED tests before changing defaults**

  Extend the bound Spring configuration test to assert every new variable and the strict
  claim invariant under application, Compose-equivalent, and Kubernetes-equivalent values.
  Run:

  ```powershell
  apps\worker\mvnw.cmd -q "-Dtest=WorkerOcrPropertiesTest" test
  ```

  Expected: FAIL because deployment/runtime values are not yet defined.

- [ ] **Step 2: Package the helper into the existing worker image**

  Add a helper build stage or build both Maven projects in the existing builder, copy the
  helper executable to `/app/pdf-helper.jar`, and keep the runtime entrypoint unchanged:

  ```dockerfile
  COPY --from=builder /app/pdf-helper/target/open-ecosystem-pdf-helper-0.1.0-SNAPSHOT.jar /app/pdf-helper.jar
  ENTRYPOINT ["java", "-jar", "/app/app.jar"]
  ```

  Do not add a port, sidecar, volume, service, or helper credential.

- [ ] **Step 3: Align application, Compose, Kubernetes, and example configuration**

  Add the exact non-secret variables to all runtime surfaces:

  ```txt
  OCR_DOCUMENT_TIMEOUT_SECONDS=10m
  OCR_PERSISTENCE_CLEANUP_MARGIN_SECONDS=2m
  OCR_STALE_PROCESSING_TIMEOUT_SECONDS=15m
  OCR_PDF_HELPER_COMMAND=java
  OCR_PDF_HELPER_JAR=/app/pdf-helper.jar
  OCR_MAX_WORDS_PER_PAGE=10000
  OCR_MAX_WORDS_PER_DOCUMENT=100000
  OCR_MAX_PROCESS_DIAGNOSTICS_BYTES=4096
  OCR_VERSION_PROBE_TIMEOUT_SECONDS=5s
  OCR_VERSION_PROBE_MAX_OUTPUT_BYTES=4096
  ```

  Keep `/tmp` at 128 MiB unless measured helper/render output demonstrates it cannot hold
  the existing one-document/input/render limits; do not increase it speculatively.

- [ ] **Step 4: Remove obsolete demo commands**

  Delete the four scripts and remove `SEED_DEMO`, `RESET_DEMO`, `seed`, and `reset` from the
  Makefile and `.PHONY`. Verify:

  ```powershell
  $matches = Select-String -Path Makefile,scripts\* -Pattern '/api/demo/invoice-automation' -ErrorAction SilentlyContinue
  if ($matches) { throw 'Obsolete demo endpoint references remain.' }
  ```

- [ ] **Step 5: Correct current-behavior documentation without rewriting history**

  Document helper isolation, budgets, limits, and provider-version provenance in deployment
  and data strategy docs. Change the invoice journey so Kanban/approval is explicitly P2
  and current MVP ends at review, notification, audit, Knowledge placeholder, and search.
  Remove only the two trailing spaces called out in M-04 from the original design.

  Do not edit historical verification reports. State in the eventual new report that they
  are superseded for merge evidence.

- [ ] **Step 6: Implement and verify the source-file `file:view` decision gate**

  Re-read `PERMISSIONS.md`, `PlaceholderAuthenticationContext`, `OcrJobQueryService`, and
  all existing permission services, evaluators, ports, and policies. Reuse the executable
  mechanism when one exists. If inspection still finds none, record that architectural gap
  and add the smallest reusable resource/action permission-decision boundary; do not embed
  an OCR-only ACL or duplicate a parallel permission model.

  Make the source-file `file:view` decision after resolving the authenticated principal and
  source identity but before calling any OCR result or extraction repository that can load
  structured words, text, fields, warnings, or provenance. Preserve the repository's
  non-enumerating not-found/denied response convention.

  Add service/controller tests for exactly these cases:
  1. an authorized same-workspace principal receives the requested detail;
  2. a same-workspace principal without `file:view` receives the non-enumerating denied
     response;
  3. a foreign-workspace principal receives the same non-enumerating response; and
  4. a missing source file receives the same repository-conventional response.

  In every denied/foreign/missing test, verify that the structured OCR and invoice
  extraction repositories are never called. Run:

  ```powershell
  apps\api\mvnw.cmd -q "-Dtest=OcrJobQueryServiceTest,OcrJobControllerTest,ResourcePermissionDecisionTest" test
  ```

  If the existing permission mechanism makes the conditional
  `ResourcePermissionDecisionTest` unnecessary, omit that class from the selector. Expected:
  all cases pass without loading protected OCR/extraction content on an authorization
  failure.

- [ ] **Step 7: Run Task 5 runtime and documentation checks**

  ```powershell
  docker compose -f infra\docker\docker-compose.yml config --quiet
  docker compose -f infra\docker\docker-compose.yml build worker
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\k8s-validate.ps1 -KubeconformImage ghcr.io/yannh/kubeconform:latest
  npx --yes prettier@3.8.3 "docs/superpowers/specs/*.md" "docs/superpowers/plans/*.md" "docs/architecture/*.md" "docs/product/*.md" "docs/development/*.md" --check
  ```

  Expected: Compose, worker build, base/dev/prod Kubernetes validation, and formatting exit 0. The worker image must contain both JARs and remain UID/GID 10001 with a read-only root.

- [ ] **Step 8: Commit runtime/tooling/docs corrections**

  ```powershell
  git add -- apps/worker/Dockerfile apps/worker/src/main/resources apps/worker/src/test/resources infra .env.example Makefile scripts docs
  git commit -m "chore(ocr): align remediation runtime and documentation"
  ```

  Do not present this commit as merge-ready unless the authorization gate and its four
  required cases pass.

---

### Task 6: PostgreSQL, POSIX smoke, vulnerability scans, and integration verification

**Findings:** CV-01, CV-02, CV-03, M-04; final proof for I-01 through I-14

**Files:**

- Modify: `apps/api/pom.xml`
- Create: `apps/api/src/test/java/com/openecosystem/os/migration/RealOcrPostgresMigrationTest.java`
- Modify: `apps/worker/pom.xml`
- Create: `apps/worker/src/test/java/com/openecosystem/os/worker/ocr/OcrJobRepositoryPostgresTest.java`
- Modify: `scripts/smoke-real-ocr.ps1`
- Modify: `scripts/smoke-real-ocr.sh`
- Create: `.github/workflows/real-ocr-integration.yml`
- Modify: `.github/workflows/security.yml`
- Create after commands run: `.superpowers/sdd/real-ocr-remediation-integration-verification.md`

**Interfaces:**

- PostgreSQL migration test starts at V6-shaped data, migrates through unmodified V7/V8,
  and proves composite lineage constraints.
- PostgreSQL worker test races queued/stale/fresh claims and duplicate completion paths;
  one live claim produces one result/terminal side-effect set.
- Both smoke scripts assert API `providerVersion` equals the first semantic version reported
  by `tesseract --version` inside the exact worker image.
- Linux CI parses and executes the POSIX smoke, always tears down Compose, and records logs
  on failure.
- Trivy scans repository vulnerabilities/misconfiguration and the exact worker image ID.

- [ ] **Step 1: Write PostgreSQL RED tests and add test-scoped dependencies**

  Add Testcontainers PostgreSQL/JUnit dependencies to API and worker POMs. In the API test,
  run Flyway to target 6, insert representative existing Drive/OCR/workflow rows, then
  migrate to latest. Assert V7/V8 objects, composite foreign-key rejection, uniqueness, and
  seeded workflow v3.

  In the worker test, use a real PostgreSQL container and two concurrent JDBC connections:

  ```java
  assertThat(race(() -> repository.claimForProcessing(jobId, "tesseract", now, staleBefore)))
      .containsExactlyInAnyOrder(true, false);
  assertThat(count("select count(*) from ocr_results where job_id = ?", jobId)).isEqualTo(1);
  ```

- [ ] **Step 2: Run PostgreSQL tests and confirm behavioral RED evidence**

  ```powershell
  apps\api\mvnw.cmd -q "-Dtest=RealOcrPostgresMigrationTest" test
  apps\worker\mvnw.cmd -q "-Dtest=OcrJobRepositoryPostgresTest" test
  ```

  Expected before implementation: FAIL on missing Testcontainers tests/dependencies. After
  implementation: PASS against PostgreSQL 16, not H2.

- [ ] **Step 3: Extend both real-OCR smoke scripts**

  Parse the container's first Tesseract version line to a semantic version and compare it
  with `.ocrResult.providerVersion`. Keep fixture assertions value-safe and synthetic. Add
  cleanup/trap handling so a stack started by the script is stopped on success or failure.

- [ ] **Step 4: Add Linux POSIX smoke CI**

  Create an Ubuntu workflow that checks out the branch, runs
  `sh -n scripts/smoke-real-ocr.sh`, builds/starts Compose, runs
  `sh scripts/smoke-real-ocr.sh --timeout-seconds 300`, captures Compose logs on failure,
  and always executes `docker compose ... down -v --remove-orphans`.

- [ ] **Step 5: Add exact worker-image vulnerability scanning**

  In security CI, build the worker image with a deterministic local tag, resolve its image
  ID/digest, and run Trivy with HIGH/CRITICAL exit code 1. Keep the existing repository
  vuln/misconfiguration scan. Record Trivy database update timestamp and image ID in job
  output/artifacts.

- [ ] **Step 6: Run the complete local Windows verification matrix**

  ```powershell
  mvn -q -f apps\worker\pdf-helper\pom.xml test
  apps\worker\mvnw.cmd -q test
  apps\worker\mvnw.cmd -q spotless:check
  apps\api\mvnw.cmd -q test
  apps\api\mvnw.cmd -q spotless:check
  Push-Location apps\web
  corepack pnpm format:check
  corepack pnpm lint
  corepack pnpm typecheck
  corepack pnpm test
  corepack pnpm build
  corepack pnpm audit --audit-level high
  Pop-Location
  docker compose -f infra\docker\docker-compose.yml config --quiet
  docker compose -f infra\docker\docker-compose.yml build worker
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\k8s-validate.ps1 -KubeconformImage ghcr.io/yannh/kubeconform:latest
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-real-ocr.ps1 -StartStack -TimeoutSeconds 300
  apps\api\mvnw.cmd -q verify -P security-scan
  apps\worker\mvnw.cmd -q verify -P security-scan
  git diff --check 993b3479cb2401b8a9d831695a4244b8fa947bfe...HEAD
  ```

  Expected: every available command exits 0. Capture exact commands and exit codes; do not
  paraphrase a PowerShell substitute as a Make target.

- [ ] **Step 7: Run Linux/scanner evidence in CI or an equivalent scanner-enabled host**

  Required commands:

  ```sh
  sh -n scripts/smoke-real-ocr.sh
  sh scripts/smoke-real-ocr.sh --start-stack --timeout-seconds 300
  trivy fs --scanners vuln,misconfig --severity HIGH,CRITICAL --exit-code 1 --skip-dirs infra/k8s/overlays/dev .
  trivy config --severity HIGH,CRITICAL --exit-code 1 infra/k8s
  trivy image --severity HIGH,CRITICAL --exit-code 1 open-ecosystem-worker:real-ocr-remediation
  ```

  Expected: POSIX syntax/runtime and all scans exit 0, or a policy-compliant vulnerability
  triage blocks merge with advisory, exploitability, mitigation, owner, and date.

- [ ] **Step 8: Create the new truthful integration verification report**

  Write `.superpowers/sdd/real-ocr-remediation-integration-verification.md` with source/base
  hashes, exact commands, exit codes, PostgreSQL version, image ID/digest, Tesseract version,
  Trivy database freshness, findings disposition, and unavailable evidence. Mark historical
  Task 8 evidence superseded. Include the authorization result and evidence:

  ```txt
  CV-04: PASS - source-file file:view is decided before protected OCR/extraction loads;
  authorized, denied, foreign-workspace, and missing-source cases pass, and failed
  authorization performs no protected repository calls.
  ```

  If that statement is not supported by exact test evidence, report CV-04 as blocked and do
  not declare the branch merge-ready.

- [ ] **Step 9: Run final self-review against the amendment and code diff**

  Verify one-by-one that I-01 through I-14, M-01 through M-04, and CV-01 through CV-04 have
  code/evidence or an explicit blocker. Search for helper protocol/TSV/text/value leakage,
  old demo endpoints, `tesseract-cli`, bare-dollar USD mapping, unbounded list reads, and
  polling without a budget.

- [ ] **Step 10: Commit verification infrastructure and evidence only after it is true**

  ```powershell
  git add -- apps/api/pom.xml apps/api/src/test apps/worker/pom.xml apps/worker/src/test scripts .github/workflows .superpowers/sdd/real-ocr-remediation-integration-verification.md
  git commit -m "test(ocr): verify remediation on production boundaries"
  ```

  If CV-04 is still blocked, stop after the commit and report the concrete implementation or
  evidence gap. Do not push/open a merge-ready PR or claim final approval.

## Plan completion criteria

- Tasks 1-5 have focused green tests and independently reviewable commits.
- Task 6 proves PostgreSQL 16 behavior, PowerShell and POSIX real-OCR smoke, exact provider
  version, and repository/IaC/worker-image vulnerability policy.
- No V1-V8 migration, event payload, REST route, OCR/extraction table, or historical
  verification report was rewritten.
- The new verification report contains exact evidence and no contradicted command claim.
- CV-04 passes only when the source-file `file:view` decision occurs before protected detail
  loads, the four required authorization cases pass, and failed authorization makes no OCR
  or extraction repository calls. Workspace membership alone never grants file access.
