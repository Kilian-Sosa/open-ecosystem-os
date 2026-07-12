package com.openecosystem.os.invoice;

import com.openecosystem.os.common.ids.Ids;
import com.openecosystem.os.media.OcrDocumentResult;
import com.openecosystem.os.media.OcrPageResult;
import com.openecosystem.os.media.OcrWord;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class HeuristicInvoiceExtractionAdapter implements InvoiceExtractionPort {

  private static final String EXTRACTOR_NAME = "heuristic_invoice";
  private static final String EXTRACTOR_VERSION = "1";
  private static final BigDecimal LOW_CONFIDENCE = new BigDecimal("60.00");
  private static final BigDecimal ARITHMETIC_TOLERANCE = new BigDecimal("0.02");
  private static final Pattern INVOICE_IDENTIFIER =
      Pattern.compile("(?i)^[A-Z0-9][A-Z0-9/_-]{2,63}$");
  private static final Pattern TAX_IDENTIFIER = Pattern.compile("(?i)^[A-Z0-9][A-Z0-9.-]{6,19}$");
  private static final Pattern AMOUNT =
      Pattern.compile(
          "(?<![\\p{Alnum}])([+-]?(?:\\d{1,3}(?:[.,"
              + " ]\\d{3})+|\\d+)(?:[.,]\\d{2})?)(?![\\p{Alnum}])");
  private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final DateTimeFormatter DMY_DATE =
      DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
  private static final List<FieldDefinition> FIELD_DEFINITIONS =
      List.of(
          new FieldDefinition(
              "invoice_number",
              List.of(
                  "invoice number",
                  "invoice no",
                  "invoice",
                  "numero de factura",
                  "número de factura",
                  "factura")),
          new FieldDefinition("supplier_name", List.of("supplier name", "supplier", "proveedor")),
          new FieldDefinition("supplier_tax_id", List.of("tax id", "vat number", "nif", "cif")),
          new FieldDefinition("supplier_iban", List.of("iban")),
          new FieldDefinition("subtotal_amount", List.of("subtotal", "base imponible")),
          new FieldDefinition("tax_amount", List.of("tax amount", "iva", "vat", "tax")),
          new FieldDefinition("total_amount", List.of("total amount", "total")),
          new FieldDefinition("currency", List.of("currency", "moneda")),
          new FieldDefinition(
              "issue_date",
              List.of("issue date", "invoice date", "fecha de emision", "fecha de emisión")),
          new FieldDefinition(
              "due_date", List.of("due date", "payment due", "fecha de vencimiento")));

  @Override
  public InvoiceExtraction extract(InvoiceExtractionRequest request, OcrDocumentResult result) {
    requireMatchingLineage(request, result);
    Instant now = Instant.now();
    String extractionId = Ids.newId("invx");
    List<InvoiceExtractionField> fields = new ArrayList<>();
    Map<String, InvoiceExtractionWarning> warnings = new LinkedHashMap<>();
    for (FieldDefinition definition : FIELD_DEFINITIONS) {
      resolveField(definition, result)
          .ifPresentOrElse(
              candidate ->
                  fields.add(
                      toField(extractionId, request, definition.key(), candidate, now, warnings)),
              () ->
                  warnings.putIfAbsent(
                      "missing_" + definition.key(), missingWarning(definition.key())));
      if (hasInvalidLabelledCandidate(definition, result)) {
        warnings.putIfAbsent("invalid_" + definition.key(), invalidWarning(definition.key()));
        fields.removeIf(field -> field.fieldKey().equals(definition.key()));
      }
      if (hasAmbiguousCandidates(definition, result)) {
        warnings.putIfAbsent("ambiguous_" + definition.key(), ambiguousWarning(definition.key()));
        fields.removeIf(field -> field.fieldKey().equals(definition.key()));
      }
    }
    validateArithmetic(fields, warnings);
    BigDecimal aggregateConfidence = aggregateConfidence(fields);
    InvoiceExtractionStatus status =
        warnings.isEmpty()
            ? InvoiceExtractionStatus.COMPLETED
            : InvoiceExtractionStatus.REVIEW_REQUIRED;
    return new InvoiceExtraction(
        extractionId,
        request.workflowExecutionId(),
        result.ocrResultId(),
        result.jobId(),
        result.fileId(),
        request.workspaceId(),
        EXTRACTOR_NAME,
        EXTRACTOR_VERSION,
        status,
        List.copyOf(warnings.values()),
        aggregateConfidence,
        fields,
        now,
        now);
  }

  private void requireMatchingLineage(InvoiceExtractionRequest request, OcrDocumentResult result) {
    if (!request.workspaceId().equals(result.workspaceId())
        || !request.ocrResultId().equals(result.ocrResultId())
        || !request.ocrJobId().equals(result.jobId())
        || !request.fileId().equals(result.fileId())) {
      throw new IllegalArgumentException("Invoice extraction requires matching OCR lineage");
    }
  }

  private Optional<Candidate> resolveField(FieldDefinition definition, OcrDocumentResult result) {
    List<Candidate> candidates =
        candidates(definition, result).stream().filter(this::isValid).toList();
    if (candidates.isEmpty()) return Optional.empty();
    int bestScore = candidates.stream().mapToInt(Candidate::score).min().orElseThrow();
    List<Candidate> best =
        candidates.stream().filter(candidate -> candidate.score() == bestScore).toList();
    return best.size() == 1 ? Optional.of(best.getFirst()) : Optional.empty();
  }

  private boolean hasInvalidLabelledCandidate(
      FieldDefinition definition, OcrDocumentResult result) {
    List<Candidate> candidates = candidates(definition, result);
    return !candidates.isEmpty() && candidates.stream().noneMatch(this::isValid);
  }

  private boolean hasAmbiguousCandidates(FieldDefinition definition, OcrDocumentResult result) {
    List<Candidate> valid = candidates(definition, result).stream().filter(this::isValid).toList();
    if (valid.isEmpty()) return false;
    int bestScore = valid.stream().mapToInt(Candidate::score).min().orElseThrow();
    return valid.stream().filter(candidate -> candidate.score() == bestScore).count() > 1;
  }

  private List<Candidate> candidates(FieldDefinition definition, OcrDocumentResult result) {
    List<Line> lines = lines(result);
    List<Candidate> candidates = new ArrayList<>();
    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
      Line line = lines.get(lineIndex);
      for (List<String> label : definition.labelWords()) {
        for (int wordIndex = 0; wordIndex <= line.words().size() - label.size(); wordIndex++) {
          if (!matchesLabel(line.words(), wordIndex, label)
              || isExtendedTaxLabel(definition.key(), line.words(), wordIndex, label)
              || isStrictPrefixOfMatchedLabel(definition, line.words(), wordIndex, label)) {
            continue;
          }
          List<OcrWord> labelWords = line.words().subList(wordIndex, wordIndex + label.size());
          List<OcrWord> sameLineValues =
              line.words().subList(wordIndex + label.size(), line.words().size());
          if (!sameLineValues.isEmpty()) {
            candidateValueGroups(definition.key(), sameLineValues)
                .forEach(
                    values ->
                        candidates.add(
                            new Candidate(
                                definition.key(),
                                labelWords,
                                values,
                                sameLineDistance(labelWords, values))));
          } else if (lineIndex + 1 < lines.size() && lines.get(lineIndex + 1).samePage(line)) {
            candidates.add(
                new Candidate(
                    definition.key(),
                    labelWords,
                    lines.get(lineIndex + 1).words(),
                    1_000_000 + lines.get(lineIndex + 1).key().lineNumber()));
          }
        }
      }
    }
    return candidates;
  }

  private List<List<OcrWord>> candidateValueGroups(String key, List<OcrWord> values) {
    if (key.equals("supplier_name") || key.equals("supplier_iban")) return List.of(values);
    return values.stream().map(List::of).toList();
  }

  private int sameLineDistance(List<OcrWord> labelWords, List<OcrWord> valueWords) {
    OcrWord label = labelWords.getLast();
    OcrWord value = valueWords.getFirst();
    if (label.leftPx() != null && label.widthPx() != null && value.leftPx() != null) {
      return Math.max(0, value.leftPx() - label.leftPx() - label.widthPx());
    }
    return Math.max(0, value.pageWordOrder() - label.pageWordOrder());
  }

  private boolean matchesLabel(List<OcrWord> words, int offset, List<String> label) {
    for (int index = 0; index < label.size(); index++) {
      if (!normalized(words.get(offset + index).wordText()).equals(label.get(index))) return false;
    }
    return true;
  }

  private boolean isExtendedTaxLabel(
      String key, List<OcrWord> words, int offset, List<String> label) {
    return key.equals("tax_amount")
        && label.size() == 1
        && normalized(label.getFirst()).equals("tax")
        && offset + 1 < words.size()
        && normalized(words.get(offset + 1).wordText()).equals("id");
  }

  private boolean isStrictPrefixOfMatchedLabel(
      FieldDefinition definition, List<OcrWord> words, int offset, List<String> label) {
    return definition.labelWords().stream()
        .anyMatch(
            longerLabel ->
                longerLabel.size() > label.size()
                    && longerLabel.subList(0, label.size()).equals(label)
                    && offset + longerLabel.size() <= words.size()
                    && matchesLabel(words, offset, longerLabel));
  }

  private List<Line> lines(OcrDocumentResult result) {
    return result.pages().stream()
        .sorted(Comparator.comparingInt(OcrPageResult::pageNumber))
        .flatMap(page -> page.words().stream())
        .collect(
            java.util.stream.Collectors.groupingBy(
                word ->
                    new LineKey(
                        word.pageNumber(),
                        word.blockNumber(),
                        word.paragraphNumber(),
                        word.lineNumber()),
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()))
        .entrySet()
        .stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            entry ->
                new Line(
                    entry.getKey(),
                    entry.getValue().stream()
                        .sorted(Comparator.comparingInt(OcrWord::pageWordOrder))
                        .toList()))
        .toList();
  }

  private boolean isValid(Candidate candidate) {
    return switch (candidate.key()) {
      case "invoice_number" -> INVOICE_IDENTIFIER.matcher(candidate.text()).matches();
      case "supplier_name" ->
          candidate.text().matches(".*[\\p{L}].*") && candidate.text().length() >= 2;
      case "supplier_tax_id" -> TAX_IDENTIFIER.matcher(candidate.text().replace(" ", "")).matches();
      case "supplier_iban" -> isValidIban(candidate.text());
      case "subtotal_amount", "tax_amount", "total_amount" ->
          parseAmount(candidate.text()).isPresent();
      case "currency" -> currency(candidate.text()).isPresent();
      case "issue_date", "due_date" -> parseDate(candidate.text()).isPresent();
      default -> false;
    };
  }

  private InvoiceExtractionField toField(
      String extractionId,
      InvoiceExtractionRequest request,
      String key,
      Candidate candidate,
      Instant now,
      Map<String, InvoiceExtractionWarning> warnings) {
    String fieldId = Ids.newId("invf");
    List<OcrWord> valueWords = candidate.valueWords();
    String normalized = normalizedValue(key, candidate.text());
    BigDecimal confidence = confidence(valueWords);
    InvoiceFieldStatus status = InvoiceFieldStatus.EXTRACTED;
    if (confidence != null && confidence.compareTo(LOW_CONFIDENCE) < 0) {
      status = InvoiceFieldStatus.LOW_CONFIDENCE;
      warnings.putIfAbsent("low_confidence_" + key, lowConfidenceWarning(key));
    }
    OcrWord source = valueWords.getFirst();
    List<InvoiceExtractionFieldSource> sources = new ArrayList<>();
    int sourceOrder = 0;
    for (OcrWord labelWord : candidate.labelWords()) {
      sources.add(
          new InvoiceExtractionFieldSource(
              fieldId,
              request.ocrResultId(),
              request.workspaceId(),
              labelWord.ocrWordId(),
              "label",
              sourceOrder++));
    }
    for (OcrWord valueWord : valueWords) {
      sources.add(
          new InvoiceExtractionFieldSource(
              fieldId,
              request.ocrResultId(),
              request.workspaceId(),
              valueWord.ocrWordId(),
              "value",
              sourceOrder++));
    }
    return new InvoiceExtractionField(
        fieldId,
        extractionId,
        request.ocrResultId(),
        request.workspaceId(),
        key,
        candidate.text(),
        normalized,
        status,
        confidence,
        source.pageNumber(),
        source.blockNumber(),
        source.paragraphNumber(),
        source.lineNumber(),
        now,
        sources);
  }

  private String normalizedValue(String key, String text) {
    return switch (key) {
      case "subtotal_amount", "tax_amount", "total_amount" ->
          parseAmount(text).orElseThrow().toPlainString();
      case "currency" -> currency(text).orElseThrow();
      case "issue_date", "due_date" -> parseDate(text).orElseThrow().toString();
      case "supplier_iban" -> text.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
      case "supplier_tax_id" -> text.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
      default -> text.trim();
    };
  }

  private Optional<BigDecimal> parseAmount(String text) {
    Matcher matcher = AMOUNT.matcher(text);
    if (!matcher.find()) return Optional.empty();
    String amount = matcher.group(1).replace(" ", "");
    int comma = amount.lastIndexOf(',');
    int period = amount.lastIndexOf('.');
    if (comma >= 0 && period >= 0) {
      char decimal = Math.max(comma, period) == comma ? ',' : '.';
      amount = amount.replace(decimal == ',' ? "." : ",", "").replace(decimal, '.');
    } else if (comma >= 0 || period >= 0) {
      char separator = comma >= 0 ? ',' : '.';
      int position = amount.lastIndexOf(separator);
      if (amount.length() - position - 1 == 2) {
        amount = amount.replace(separator, '.');
      } else {
        amount = amount.replace(String.valueOf(separator), "");
      }
    }
    try {
      return Optional.of(new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP));
    } catch (NumberFormatException exception) {
      return Optional.empty();
    }
  }

  private Optional<String> currency(String text) {
    String upper = text.toUpperCase(Locale.ROOT);
    for (String token : upper.split("[^A-Z]+")) {
      if (token.length() != 3) continue;
      try {
        return Optional.of(Currency.getInstance(token).getCurrencyCode());
      } catch (IllegalArgumentException ignored) {
        // Continue searching an explicitly labelled candidate for a supported code.
      }
    }
    if (upper.contains("€")) return Optional.of("EUR");
    if (upper.contains("£")) return Optional.of("GBP");
    if (upper.contains("$") && !upper.contains("US$")) return Optional.of("USD");
    return Optional.empty();
  }

  private Optional<LocalDate> parseDate(String text) {
    try {
      return Optional.of(LocalDate.parse(text.trim(), ISO_DATE));
    } catch (DateTimeParseException ignored) {
      // Continue with an unambiguous day/month format.
    }
    Matcher matcher = Pattern.compile("^(\\d{2})/(\\d{2})/(\\d{4})$").matcher(text.trim());
    if (!matcher.matches()) return Optional.empty();
    int day = Integer.parseInt(matcher.group(1));
    int month = Integer.parseInt(matcher.group(2));
    if (day <= 12 && month <= 12) return Optional.empty();
    try {
      return Optional.of(LocalDate.parse(text.trim(), DMY_DATE));
    } catch (DateTimeParseException ignored) {
      return Optional.empty();
    }
  }

  private boolean isValidIban(String text) {
    String iban = text.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
    if (iban.length() < 15 || iban.length() > 34 || !iban.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]+")) {
      return false;
    }
    String rearranged = iban.substring(4) + iban.substring(0, 4);
    int remainder = 0;
    for (int index = 0; index < rearranged.length(); index++) {
      char character = rearranged.charAt(index);
      if (Character.isDigit(character)) {
        remainder = (remainder * 10 + Character.digit(character, 10)) % 97;
      } else {
        int value = character - 'A' + 10;
        remainder = (remainder * 10 + value / 10) % 97;
        remainder = (remainder * 10 + value % 10) % 97;
      }
    }
    return remainder == 1;
  }

  private BigDecimal confidence(List<OcrWord> words) {
    if (words.stream().anyMatch(word -> word.confidence() == null)) return null;
    return words.stream()
        .map(OcrWord::confidence)
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(words.size()), 2, RoundingMode.HALF_UP);
  }

  private BigDecimal aggregateConfidence(List<InvoiceExtractionField> fields) {
    List<BigDecimal> confidences = fields.stream().map(InvoiceExtractionField::confidence).toList();
    if (confidences.stream().anyMatch(confidence -> confidence == null) || confidences.isEmpty())
      return null;
    return confidences.stream()
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .divide(BigDecimal.valueOf(confidences.size()), 2, RoundingMode.HALF_UP);
  }

  private void validateArithmetic(
      List<InvoiceExtractionField> fields, Map<String, InvoiceExtractionWarning> warnings) {
    Optional<BigDecimal> subtotal = amount(fields, "subtotal_amount");
    Optional<BigDecimal> tax = amount(fields, "tax_amount");
    Optional<BigDecimal> total = amount(fields, "total_amount");
    if (subtotal.isPresent()
        && tax.isPresent()
        && total.isPresent()
        && subtotal.get().add(tax.get()).subtract(total.get()).abs().compareTo(ARITHMETIC_TOLERANCE)
            > 0) {
      warnings.putIfAbsent(
          "arithmetic_mismatch",
          new InvoiceExtractionWarning("arithmetic_mismatch", "Invoice totals require review."));
    }
  }

  private Optional<BigDecimal> amount(List<InvoiceExtractionField> fields, String key) {
    return fields.stream()
        .filter(field -> field.fieldKey().equals(key))
        .findFirst()
        .flatMap(field -> parseAmount(field.normalizedValue()));
  }

  private InvoiceExtractionWarning missingWarning(String key) {
    return new InvoiceExtractionWarning("missing_" + key, "A required invoice field is missing.");
  }

  private InvoiceExtractionWarning invalidWarning(String key) {
    return new InvoiceExtractionWarning("invalid_" + key, "A labelled invoice field is invalid.");
  }

  private InvoiceExtractionWarning ambiguousWarning(String key) {
    return new InvoiceExtractionWarning(
        "ambiguous_" + key, "A labelled invoice field is ambiguous.");
  }

  private InvoiceExtractionWarning lowConfidenceWarning(String key) {
    return new InvoiceExtractionWarning(
        "low_confidence_" + key, "An invoice field has low OCR confidence.");
  }

  private String normalized(String value) {
    return value
        .toLowerCase(Locale.ROOT)
        .replace('á', 'a')
        .replace('é', 'e')
        .replace('í', 'i')
        .replace('ó', 'o')
        .replace('ú', 'u')
        .replaceAll("[^a-z0-9]+", "")
        .trim();
  }

  private record FieldDefinition(String key, List<String> labels) {
    private List<List<String>> labelWords() {
      return labels.stream()
          .map(label -> List.of(label.split("\\s+")))
          .map(
              parts ->
                  parts.stream().map(HeuristicInvoiceExtractionAdapter::normalizedStatic).toList())
          .toList();
    }
  }

  private static String normalizedStatic(String value) {
    return value
        .toLowerCase(Locale.ROOT)
        .replace('á', 'a')
        .replace('é', 'e')
        .replace('í', 'i')
        .replace('ó', 'o')
        .replace('ú', 'u')
        .replaceAll("[^a-z0-9]+", "")
        .trim();
  }

  private record Candidate(
      String key, List<OcrWord> labelWords, List<OcrWord> valueWords, int score) {
    private String text() {
      return valueWords.stream()
          .map(OcrWord::wordText)
          .reduce((left, right) -> left + " " + right)
          .orElse("")
          .trim();
    }
  }

  private record LineKey(int pageNumber, int blockNumber, int paragraphNumber, int lineNumber)
      implements Comparable<LineKey> {
    @Override
    public int compareTo(LineKey other) {
      return Comparator.comparingInt(LineKey::pageNumber)
          .thenComparingInt(LineKey::blockNumber)
          .thenComparingInt(LineKey::paragraphNumber)
          .thenComparingInt(LineKey::lineNumber)
          .compare(this, other);
    }
  }

  private record Line(LineKey key, List<OcrWord> words) {
    private boolean samePage(Line other) {
      return key.pageNumber() == other.key.pageNumber();
    }
  }
}
