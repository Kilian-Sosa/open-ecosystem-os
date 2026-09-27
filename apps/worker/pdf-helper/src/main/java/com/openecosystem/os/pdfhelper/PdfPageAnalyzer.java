package com.openecosystem.os.pdfhelper;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.PDFRenderer;

public final class PdfPageAnalyzer {

  private final int dpi;
  private final long maxRenderedPixels;

  public PdfPageAnalyzer(int dpi, long maxRenderedPixels) {
    this.dpi = dpi;
    this.maxRenderedPixels = maxRenderedPixels;
  }

  public int inspect(Path input) throws IOException {
    try (PDDocument document = Loader.loadPDF(input.toFile())) {
      return document.getNumberOfPages();
    }
  }

  public PdfHelperResponse analyze(Path input, int pageNumber, Path renderedOutput) throws IOException {
    try (PDDocument document = Loader.loadPDF(input.toFile())) {
      if (pageNumber <= 0 || pageNumber > document.getNumberOfPages()) {
        throw new IOException("Invalid PDF page");
      }
      PDPage page = document.getPage(pageNumber - 1);
      NativePdfWordExtractor extractor = new NativePdfWordExtractor(dpi, page, pageNumber);
      List<PdfHelperResponse.NativeWord> words = extractor.extract(document);
      if (useNative(page, words)) {
        return PdfHelperResponse.nativePage(pageText(words), words);
      }
      render(document, pageNumber - 1, renderedOutput);
      return PdfHelperResponse.tesseractPage();
    }
  }

  private boolean useNative(PDPage page, List<PdfHelperResponse.NativeWord> words) throws IOException {
    Quality quality = Quality.from(words, dpi, page.getCropBox().getHeight());
    boolean minimallyUsable =
        quality.alphanumericCharacters >= 20 && quality.usableTokens >= 3 && quality.malformedRatio <= .02D;
    boolean completeBesideRaster =
        quality.alphanumericCharacters >= 80
            && quality.usableTokens >= 12
            && quality.nonEmptyLines >= 3
            && quality.verticalSpan >= .20D
            && quality.malformedRatio <= .02D;
    return minimallyUsable && (!RasterCoverageAnalyzer.hasSubstantialRaster(page) || completeBesideRaster);
  }

  private void render(PDDocument document, int pageIndex, Path output) throws IOException {
    BufferedImage image = new PDFRenderer(document).renderImageWithDPI(pageIndex, dpi);
    try {
      long pixels = Math.multiplyExact((long) image.getWidth(), image.getHeight());
      if (pixels > maxRenderedPixels || !ImageIO.write(image, "png", output.toFile())) {
        throw new IOException("PDF render unavailable");
      }
    } catch (ArithmeticException exception) {
      throw new IOException("PDF render unavailable", exception);
    }
  }

  private static String pageText(List<PdfHelperResponse.NativeWord> words) {
    StringBuilder text = new StringBuilder();
    int currentLine = -1;
    for (PdfHelperResponse.NativeWord word : words) {
      if (currentLine != -1) text.append(currentLine == word.lineNumber() ? ' ' : '\n');
      text.append(word.text());
      currentLine = word.lineNumber();
    }
    return text.toString();
  }

  private static final class Quality {
    private final long alphanumericCharacters;
    private final int usableTokens;
    private final int nonEmptyLines;
    private final double malformedRatio;
    private final double verticalSpan;

    private Quality(long alphanumericCharacters, int usableTokens, int nonEmptyLines, double malformedRatio, double verticalSpan) {
      this.alphanumericCharacters = alphanumericCharacters;
      this.usableTokens = usableTokens;
      this.nonEmptyLines = nonEmptyLines;
      this.malformedRatio = malformedRatio;
      this.verticalSpan = verticalSpan;
    }

    private static Quality from(List<PdfHelperResponse.NativeWord> words, int dpi, float pageHeight) {
      long alphanumeric = 0;
      long malformed = 0;
      long nonWhitespace = 0;
      int usable = 0;
      java.util.Set<Integer> lines = new java.util.HashSet<>();
      int minTop = Integer.MAX_VALUE;
      int maxBottom = Integer.MIN_VALUE;
      for (PdfHelperResponse.NativeWord word : words) {
        long tokenCharacters = word.text().codePoints().filter(Character::isLetterOrDigit).count();
        alphanumeric += tokenCharacters;
        if (tokenCharacters >= 2) usable++;
        lines.add(word.lineNumber());
        for (int index = 0; index < word.text().length(); index++) {
          char character = word.text().charAt(index);
          if (!Character.isWhitespace(character)) {
            nonWhitespace++;
            if (Character.isISOControl(character)
                || character == '\uFFFD'
                || Character.isSurrogate(character)
                || Character.getType(character) == Character.PRIVATE_USE) malformed++;
          }
        }
        if (word.boundingBox() != null) {
          minTop = Math.min(minTop, word.boundingBox().top());
          maxBottom = Math.max(maxBottom, word.boundingBox().top() + word.boundingBox().height());
        }
      }
      double pagePixels = Math.max(1D, pageHeight * dpi / 72D);
      return new Quality(
          alphanumeric,
          usable,
          lines.size(),
          nonWhitespace == 0 ? 0D : (double) malformed / nonWhitespace,
          minTop == Integer.MAX_VALUE ? 0D : (maxBottom - minTop) / pagePixels);
    }
  }
}
