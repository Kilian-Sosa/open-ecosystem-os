package com.openecosystem.os.worker.ocr;

import java.util.List;

public record PdfHelperPage(OcrSourceKind sourceKind, String pageText, List<OcrWord> words) {
  public PdfHelperPage {
    if (sourceKind == null
        || (sourceKind == OcrSourceKind.PDF_TEXT_LAYER && (pageText == null || words == null))) {
      throw new IllegalArgumentException("PDF helper page was invalid");
    }
    words = words == null ? List.of() : List.copyOf(words);
  }

  static PdfHelperPage tesseract() {
    return new PdfHelperPage(OcrSourceKind.TESSERACT_TSV, null, List.of());
  }
}
