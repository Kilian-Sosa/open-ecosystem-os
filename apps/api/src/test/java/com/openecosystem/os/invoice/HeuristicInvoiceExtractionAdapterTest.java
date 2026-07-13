package com.openecosystem.os.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.openecosystem.os.media.OcrDocumentResult;
import com.openecosystem.os.media.OcrPageResult;
import com.openecosystem.os.media.OcrWord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HeuristicInvoiceExtractionAdapterTest {

  private static final Instant NOW = Instant.parse("2026-07-11T12:00:00Z");

  @Test
  void extractsSpatiallyAdjacentLabelledValuesWithProvenance() {
    InvoiceExtraction extraction =
        new HeuristicInvoiceExtractionAdapter().extract(request(), completeStructuredInvoice());

    assertThat(extraction.status()).isEqualTo(InvoiceExtractionStatus.COMPLETED);
    assertThat(extraction.fields())
        .extracting(InvoiceExtractionField::fieldKey)
        .containsExactlyInAnyOrder(
            "invoice_number",
            "supplier_name",
            "supplier_tax_id",
            "supplier_iban",
            "subtotal_amount",
            "tax_amount",
            "total_amount",
            "currency",
            "issue_date",
            "due_date");
    assertThat(field(extraction, "invoice_number").normalizedValue()).isEqualTo("INV-2026-42");
    assertThat(field(extraction, "total_amount").normalizedValue()).isEqualTo("121.00");
    assertThat(field(extraction, "supplier_iban").normalizedValue())
        .isEqualTo("ES9121000418450200051332");
    assertThat(field(extraction, "total_amount").sources())
        .extracting(InvoiceExtractionFieldSource::sourceRole)
        .contains("label", "value");
    assertThat(extraction.aggregateConfidence()).isEqualByComparingTo("92.00");
  }

  @Test
  void prefersTheMostSpecificOverlappingLabelAtTheSameOffset() {
    InvoiceExtraction extraction =
        new HeuristicInvoiceExtractionAdapter()
            .extract(
                request(),
                document(
                    List.of(
                        words(1, "Invoice", "Number", "INV-2026-42"),
                        words(2, "Supplier", "Name", "Acme", "Limited"))));

    assertThat(field(extraction, "invoice_number").normalizedValue()).isEqualTo("INV-2026-42");
    assertThat(field(extraction, "supplier_name").normalizedValue()).isEqualTo("Acme Limited");
    assertThat(extraction.warnings())
        .extracting(InvoiceExtractionWarning::code)
        .doesNotContain(
            "ambiguous_invoice_number",
            "missing_invoice_number",
            "ambiguous_supplier_name",
            "missing_supplier_name");
  }

  @Test
  void marksMissingOrInvalidLabelledValuesForReviewWithoutFabricatingFields() {
    OcrDocumentResult incomplete =
        document(
            List.of(
                words(1, "Invoice", "INV-2026-42"),
                words(2, "IBAN", "ES001234"),
                words(3, "Total", "not-an-amount")));

    InvoiceExtraction extraction =
        new HeuristicInvoiceExtractionAdapter().extract(request(), incomplete);

    assertThat(extraction.status()).isEqualTo(InvoiceExtractionStatus.REVIEW_REQUIRED);
    assertThat(extraction.fields())
        .extracting(InvoiceExtractionField::fieldKey)
        .containsExactly("invoice_number");
    assertThat(extraction.warnings())
        .extracting(InvoiceExtractionWarning::code)
        .contains("invalid_supplier_iban", "invalid_total_amount", "missing_supplier_name");
    assertThat(extraction.warnings())
        .extracting(InvoiceExtractionWarning::message)
        .allMatch(message -> !message.contains("ES001234") && !message.contains("not-an-amount"));
  }

  @Test
  void prefersTheNearestSameLineEvidenceWhenValuesNormalizeTheSame() {
    OcrWord label = word("ocrw_label", 1, 0, 0, "Total");
    OcrWord distantValue = word("ocrw_distant", 1, 1, 900, "121,00");
    OcrWord nearbyValue = word("ocrw_nearby", 1, 2, 100, "121.00");
    OcrDocumentResult result = document(List.of(List.of(label, distantValue, nearbyValue)));

    InvoiceExtraction extraction =
        new HeuristicInvoiceExtractionAdapter().extract(request(), result);

    assertThat(field(extraction, "total_amount").normalizedValue()).isEqualTo("121.00");
    assertThat(field(extraction, "total_amount").sources())
        .extracting(InvoiceExtractionFieldSource::ocrWordId)
        .contains("ocrw_nearby");
  }

  @Test
  void doesNotFabricateAnInvoiceNumberFromAnUnlabelledHeaderValue() {
    InvoiceExtraction extraction =
        new HeuristicInvoiceExtractionAdapter()
            .extract(request(), document(List.of(words(1, "INV-2026-42"))));

    assertThat(extraction.fields()).isEmpty();
    assertThat(extraction.warnings())
        .extracting(InvoiceExtractionWarning::code)
        .contains("missing_invoice_number");
  }

  @Test
  void rejectsAnIbanWithAnInvalidChecksumWithoutPersistingTheValue() {
    InvoiceExtraction extraction =
        new HeuristicInvoiceExtractionAdapter()
            .extract(request(), document(List.of(words(1, "IBAN", "ES9121000418450200051333"))));

    assertThat(extraction.fields()).isEmpty();
    assertThat(extraction.warnings())
        .extracting(InvoiceExtractionWarning::code)
        .contains("invalid_supplier_iban");
    assertThat(extraction.warnings())
        .extracting(InvoiceExtractionWarning::message)
        .allMatch(message -> !message.contains("ES9121000418450200051333"));
  }

  @Test
  void requiresReviewWhenInvoiceTotalsDoNotBalance() {
    InvoiceExtraction extraction =
        new HeuristicInvoiceExtractionAdapter()
            .extract(
                request(),
                document(
                    List.of(
                        words(1, "Subtotal", "100.00"),
                        words(2, "Tax", "21.00"),
                        words(3, "Total", "122.00"))));

    assertThat(extraction.status()).isEqualTo(InvoiceExtractionStatus.REVIEW_REQUIRED);
    assertThat(extraction.warnings())
        .extracting(InvoiceExtractionWarning::code)
        .contains("arithmetic_mismatch");
  }

  @Test
  void marksAValidLowConfidenceFieldForReview() {
    InvoiceExtraction extraction =
        new HeuristicInvoiceExtractionAdapter()
            .extract(
                request(),
                document(List.of(words(1, new BigDecimal("59.00"), "Invoice", "INV-2026-42"))));

    assertThat(field(extraction, "invoice_number").status())
        .isEqualTo(InvoiceFieldStatus.LOW_CONFIDENCE);
    assertThat(extraction.warnings())
        .extracting(InvoiceExtractionWarning::code)
        .contains("low_confidence_invoice_number");
  }

  @Test
  void requiresReviewWhenEqualCandidatesMakeALabelledFieldAmbiguous() {
    InvoiceExtraction extraction =
        new HeuristicInvoiceExtractionAdapter()
            .extract(
                request(),
                document(List.of(words(1, "Total", "121.00"), words(2, "Total", "122.00"))));

    assertThat(extraction.fields())
        .extracting(InvoiceExtractionField::fieldKey)
        .doesNotContain("total_amount");
    assertThat(extraction.warnings())
        .extracting(InvoiceExtractionWarning::code)
        .contains("ambiguous_total_amount");
  }

  @Test
  void marksDifferentlySpacedDistinctLabelledTotalsAsAmbiguous() {
    InvoiceExtraction extraction =
        new HeuristicInvoiceExtractionAdapter()
            .extract(
                request(),
                document(
                    List.of(
                        List.of(
                            word("ocrw_total_label_near", 1, 0, 10, "Total"),
                            word("ocrw_total_value_near", 1, 1, 100, "121.00")),
                        List.of(
                            word("ocrw_total_label_distant", 2, 0, 10, "Total"),
                            word("ocrw_total_value_distant", 2, 1, 800, "122.00")))));

    assertThat(extraction.status()).isEqualTo(InvoiceExtractionStatus.REVIEW_REQUIRED);
    assertThat(extraction.fields())
        .extracting(InvoiceExtractionField::fieldKey)
        .doesNotContain("total_amount");
    assertThat(extraction.warnings())
        .extracting(InvoiceExtractionWarning::code)
        .contains("ambiguous_total_amount")
        .doesNotContain("missing_total_amount");
    assertThat(extraction.warnings())
        .extracting(InvoiceExtractionWarning::message)
        .allMatch(message -> !message.contains("121.00") && !message.contains("122.00"));
  }

  @Test
  void selectsRepeatedLabelledEvidenceWhenTheNormalizedTotalIsTheSame() {
    InvoiceExtraction extraction =
        new HeuristicInvoiceExtractionAdapter()
            .extract(
                request(),
                document(
                    List.of(
                        List.of(
                            word("ocrw_total_label_near", 1, 0, 10, "Total"),
                            word("ocrw_total_value_near", 1, 1, 100, "121.00")),
                        List.of(
                            word("ocrw_total_label_distant", 2, 0, 10, "Total"),
                            word("ocrw_total_value_distant", 2, 1, 800, "121,00")))));

    assertThat(field(extraction, "total_amount").normalizedValue()).isEqualTo("121.00");
    assertThat(extraction.warnings())
        .extracting(InvoiceExtractionWarning::code)
        .doesNotContain("ambiguous_total_amount");
  }

  @ParameterizedTest
  @CsvSource({
    "USD, USD",
    "CAD, CAD",
    "AUD, AUD",
    "EUR, EUR",
    "GBP, GBP",
    "US$, USD",
    "CA$, CAD",
    "C$, CAD",
    "AU$, AUD",
    "A$, AUD",
    "NZ$, NZD",
    "€, EUR",
    "£, GBP"
  })
  void acceptsOnlyApprovedExplicitCurrencyEvidence(String evidence, String expectedCurrency) {
    InvoiceExtraction extraction =
        new HeuristicInvoiceExtractionAdapter()
            .extract(request(), document(List.of(words(1, "Currency", evidence))));

    assertThat(field(extraction, "currency").normalizedValue()).isEqualTo(expectedCurrency);
  }

  @Test
  void treatsALabelledBareDollarAsAmbiguousWithoutFabricatingCurrency() {
    InvoiceExtraction extraction =
        new HeuristicInvoiceExtractionAdapter()
            .extract(request(), document(List.of(words(1, "Currency", "$"))));

    assertThat(extraction.status()).isEqualTo(InvoiceExtractionStatus.REVIEW_REQUIRED);
    assertThat(extraction.fields())
        .extracting(InvoiceExtractionField::fieldKey)
        .doesNotContain("currency");
    assertThat(extraction.warnings())
        .extracting(InvoiceExtractionWarning::code)
        .contains("ambiguous_currency")
        .doesNotContain("missing_currency", "invalid_currency");
    assertThat(extraction.warnings())
        .extracting(InvoiceExtractionWarning::message)
        .allMatch(message -> !message.contains("$") && !message.contains("USD"));
  }

  @Test
  void rejectsUnlistedCurrencyMarkersWithoutMappingThem() {
    InvoiceExtraction extraction =
        new HeuristicInvoiceExtractionAdapter()
            .extract(request(), document(List.of(words(1, "Currency", "¥"))));

    assertThat(extraction.fields())
        .extracting(InvoiceExtractionField::fieldKey)
        .doesNotContain("currency");
    assertThat(extraction.warnings())
        .extracting(InvoiceExtractionWarning::message)
        .allMatch(message -> !message.contains("¥") && !message.contains("JPY"));
  }

  private InvoiceExtractionRequest request() {
    return new InvoiceExtractionRequest(
        "wrk_test",
        "ocrr_test",
        "ocr_test",
        "file_test",
        "wfe_test",
        "usr_test",
        "corr_test",
        "evt_test");
  }

  private OcrDocumentResult completeStructuredInvoice() {
    return document(
        List.of(
            words(1, "Invoice", "INV-2026-42"),
            words(2, "Supplier", "Acme", "Limited"),
            words(3, "Tax", "ID", "B12345678"),
            words(4, "IBAN", "ES9121000418450200051332"),
            words(5, "Subtotal", "100.00"),
            words(6, "Tax", "21.00"),
            words(7, "Total", "121.00"),
            words(8, "Currency", "EUR"),
            words(9, "Issue", "date", "2026-07-01"),
            words(10, "Due", "date", "2026-07-31")));
  }

  private OcrDocumentResult document(List<List<OcrWord>> lines) {
    List<OcrWord> words = lines.stream().flatMap(List::stream).toList();
    OcrPageResult page =
        new OcrPageResult(
            "ocrp_test",
            "ocrr_test",
            "wrk_test",
            1,
            "tesseract_tsv",
            words.stream()
                .map(OcrWord::wordText)
                .reduce((left, right) -> left + " " + right)
                .orElse(""),
            words.size(),
            NOW,
            words);
    return new OcrDocumentResult(
        "ocrr_test",
        "ocr_test",
        "file_test",
        "wrk_test",
        "tesseract",
        "5.5.0",
        page.pageText(),
        1,
        words.size(),
        NOW,
        NOW,
        List.of(page));
  }

  private List<OcrWord> words(int line, String... text) {
    return words(line, new BigDecimal("92.00"), text);
  }

  private List<OcrWord> words(int line, BigDecimal confidence, String... text) {
    return java.util.stream.IntStream.range(0, text.length)
        .mapToObj(
            index ->
                new OcrWord(
                    "ocrw_" + line + "_" + index,
                    "ocrp_test",
                    "ocrr_test",
                    "wrk_test",
                    line * 100 + index,
                    1,
                    index,
                    0,
                    0,
                    line,
                    index,
                    text[index],
                    confidence,
                    index * 100,
                    line * 20,
                    80,
                    18,
                    "tesseract_tsv",
                    NOW))
        .toList();
  }

  private OcrWord word(String id, int line, int order, int left, String text) {
    return new OcrWord(
        id,
        "ocrp_test",
        "ocrr_test",
        "wrk_test",
        line * 100 + order,
        1,
        order,
        0,
        0,
        line,
        order,
        text,
        new BigDecimal("92.00"),
        left,
        line * 20,
        80,
        18,
        "tesseract_tsv",
        NOW);
  }

  private InvoiceExtractionField field(InvoiceExtraction extraction, String key) {
    return extraction.fields().stream()
        .filter(field -> field.fieldKey().equals(key))
        .findFirst()
        .orElseThrow();
  }
}
