package com.openecosystem.os.pdfhelper;

import java.util.List;

public record PdfHelperResponse(
    int protocolVersion, Integer pageCount, PageDecision decision, String pageText, List<NativeWord> words) {

  public PdfHelperResponse {
    if (protocolVersion != 1) {
      throw new IllegalArgumentException("Unsupported PDF helper protocol version");
    }
    words = words == null ? List.of() : List.copyOf(words);
  }

  static PdfHelperResponse inspection(int pageCount) {
    return new PdfHelperResponse(1, pageCount, null, null, List.of());
  }

  static PdfHelperResponse nativePage(String pageText, List<NativeWord> words) {
    return new PdfHelperResponse(1, null, PageDecision.NATIVE, pageText, words);
  }

  static PdfHelperResponse tesseractPage() {
    return new PdfHelperResponse(1, null, PageDecision.TESSERACT, null, List.of());
  }

  public enum PageDecision {
    NATIVE,
    TESSERACT
  }

  public record NativeWord(
      int blockNumber,
      int paragraphNumber,
      int lineNumber,
      int wordNumber,
      int pageWordOrder,
      String text,
      BoundingBox boundingBox) {}

  public record BoundingBox(int left, int top, int width, int height) {}
}
