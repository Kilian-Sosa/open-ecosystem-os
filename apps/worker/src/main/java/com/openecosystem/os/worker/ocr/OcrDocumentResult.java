package com.openecosystem.os.worker.ocr;

import java.util.List;

public record OcrDocumentResult(
    String provider, String providerVersion, String documentText, List<OcrPageResult> pages) {

  public OcrDocumentResult {
    if (provider == null
        || provider.isBlank()
        || providerVersion == null
        || providerVersion.isBlank()) {
      throw new IllegalArgumentException("OCR provider identity is required");
    }
    pages = pages == null ? List.of() : List.copyOf(pages);
    if (pages.isEmpty() || pages.stream().anyMatch(page -> page == null)) {
      throw new IllegalArgumentException("OCR result must contain pages");
    }
    pages = withDocumentReadingOrder(pages);
    documentText = documentText == null ? "" : documentText;
  }

  public static OcrDocumentResult of(
      String provider, String providerVersion, List<OcrPageResult> pages) {
    return new OcrDocumentResult(
        provider,
        providerVersion,
        pages.stream()
            .map(OcrPageResult::pageText)
            .filter(text -> !text.isBlank())
            .reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right),
        pages);
  }

  private static List<OcrPageResult> withDocumentReadingOrder(List<OcrPageResult> pages) {
    int documentReadingOrder = 1;
    var orderedPages = new java.util.ArrayList<OcrPageResult>(pages.size());
    for (OcrPageResult page : pages) {
      var orderedWords = new java.util.ArrayList<OcrWord>(page.words().size());
      for (OcrWord word : page.words()) {
        orderedWords.add(
            new OcrWord(
                documentReadingOrder++,
                word.pageNumber(),
                word.pageWordOrder(),
                word.blockNumber(),
                word.paragraphNumber(),
                word.lineNumber(),
                word.wordNumber(),
                word.text(),
                word.confidence(),
                word.boundingBox()));
      }
      orderedPages.add(
          new OcrPageResult(page.pageNumber(), page.sourceKind(), page.pageText(), orderedWords));
    }
    return List.copyOf(orderedPages);
  }
}
