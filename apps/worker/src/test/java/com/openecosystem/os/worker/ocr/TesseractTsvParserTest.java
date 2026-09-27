package com.openecosystem.os.worker.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TesseractTsvParserTest {

  private static final String HEADER =
      "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf"
          + "\ttext\n";

  @Test
  void parsesRowsAndPreservesOrderingConfidenceIdentifiersAndBoxes() {
    String tsv =
        HEADER
            + "1\t1\t0\t0\t0\t0\t0\t0\t100\t100\t-1\t\n"
            + "5\t1\t2\t3\t4\t1\t10\t20\t30\t11\t95.126\tHello\n"
            + "5\t1\t2\t3\t4\t2\t45\t20\t40\t11\t88.5\tworld\n"
            + "5\t1\t2\t3\t5\t1\t10\t40\t35\t12\t-1\tAgain\n";

    OcrPageResult page = parser(4096).parse(tsv.getBytes(StandardCharsets.UTF_8), 7);

    assertThat(page.pageNumber()).isEqualTo(7);
    assertThat(page.sourceKind()).isEqualTo(OcrSourceKind.TESSERACT_TSV);
    assertThat(page.pageText()).isEqualTo("Hello world\nAgain");
    assertThat(page.words()).hasSize(3);

    OcrWord first = page.words().getFirst();
    assertThat(first.readingOrder()).isEqualTo(1);
    assertThat(first.pageWordOrder()).isEqualTo(1);
    assertThat(first.pageNumber()).isEqualTo(7);
    assertThat(first.blockNumber()).isEqualTo(2);
    assertThat(first.paragraphNumber()).isEqualTo(3);
    assertThat(first.lineNumber()).isEqualTo(4);
    assertThat(first.wordNumber()).isEqualTo(1);
    assertThat(first.text()).isEqualTo("Hello");
    assertThat(first.confidence()).isEqualByComparingTo("95.13");
    assertThat(first.boundingBox()).isEqualTo(new OcrBoundingBox(10, 20, 30, 11));

    assertThat(page.words()).extracting(OcrWord::readingOrder).containsExactly(1, 2, 3);
    assertThat(page.words()).extracting(OcrWord::text).containsExactly("Hello", "world", "Again");
    assertThat(page.words().get(2).confidence()).isNull();
  }

  @Test
  void ignoresNonWordRowsAndBlankWordRowsWhenReconstructingText() {
    String tsv =
        HEADER
            + "4\t1\t1\t1\t1\t0\t0\t0\t100\t10\t-1\t\n"
            + "5\t1\t1\t1\t1\t1\t5\t5\t20\t8\t90\tOne\n"
            + "5\t1\t1\t1\t1\t2\t30\t5\t20\t8\t85\t   \n"
            + "5\t1\t1\t2\t2\t1\t5\t20\t20\t8\t80\tTwo\n";

    OcrPageResult page = parser(4096).parse(tsv.getBytes(StandardCharsets.UTF_8), 2);

    assertThat(page.pageText()).isEqualTo("One\nTwo");
    assertThat(page.words()).extracting(OcrWord::text).containsExactly("One", "Two");
    assertThat(page.words()).extracting(OcrWord::pageWordOrder).containsExactly(1, 2);
  }

  @Test
  void rejectsMalformedRowsWithFixedSafeFailure() {
    String malformed = HEADER + "5\t1\t1\t1\t1\t1\tsecret-path\n";

    assertThatThrownBy(() -> parser(4096).parse(malformed.getBytes(StandardCharsets.UTF_8), 1))
        .isInstanceOf(OcrProviderException.class)
        .hasMessage("OCR output was invalid")
        .extracting(exception -> ((OcrProviderException) exception).code())
        .isEqualTo("OCR_TSV_INVALID");
  }

  @Test
  void rejectsOversizedInputBeforeParsingWithFixedSafeFailure() {
    byte[] oversized = (HEADER + "x".repeat(256)).getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> parser(HEADER.length()).parse(oversized, 1))
        .isInstanceOf(OcrProviderException.class)
        .hasMessage("OCR output exceeded the configured limit")
        .extracting(exception -> ((OcrProviderException) exception).code())
        .isEqualTo("OCR_TSV_TOO_LARGE");
  }

  @Test
  void rejectsTheNextWordBeforeAppendingBeyondThePageLimit() {
    String tsv =
        HEADER
            + "5\t1\t1\t1\t1\t1\t1\t1\t1\t1\t90\tOne\n"
            + "5\t1\t1\t1\t1\t2\t1\t1\t1\t1\t90\tTwo\n"
            + "5\t1\t1\t1\t1\t3\t1\t1\t1\t1\t90\tThree\n";

    assertThatThrownBy(
            () -> new TesseractTsvParser(4096, 2).parse(tsv.getBytes(StandardCharsets.UTF_8), 1))
        .isInstanceOf(OcrProviderException.class)
        .hasMessage("OCR result exceeded the configured word limit")
        .extracting(exception -> ((OcrProviderException) exception).code())
        .isEqualTo("OCR_WORD_LIMIT");
  }

  private TesseractTsvParser parser(int maxBytes) {
    return new TesseractTsvParser(maxBytes);
  }
}
