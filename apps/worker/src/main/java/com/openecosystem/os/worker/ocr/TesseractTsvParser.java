package com.openecosystem.os.worker.ocr;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TesseractTsvParser {

  private static final String EXPECTED_HEADER =
      "level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf"
          + "\ttext";

  private final int maxBytes;

  public TesseractTsvParser(int maxBytes) {
    if (maxBytes <= 0) {
      throw new IllegalArgumentException("TSV byte limit must be positive");
    }
    this.maxBytes = maxBytes;
  }

  public OcrPageResult parse(byte[] output, int documentPage) {
    if (output == null || output.length > maxBytes) {
      throw failure("OCR_TSV_TOO_LARGE", "OCR output exceeded the configured limit");
    }
    if (documentPage <= 0) {
      throw new IllegalArgumentException("Document page must be positive");
    }

    String tsv = decode(output);
    String[] rows = tsv.split("\\R", -1);
    if (rows.length == 0 || !EXPECTED_HEADER.equals(rows[0])) {
      throw invalidOutput();
    }

    List<OcrWord> words = new ArrayList<>();
    LineKey previousLine = null;
    StringBuilder pageText = new StringBuilder();
    for (int rowIndex = 1; rowIndex < rows.length; rowIndex++) {
      if (rows[rowIndex].isBlank()) {
        continue;
      }
      String[] columns = rows[rowIndex].split("\\t", -1);
      if (columns.length != 12) {
        throw invalidOutput();
      }

      ParsedRow row = parseRow(columns);
      if (row.level() != 5 || row.text().isBlank()) {
        continue;
      }

      int pageWordOrder = words.size() + 1;
      LineKey currentLine = new LineKey(row.blockNumber(), row.paragraphNumber(), row.lineNumber());
      if (!pageText.isEmpty()) {
        pageText.append(currentLine.equals(previousLine) ? ' ' : '\n');
      }
      String text = row.text().trim();
      pageText.append(text);
      words.add(
          new OcrWord(
              pageWordOrder,
              documentPage,
              pageWordOrder,
              row.blockNumber(),
              row.paragraphNumber(),
              row.lineNumber(),
              row.wordNumber(),
              text,
              row.confidence(),
              new OcrBoundingBox(row.left(), row.top(), row.width(), row.height())));
      previousLine = currentLine;
    }

    return new OcrPageResult(documentPage, OcrSourceKind.TESSERACT_TSV, pageText.toString(), words);
  }

  private ParsedRow parseRow(String[] columns) {
    try {
      int level = integer(columns[0]);
      integer(columns[1]);
      int blockNumber = nonNegative(columns[2]);
      int paragraphNumber = nonNegative(columns[3]);
      int lineNumber = nonNegative(columns[4]);
      int wordNumber = nonNegative(columns[5]);
      int left = nonNegative(columns[6]);
      int top = nonNegative(columns[7]);
      int width = nonNegative(columns[8]);
      int height = nonNegative(columns[9]);
      BigDecimal rawConfidence = new BigDecimal(columns[10]);
      BigDecimal confidence =
          rawConfidence.signum() < 0 ? null : rawConfidence.setScale(2, RoundingMode.HALF_UP);
      if (confidence != null && confidence.compareTo(BigDecimal.valueOf(100)) > 0) {
        throw invalidOutput();
      }
      return new ParsedRow(
          level,
          blockNumber,
          paragraphNumber,
          lineNumber,
          wordNumber,
          left,
          top,
          width,
          height,
          confidence,
          columns[11]);
    } catch (NumberFormatException exception) {
      throw invalidOutput();
    }
  }

  private int integer(String value) {
    return Integer.parseInt(value);
  }

  private int nonNegative(String value) {
    int parsed = integer(value);
    if (parsed < 0) {
      throw invalidOutput();
    }
    return parsed;
  }

  private String decode(byte[] output) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(output))
          .toString();
    } catch (CharacterCodingException exception) {
      throw invalidOutput();
    }
  }

  private OcrProviderException invalidOutput() {
    return failure("OCR_TSV_INVALID", "OCR output was invalid");
  }

  private OcrProviderException failure(String code, String summary) {
    return new OcrProviderException(code, summary);
  }

  private record LineKey(int blockNumber, int paragraphNumber, int lineNumber) {}

  private record ParsedRow(
      int level,
      int blockNumber,
      int paragraphNumber,
      int lineNumber,
      int wordNumber,
      int left,
      int top,
      int width,
      int height,
      BigDecimal confidence,
      String text) {}
}
