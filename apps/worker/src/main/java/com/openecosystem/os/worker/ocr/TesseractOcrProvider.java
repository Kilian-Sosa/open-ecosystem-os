package com.openecosystem.os.worker.ocr;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class TesseractOcrProvider implements OcrProvider {

  private static final int MEANINGFUL_NATIVE_TEXT_CHARACTERS = 20;

  private final OcrSourceReader sourceReader;
  private final BoundedProcessRunner processRunner;
  private final WorkerOcrProperties properties;
  private final Semaphore permits;

  public TesseractOcrProvider(
      OcrSourceReader sourceReader,
      BoundedProcessRunner processRunner,
      WorkerOcrProperties properties) {
    this.sourceReader = sourceReader;
    this.processRunner = processRunner;
    this.properties = properties;
    this.permits = new Semaphore(properties.maxConcurrentDocuments(), true);
  }

  @Override
  public String name() {
    return "tesseract";
  }

  @Override
  public OcrDocumentResult extract(OcrJob job) {
    boolean acquired = false;
    try {
      permits.acquire();
      acquired = true;
      try (OcrSource source = sourceReader.open(job)) {
        List<OcrPageResult> pages =
            switch (source.contentType()) {
              case "application/pdf" -> extractPdf(source);
              case "image/png", "image/jpeg" -> List.of(extractImage(source.path(), 1));
              default ->
                  throw failure(
                      "OCR_SOURCE_CONTENT_TYPE_INVALID", "OCR source content type was invalid");
            };
        return OcrDocumentResult.of(name(), "tesseract-cli", pages);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw failure("OCR_PROCESS_INTERRUPTED", "OCR process was interrupted");
    } finally {
      if (acquired) {
        permits.release();
      }
    }
  }

  private List<OcrPageResult> extractPdf(OcrSource source) {
    try (PDDocument document = Loader.loadPDF(source.path().toFile())) {
      if (document.getNumberOfPages() > properties.maxPages()) {
        throw failure("OCR_PDF_PAGE_LIMIT", "OCR document exceeded the configured page limit");
      }
      List<OcrPageResult> pages = new ArrayList<>();
      PDFRenderer renderer = new PDFRenderer(document);
      for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
        int pageNumber = pageIndex + 1;
        String nativeText = nativeText(document, pageNumber);
        if (meaningful(nativeText)) {
          pages.add(nativePage(pageNumber, nativeText));
        } else {
          pages.add(
              renderAndExtract(
                  renderer,
                  document.getPage(pageIndex),
                  source.path().getParent(),
                  pageIndex,
                  pageNumber));
        }
      }
      if (pages.isEmpty()) {
        throw failure("OCR_PDF_INVALID", "OCR PDF could not be processed");
      }
      return pages;
    } catch (OcrProviderException exception) {
      throw exception;
    } catch (IOException | RuntimeException exception) {
      throw failure("OCR_PDF_INVALID", "OCR PDF could not be processed");
    }
  }

  private String nativeText(PDDocument document, int pageNumber) throws IOException {
    PDFTextStripper stripper = new PDFTextStripper();
    stripper.setStartPage(pageNumber);
    stripper.setEndPage(pageNumber);
    return stripper.getText(document).trim();
  }

  private boolean meaningful(String text) {
    return text.replaceAll("\\s+", "").length() >= MEANINGFUL_NATIVE_TEXT_CHARACTERS;
  }

  private OcrPageResult nativePage(int pageNumber, String text) {
    String normalized = text.replaceAll("[\\t ]+", " ").replaceAll("\\s*\\n\\s*", "\n").trim();
    String[] tokens = normalized.split("\\s+");
    List<OcrWord> words = new ArrayList<>();
    for (int index = 0; index < tokens.length; index++) {
      if (!tokens[index].isBlank()) {
        int order = words.size() + 1;
        words.add(new OcrWord(order, pageNumber, order, 0, 0, 0, order, tokens[index], null, null));
      }
    }
    return new OcrPageResult(pageNumber, OcrSourceKind.PDF_TEXT_LAYER, normalized, words);
  }

  private OcrPageResult renderAndExtract(
      PDFRenderer renderer, PDPage page, Path temporaryDirectory, int pageIndex, int pageNumber)
      throws IOException {
    Path rendered = temporaryDirectory.resolve("page-" + pageNumber + ".png");
    try {
      if (estimatedRenderedPixels(page.getCropBox(), properties.renderDpi())
          > properties.maxRenderedPixels()) {
        throw failure(
            "OCR_PDF_RENDER_LIMIT", "OCR rendered page exceeded the configured pixel limit");
      }
      BufferedImage image = renderer.renderImageWithDPI(pageIndex, properties.renderDpi());
      if ((long) image.getWidth() * image.getHeight() > properties.maxRenderedPixels()) {
        throw failure(
            "OCR_PDF_RENDER_LIMIT", "OCR rendered page exceeded the configured pixel limit");
      }
      if (!ImageIO.write(image, "png", rendered.toFile())) {
        throw failure("OCR_PDF_RENDER_FAILED", "OCR PDF page could not be rendered");
      }
      return extractImage(rendered, pageNumber);
    } finally {
      Files.deleteIfExists(rendered);
    }
  }

  static long estimatedRenderedPixels(PDRectangle pageBox, int dpi) {
    long width = Math.max(1L, (long) Math.ceil(pageBox.getWidth() * dpi / 72D));
    long height = Math.max(1L, (long) Math.ceil(pageBox.getHeight() * dpi / 72D));
    try {
      return Math.multiplyExact(width, height);
    } catch (ArithmeticException exception) {
      return Long.MAX_VALUE;
    }
  }

  private OcrPageResult extractImage(Path imagePath, int pageNumber) {
    List<String> command =
        List.of(
            properties.command(),
            imagePath.toString(),
            "stdout",
            "-l",
            properties.languages(),
            "tsv");
    BoundedProcessResult output =
        processRunner.run(command, properties.pageTimeout(), properties.maxProcessOutputBytes());
    if (output.exitCode() != 0) {
      throw failure("OCR_PROCESS_FAILED", "OCR process failed");
    }
    return new TesseractTsvParser(properties.maxProcessOutputBytes())
        .parse(output.stdout(), pageNumber);
  }

  private OcrProviderException failure(String code, String summary) {
    return new OcrProviderException(code, summary);
  }
}
