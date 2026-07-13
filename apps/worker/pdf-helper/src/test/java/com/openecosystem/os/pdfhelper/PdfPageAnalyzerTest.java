package com.openecosystem.os.pdfhelper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfPageAnalyzerTest {

  @TempDir Path temporaryRoot;

  @Test
  void scannedPageWithSubstantialRasterAndSparseFooterChoosesTesseract() throws Exception {
    PdfHelperResponse page =
        new PdfPageAnalyzer(200, 20_000_000)
            .analyze(PdfFixtures.scannedWithFooter(temporaryRoot), 1, temporaryRoot.resolve("page-1.png"));

    assertThat(page.decision()).isEqualTo(PdfHelperResponse.PageDecision.TESSERACT);
    assertThat(page.words()).isEmpty();
  }

  @Test
  void bornDigitalPagePreservesMultiWordLinesAndPixelBoxes() throws Exception {
    PdfHelperResponse page =
        new PdfPageAnalyzer(200, 20_000_000)
            .analyze(
                PdfFixtures.bornDigitalMultiline(temporaryRoot),
                1,
                temporaryRoot.resolve("page-1.png"));

    assertThat(page.decision()).isEqualTo(PdfHelperResponse.PageDecision.NATIVE);
    assertThat(page.words()).extracting(PdfHelperResponse.NativeWord::text)
        .containsSequence("Invoice", "Number", "INV-2026-42");
    assertThat(page.words().stream().map(PdfHelperResponse.NativeWord::lineNumber).distinct())
        .hasSizeGreaterThan(1);
    assertThat(page.words()).allMatch(word -> word.boundingBox() != null);
  }
}
