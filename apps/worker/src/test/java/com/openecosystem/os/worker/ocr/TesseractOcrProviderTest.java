package com.openecosystem.os.worker.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TesseractOcrProviderTest {

  private static final String TSV =
      """
      level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext
      5\t1\t1\t1\t1\t1\t1\t2\t3\t4\t98.50\tFallback
      """;

  @Test
  void bornDigitalPageBypassesTesseractAndPreservesGroupedNativeWords() throws Exception {
    Path root = Files.createTempDirectory("ocr-native-");
    Path input = Files.createFile(root.resolve("input.pdf"));
    AtomicInteger starts = new AtomicInteger();
    TesseractOcrProvider provider =
        provider(input, root, "application/pdf", starts, new FakePdfHelperClient(1, nativePage()));

    OcrDocumentResult result = provider.extract(job());

    assertThat(result.pages().getFirst().sourceKind()).isEqualTo(OcrSourceKind.PDF_TEXT_LAYER);
    assertThat(result.pages().getFirst().words())
        .extracting(OcrWord::text)
        .containsExactly("Invoice", "Number", "INV-2026-42", "Issue", "Date", "2026-07-13");
    assertThat(result.pages().getFirst().words())
        .extracting(OcrWord::lineNumber)
        .containsSequence(1, 1, 1, 2, 2, 2);
    assertThat(starts).hasValue(0);
    assertThat(Files.exists(root)).isFalse();
  }

  @Test
  void sparseNativeLayerFallsBackToTesseractAndDeletesRenderedAndSourceFiles() throws Exception {
    Path root = Files.createTempDirectory("ocr-fallback-");
    Path input = Files.createFile(root.resolve("input.pdf"));
    AtomicInteger starts = new AtomicInteger();
    TesseractOcrProvider provider =
        provider(
            input,
            root,
            "application/pdf",
            starts,
            new FakePdfHelperClient(1, PdfHelperPage.tesseract()));

    OcrDocumentResult result = provider.extract(job());

    assertThat(result.pages().getFirst().sourceKind()).isEqualTo(OcrSourceKind.TESSERACT_TSV);
    assertThat(starts).hasValue(1);
    assertThat(Files.exists(root)).isFalse();
  }

  @Test
  void rejectsHelperPageCountsOverTheConfiguredLimit() throws Exception {
    Path root = Files.createTempDirectory("ocr-page-limit-");
    Path input = Files.createFile(root.resolve("input.pdf"));
    TesseractOcrProvider provider =
        provider(
            input,
            root,
            "application/pdf",
            new AtomicInteger(),
            new FakePdfHelperClient(2, nativePage()),
            1);

    assertFailure(() -> provider.extract(job()), "OCR_PDF_PAGE_LIMIT");
    assertThat(Files.exists(root)).isFalse();
  }

  @Test
  void documentDeadlinePreventsSourceWorkAndReleasesThePermit() throws Exception {
    Path root = Files.createTempDirectory("ocr-timeout-");
    Path input = Files.createFile(root.resolve("input.pdf"));
    AtomicBoolean opened = new AtomicBoolean(false);
    OcrSourceReader reader =
        ignored -> {
          opened.set(true);
          return new OcrSource(input, root, "application/pdf");
        };
    WorkerOcrProperties properties = properties(2);
    TesseractOcrProvider provider =
        new TesseractOcrProvider(
            reader,
            runner(new AtomicInteger()),
            properties,
            new FakePdfHelperClient(1, nativePage()));
    OcrExecutionDeadline deadline =
        OcrExecutionDeadline.start(
            Clock.fixed(Instant.parse("2026-07-13T10:00:01Z"), java.time.ZoneOffset.UTC),
            Instant.parse("2026-07-13T09:50:00Z"),
            Duration.ofMinutes(10));

    assertFailure(() -> provider.extract(job(), deadline), "OCR_DOCUMENT_TIMEOUT");
    assertThat(opened).isFalse();
    assertThat(Files.exists(root)).isTrue();
    Files.deleteIfExists(input);
    Files.deleteIfExists(root);
  }

  @Test
  void returnsFixedSafeFailureWhenTesseractFails() throws Exception {
    Path root = Files.createTempDirectory("ocr-cleanup-");
    Path input = Files.createFile(root.resolve("input.png"));
    Files.write(input, png(1, 1));
    BoundedProcessRunner runner =
        new BoundedProcessRunner(
            command -> new CompletedProcess(1, "private diagnostics", "private path"));
    TesseractOcrProvider provider =
        new TesseractOcrProvider(
            sourceReader(input, root, "image/png"),
            runner,
            properties(2),
            new FakePdfHelperClient(1, nativePage()));

    assertFailure(() -> provider.extract(job()), "OCR_PROCESS_FAILED");
    assertThat(Files.exists(root)).isFalse();
  }

  @Test
  void rejectsAnOverLimitDirectImageBeforeStartingTesseract() throws Exception {
    Path root = Files.createTempDirectory("ocr-image-bound-");
    Path input = root.resolve("input.png");
    Files.write(input, png(100_000, 100_000));
    AtomicInteger starts = new AtomicInteger();
    TesseractOcrProvider provider =
        provider(input, root, "image/png", starts, new FakePdfHelperClient(1, nativePage()));

    assertFailure(() -> provider.extract(job()), "OCR_IMAGE_PIXEL_LIMIT");
    assertThat(starts).hasValue(0);
    assertThat(Files.exists(root)).isFalse();
  }

  @Test
  void rejectsMultiPageTsvOutputBeforeAppendingBeyondTheDocumentWordLimit() throws Exception {
    Path root = Files.createTempDirectory("ocr-document-word-bound-");
    Path input = Files.createFile(root.resolve("input.pdf"));
    WorkerOcrProperties limited = properties(2, 1);
    TesseractOcrProvider provider =
        new TesseractOcrProvider(
            sourceReader(input, root, "application/pdf"),
            runner(new AtomicInteger()),
            limited,
            new FakePdfHelperClient(2, PdfHelperPage.tesseract()));

    assertFailure(() -> provider.extract(job()), "OCR_WORD_LIMIT");
    assertThat(Files.exists(root)).isFalse();
  }

  private TesseractOcrProvider provider(
      Path input, Path root, String contentType, AtomicInteger starts, FakePdfHelperClient helper) {
    return provider(input, root, contentType, starts, helper, 2);
  }

  private TesseractOcrProvider provider(
      Path input,
      Path root,
      String contentType,
      AtomicInteger starts,
      FakePdfHelperClient helper,
      int maxPages) {
    WorkerOcrProperties properties = properties(maxPages);
    return new TesseractOcrProvider(
        sourceReader(input, root, contentType), runner(starts), properties, helper);
  }

  private OcrSourceReader sourceReader(Path input, Path root, String contentType) {
    return ignored -> new OcrSource(input, root, contentType);
  }

  private BoundedProcessRunner runner(AtomicInteger starts) {
    return new BoundedProcessRunner(
        command -> {
          starts.incrementAndGet();
          return new CompletedProcess(0, TSV, "");
        });
  }

  private WorkerOcrProperties properties(int maxPages) {
    return properties(maxPages, 100_000);
  }

  private WorkerOcrProperties properties(int maxPages, int maxWordsPerDocument) {
    return new WorkerOcrProperties(
        "tesseract",
        3,
        Duration.ofSeconds(1),
        "tesseract",
        "eng",
        1,
        Duration.ofSeconds(1),
        maxPages,
        200,
        25L * 1024 * 1024,
        20_000_000,
        1_024,
        Duration.ofMinutes(15),
        Duration.ofMinutes(10),
        Duration.ofMinutes(2),
        "java",
        "/app/pdf-helper.jar",
        10_000,
        maxWordsPerDocument,
        Duration.ofSeconds(5),
        4 * 1024);
  }

  private static byte[] png(int width, int height) {
    try {
      java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
      output.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
      byte[] header =
          java.nio.ByteBuffer.allocate(13)
              .order(java.nio.ByteOrder.BIG_ENDIAN)
              .putInt(width)
              .putInt(height)
              .put((byte) 8)
              .put((byte) 2)
              .put((byte) 0)
              .put((byte) 0)
              .put((byte) 0)
              .array();
      chunk(output, "IHDR", header);
      chunk(output, "IEND", new byte[0]);
      return output.toByteArray();
    } catch (java.io.IOException exception) {
      throw new AssertionError(exception);
    }
  }

  private static void chunk(java.io.ByteArrayOutputStream output, String type, byte[] data)
      throws java.io.IOException {
    output.write(
        java.nio.ByteBuffer.allocate(4)
            .order(java.nio.ByteOrder.BIG_ENDIAN)
            .putInt(data.length)
            .array());
    byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    output.write(typeBytes);
    output.write(data);
    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
    crc.update(typeBytes);
    crc.update(data);
    output.write(
        java.nio.ByteBuffer.allocate(4)
            .order(java.nio.ByteOrder.BIG_ENDIAN)
            .putInt((int) crc.getValue())
            .array());
  }

  private OcrJob job() {
    Instant now = Instant.parse("2026-07-13T10:00:00Z");
    return new OcrJob(
        "ocr_123",
        "file_123",
        "wrk_123",
        "usr_123",
        "evt_uploaded",
        "corr_123",
        "application/pdf",
        "workspaces/wrk_123/drive/file_123/original",
        OcrJobStatus.PROCESSING,
        "tesseract",
        1,
        3,
        null,
        null,
        null,
        null,
        null,
        now,
        now,
        null,
        null,
        now,
        now);
  }

  private PdfHelperPage nativePage() {
    return new PdfHelperPage(
        OcrSourceKind.PDF_TEXT_LAYER,
        "Invoice Number INV-2026-42\nIssue Date 2026-07-13",
        List.of(
            word(1, 1, "Invoice"),
            word(1, 2, "Number"),
            word(1, 3, "INV-2026-42"),
            word(2, 1, "Issue"),
            word(2, 2, "Date"),
            word(2, 3, "2026-07-13")));
  }

  private OcrWord word(int line, int word, String text) {
    return new OcrWord(
        1, 1, word, 1, 1, line, word, text, null, new OcrBoundingBox(word * 10, line * 10, 8, 8));
  }

  private void assertFailure(ThrowingAction action, String code) {
    assertThatThrownBy(action::run)
        .isInstanceOf(OcrProviderException.class)
        .extracting(exception -> ((OcrProviderException) exception).code())
        .isEqualTo(code);
  }

  @FunctionalInterface
  private interface ThrowingAction {
    void run();
  }

  private static final class FakePdfHelperClient extends PdfHelperClient {
    private final int pageCount;
    private final PdfHelperPage page;

    private FakePdfHelperClient(int pageCount, PdfHelperPage page) {
      super(
          new BoundedProcessRunner(command -> new CompletedProcess(0, "", "")),
          new WorkerOcrProperties(
              null, 0, null, null, null, 0, null, 0, 0, 0, 0, 0, null, null, null, null, null, 0, 0,
              null, 0),
          new ObjectMapper());
      this.pageCount = pageCount;
      this.page = page;
    }

    @Override
    public int inspect(Path pdf, OcrExecutionDeadline deadline) {
      return pageCount;
    }

    @Override
    public PdfHelperPage processPage(
        Path pdf, Path renderedOutput, int pageNumber, OcrExecutionDeadline deadline) {
      if (page.sourceKind() == OcrSourceKind.TESSERACT_TSV) {
        try {
          Files.write(renderedOutput, png(1, 1));
        } catch (Exception exception) {
          throw new AssertionError(exception);
        }
        return page;
      }
      return new PdfHelperPage(
          page.sourceKind(),
          page.pageText(),
          page.words().stream()
              .map(
                  word ->
                      new OcrWord(
                          word.readingOrder(),
                          pageNumber,
                          word.pageWordOrder(),
                          word.blockNumber(),
                          word.paragraphNumber(),
                          word.lineNumber(),
                          word.wordNumber(),
                          word.text(),
                          word.confidence(),
                          word.boundingBox()))
              .toList());
    }
  }

  private static final class CompletedProcess extends Process {
    private final int exitCode;
    private final InputStream stdout;
    private final InputStream stderr;

    private CompletedProcess(int exitCode, String stdout, String stderr) {
      this.exitCode = exitCode;
      this.stdout = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
      this.stderr = new ByteArrayInputStream(stderr.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public OutputStream getOutputStream() {
      return new ByteArrayOutputStream();
    }

    @Override
    public InputStream getInputStream() {
      return stdout;
    }

    @Override
    public InputStream getErrorStream() {
      return stderr;
    }

    @Override
    public int waitFor() {
      return exitCode;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public int exitValue() {
      return exitCode;
    }

    @Override
    public void destroy() {}

    @Override
    public Process destroyForcibly() {
      return this;
    }

    @Override
    public boolean isAlive() {
      return false;
    }
  }
}
