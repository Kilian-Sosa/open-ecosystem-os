package com.openecosystem.os.worker.ocr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TesseractOcrProvider implements OcrProvider {

  private static final int STDERR_LIMIT_BYTES = 4 * 1024;

  private final OcrSourceReader sourceReader;
  private final BoundedProcessRunner processRunner;
  private final WorkerOcrProperties properties;
  private final PdfHelperClient pdfHelperClient;
  private final String providerVersion;
  private final Semaphore permits;

  @Autowired
  public TesseractOcrProvider(
      OcrSourceReader sourceReader,
      BoundedProcessRunner processRunner,
      WorkerOcrProperties properties,
      PdfHelperClient pdfHelperClient,
      TesseractVersionProbe versionProbe) {
    this.sourceReader = sourceReader;
    this.processRunner = processRunner;
    this.properties = properties;
    this.pdfHelperClient = pdfHelperClient;
    providerVersion = versionProbe.version();
    permits = new Semaphore(properties.maxConcurrentDocuments(), true);
  }

  TesseractOcrProvider(
      OcrSourceReader sourceReader,
      BoundedProcessRunner processRunner,
      WorkerOcrProperties properties,
      PdfHelperClient pdfHelperClient) {
    this.sourceReader = sourceReader;
    this.processRunner = processRunner;
    this.properties = properties;
    this.pdfHelperClient = pdfHelperClient;
    providerVersion = "unknown";
    permits = new Semaphore(properties.maxConcurrentDocuments(), true);
  }

  @Override
  public String name() {
    return "tesseract";
  }

  @Override
  public OcrDocumentResult extract(OcrJob job, OcrExecutionDeadline deadline) {
    boolean acquired = false;
    try {
      deadline.remaining();
      if (!permits.tryAcquire(deadline.remaining().toMillis(), TimeUnit.MILLISECONDS)) {
        deadline.remaining();
        throw failure("OCR_DOCUMENT_TIMEOUT", "OCR document deadline was exceeded");
      }
      acquired = true;
      deadline.remaining();
      try (OcrSource source = sourceReader.open(job)) {
        List<OcrPageResult> pages =
            switch (source.contentType()) {
              case "application/pdf" -> extractPdf(source, deadline);
              case "image/png", "image/jpeg" -> directImagePages(source, deadline);
              default ->
                  throw failure(
                      "OCR_SOURCE_CONTENT_TYPE_INVALID", "OCR source content type was invalid");
            };
        deadline.remaining();
        verifyDocumentWordLimit(pages);
        return OcrDocumentResult.of(name(), providerVersion, pages);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw failure("OCR_PROCESS_INTERRUPTED", "OCR process was interrupted");
    } finally {
      if (acquired) permits.release();
    }
  }

  public OcrDocumentResult extract(OcrJob job) {
    return extract(
        job,
        OcrExecutionDeadline.start(
            Clock.systemUTC(), Clock.systemUTC().instant(), properties.documentTimeout()));
  }

  private List<OcrPageResult> extractPdf(OcrSource source, OcrExecutionDeadline deadline) {
    int pageCount = pdfHelperClient.inspect(source.path(), deadline);
    if (pageCount > properties.maxPages()) {
      throw failure("OCR_PDF_PAGE_LIMIT", "OCR document exceeded the configured page limit");
    }
    List<OcrPageResult> pages = new ArrayList<>();
    for (int pageNumber = 1; pageNumber <= pageCount; pageNumber++) {
      OcrExecutionDeadline pageDeadline = deadline.child(properties.pageTimeout());
      Path rendered = source.path().getParent().resolve("page-" + pageNumber + ".png");
      try {
        PdfHelperPage page =
            pdfHelperClient.processPage(source.path(), rendered, pageNumber, pageDeadline);
        pages.add(
            boundedPage(
                pages,
                page.sourceKind() == OcrSourceKind.PDF_TEXT_LAYER
                    ? new OcrPageResult(
                        pageNumber, page.sourceKind(), page.pageText(), page.words())
                    : extractImage(rendered, "image/png", pageNumber, pageDeadline)));
      } finally {
        deleteRendered(rendered);
      }
    }
    if (pages.isEmpty()) throw failure("OCR_PDF_INVALID", "OCR PDF could not be processed");
    return pages;
  }

  private List<OcrPageResult> directImagePages(OcrSource source, OcrExecutionDeadline deadline) {
    List<OcrPageResult> pages = new ArrayList<>();
    pages.add(
        boundedPage(
            pages,
            extractImage(
                source.path(), source.contentType(), 1, deadline.child(properties.pageTimeout()))));
    return pages;
  }

  private OcrPageResult extractImage(
      Path imagePath, String contentType, int pageNumber, OcrExecutionDeadline deadline) {
    new ImageMetadataValidator().validate(imagePath, contentType, properties.maxRenderedPixels());
    BoundedProcessResult output =
        processRunner.run(
            List.of(
                properties.command(),
                imagePath.toString(),
                "stdout",
                "-l",
                properties.languages(),
                "tsv"),
            deadline.remaining(),
            properties.maxProcessOutputBytes(),
            STDERR_LIMIT_BYTES);
    if (output.exitCode() != 0) throw failure("OCR_PROCESS_FAILED", "OCR process failed");
    return new TesseractTsvParser(properties.maxProcessOutputBytes(), properties.maxWordsPerPage())
        .parse(output.stdout(), pageNumber);
  }

  private OcrPageResult boundedPage(List<OcrPageResult> pages, OcrPageResult page) {
    long existingWords = pages.stream().mapToLong(value -> value.words().size()).sum();
    long words;
    try {
      words = Math.addExact(existingWords, page.words().size());
    } catch (ArithmeticException exception) {
      throw failure("OCR_WORD_LIMIT", "OCR result exceeded the configured word limit");
    }
    if (words > properties.maxWordsPerDocument()) {
      throw failure("OCR_WORD_LIMIT", "OCR result exceeded the configured word limit");
    }
    return page;
  }

  private void verifyDocumentWordLimit(List<OcrPageResult> pages) {
    long wordCount = 0;
    for (OcrPageResult page : pages) {
      try {
        wordCount = Math.addExact(wordCount, page.words().size());
      } catch (ArithmeticException exception) {
        throw failure("OCR_WORD_LIMIT", "OCR result exceeded the configured word limit");
      }
      if (wordCount > properties.maxWordsPerDocument()) {
        throw failure("OCR_WORD_LIMIT", "OCR result exceeded the configured word limit");
      }
    }
  }

  private void deleteRendered(Path rendered) {
    try {
      Files.deleteIfExists(rendered);
    } catch (IOException exception) {
      throw failure(
          "OCR_TEMPORARY_FILE_CLEANUP_FAILED", "OCR temporary files could not be cleaned");
    }
  }

  private OcrProviderException failure(String code, String summary) {
    return new OcrProviderException(code, summary);
  }
}
