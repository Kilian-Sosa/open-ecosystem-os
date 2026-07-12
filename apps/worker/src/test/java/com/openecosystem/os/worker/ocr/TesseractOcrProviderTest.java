package com.openecosystem.os.worker.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class TesseractOcrProviderTest {

  private static final String TSV =
      """
      level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext
      5\t1\t1\t1\t1\t1\t1\t2\t3\t4\t98.50\tFallback
      """;

  @Test
  void bypassesTesseractForMeaningfulNativePdfText() throws Exception {
    Path root = Files.createTempDirectory("ocr-native-");
    Path input = root.resolve("input.pdf");
    writePdf(input, true, 1, PDRectangle.LETTER);
    AtomicInteger starts = new AtomicInteger();
    TesseractOcrProvider provider =
        new TesseractOcrProvider(
            sourceReader(input, root, "application/pdf"),
            runner(starts, 0, TSV),
            properties(2, 20_000_000));

    OcrDocumentResult result = provider.extract(job());

    assertThat(result.pages()).hasSize(1);
    assertThat(result.pages().getFirst().sourceKind()).isEqualTo(OcrSourceKind.PDF_TEXT_LAYER);
    assertThat(starts).hasValue(0);
    assertThat(Files.exists(root)).isFalse();
  }

  @Test
  void fallsBackOnlyForTextlessPdfPagesAndAssignsDocumentWideOrder() throws Exception {
    Path root = Files.createTempDirectory("ocr-mixed-");
    Path input = root.resolve("input.pdf");
    writeMixedPdf(input);
    AtomicInteger starts = new AtomicInteger();
    TesseractOcrProvider provider =
        new TesseractOcrProvider(
            sourceReader(input, root, "application/pdf"),
            runner(starts, 0, TSV),
            properties(2, 20_000_000));

    OcrDocumentResult result = provider.extract(job());

    assertThat(starts).hasValue(1);
    assertThat(result.pages())
        .extracting(OcrPageResult::sourceKind)
        .containsExactly(OcrSourceKind.PDF_TEXT_LAYER, OcrSourceKind.TESSERACT_TSV);
    assertThat(result.pages().get(0).words().getFirst().readingOrder()).isEqualTo(1);
    assertThat(result.pages().get(1).words().getFirst().readingOrder())
        .isEqualTo(result.pages().get(0).words().size() + 1);
    assertThat(result.pages().get(1).words().getFirst().pageWordOrder()).isEqualTo(1);
    assertThat(Files.exists(root)).isFalse();
  }

  @Test
  void sendsPngAndJpegSourcesDirectlyToTesseract() throws Exception {
    for (String format : List.of("png", "jpg")) {
      Path root = Files.createTempDirectory("ocr-image-");
      Path input = root.resolve("input." + format);
      assertThat(
              ImageIO.write(
                  new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), format, input.toFile()))
          .isTrue();
      AtomicInteger starts = new AtomicInteger();
      TesseractOcrProvider provider =
          new TesseractOcrProvider(
              sourceReader(input, root, format.equals("png") ? "image/png" : "image/jpeg"),
              runner(starts, 0, TSV),
              properties(2, 20_000_000));

      OcrDocumentResult result = provider.extract(job());

      assertThat(starts).hasValue(1);
      assertThat(result.pages().getFirst().sourceKind()).isEqualTo(OcrSourceKind.TESSERACT_TSV);
      assertThat(Files.exists(root)).isFalse();
    }
  }

  @Test
  void rejectsPdfPageAndPreRenderPixelLimits() throws Exception {
    Path pageLimitRoot = Files.createTempDirectory("ocr-page-limit-");
    Path pageLimitInput = pageLimitRoot.resolve("input.pdf");
    writePdf(pageLimitInput, false, 2, PDRectangle.LETTER);
    TesseractOcrProvider pageLimitProvider =
        new TesseractOcrProvider(
            sourceReader(pageLimitInput, pageLimitRoot, "application/pdf"),
            runner(new AtomicInteger(), 0, TSV),
            properties(1, 20_000_000));

    assertFailure(() -> pageLimitProvider.extract(job()), "OCR_PDF_PAGE_LIMIT");

    Path pixelLimitRoot = Files.createTempDirectory("ocr-pixel-limit-");
    Path pixelLimitInput = pixelLimitRoot.resolve("input.pdf");
    writePdf(pixelLimitInput, false, 1, new PDRectangle(1_000, 1_000));
    TesseractOcrProvider pixelLimitProvider =
        new TesseractOcrProvider(
            sourceReader(pixelLimitInput, pixelLimitRoot, "application/pdf"),
            runner(new AtomicInteger(), 0, TSV),
            properties(2, 10));

    assertThat(TesseractOcrProvider.estimatedRenderedPixels(new PDRectangle(1_000, 1_000), 200))
        .isGreaterThan(10);
    assertFailure(() -> pixelLimitProvider.extract(job()), "OCR_PDF_RENDER_LIMIT");
    assertThat(Files.exists(pixelLimitRoot)).isFalse();
  }

  @Test
  void releasesTheFairDocumentPermitAfterAFailedSourceOpen() throws Exception {
    AtomicBoolean failFirstOpen = new AtomicBoolean(true);
    Path root = Files.createTempDirectory("ocr-permit-");
    Path input = root.resolve("input.png");
    assertThat(
            ImageIO.write(
                new BufferedImage(3, 3, BufferedImage.TYPE_INT_RGB), "png", input.toFile()))
        .isTrue();
    OcrSourceReader reader =
        ignored -> {
          if (failFirstOpen.getAndSet(false)) {
            throw new OcrProviderException(
                "OCR_SOURCE_READ_FAILED", "OCR source could not be read");
          }
          return new OcrSource(input, root, "image/png");
        };
    TesseractOcrProvider provider =
        new TesseractOcrProvider(
            reader, runner(new AtomicInteger(), 0, TSV), properties(2, 20_000_000));

    assertFailure(() -> provider.extract(job()), "OCR_SOURCE_READ_FAILED");
    assertThat(provider.extract(job()).documentText()).isEqualTo("Fallback");
    assertThat(Files.exists(root)).isFalse();
  }

  @Test
  void returnsFixedSafeFailuresAndCleansRenderedAndSourceFiles() throws Exception {
    Path root = Files.createTempDirectory("ocr-cleanup-");
    Path input = root.resolve("input.pdf");
    writePdf(input, false, 1, PDRectangle.LETTER);
    TesseractOcrProvider provider =
        new TesseractOcrProvider(
            sourceReader(input, root, "application/pdf"),
            runner(new AtomicInteger(), 1, "private diagnostics"),
            properties(2, 20_000_000));

    assertFailure(() -> provider.extract(job()), "OCR_PROCESS_FAILED");
    assertThat(Files.exists(root)).isFalse();
  }

  private OcrSourceReader sourceReader(Path input, Path root, String contentType) {
    return ignored -> new OcrSource(input, root, contentType);
  }

  private BoundedProcessRunner runner(AtomicInteger starts, int exitCode, String stdout) {
    return new BoundedProcessRunner(
        command -> {
          starts.incrementAndGet();
          return new CompletedProcess(exitCode, stdout, "");
        });
  }

  private WorkerOcrProperties properties(int maxPages, long maxRenderedPixels) {
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
        maxRenderedPixels,
        1_024,
        Duration.ofHours(1));
  }

  private OcrJob job() {
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
        null,
        null,
        null,
        null,
        InstantHolder.NOW,
        InstantHolder.NOW);
  }

  private void writePdf(Path path, boolean includeText, int pageCount, PDRectangle rectangle)
      throws IOException {
    try (PDDocument document = new PDDocument()) {
      for (int pageNumber = 0; pageNumber < pageCount; pageNumber++) {
        PDPage page = new PDPage(rectangle);
        document.addPage(page);
        if (includeText) {
          writeText(document, page, "Meaningful native text stays outside Tesseract");
        }
      }
      document.save(path.toFile());
    }
  }

  private void writeMixedPdf(Path path) throws IOException {
    try (PDDocument document = new PDDocument()) {
      PDPage first = new PDPage(PDRectangle.LETTER);
      document.addPage(first);
      writeText(document, first, "Meaningful native text stays outside Tesseract");
      document.addPage(new PDPage(PDRectangle.LETTER));
      document.save(path.toFile());
    }
  }

  private void writeText(PDDocument document, PDPage page, String text) throws IOException {
    try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
      stream.beginText();
      stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
      stream.newLineAtOffset(40, 700);
      stream.showText(text);
      stream.endText();
    }
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

  private static final class InstantHolder {
    private static final java.time.Instant NOW = java.time.Instant.parse("2026-05-22T10:00:00Z");
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
