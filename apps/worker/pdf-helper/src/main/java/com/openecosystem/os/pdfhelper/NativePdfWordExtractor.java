package com.openecosystem.os.pdfhelper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

final class NativePdfWordExtractor extends PDFTextStripper {

  private final int dpi;
  private final PDRectangle cropBox;
  private final List<PdfHelperResponse.NativeWord> words = new ArrayList<>();
  private int lineNumber;
  private int pageWordOrder;

  NativePdfWordExtractor(int dpi, PDPage page, int pageNumber) throws IOException {
    this.dpi = dpi;
    cropBox = page.getCropBox();
    setSortByPosition(true);
    setStartPage(pageNumber);
    setEndPage(pageNumber);
  }

  List<PdfHelperResponse.NativeWord> extract(org.apache.pdfbox.pdmodel.PDDocument document)
      throws IOException {
    getText(document);
    return List.copyOf(words);
  }

  @Override
  protected void writeString(String text, List<TextPosition> positions) {
    if (text == null || text.isBlank() || positions == null || positions.isEmpty()) {
      return;
    }
    lineNumber++;
    int positionIndex = 0;
    int wordNumber = 0;
    StringBuilder token = new StringBuilder();
    List<TextPosition> tokenPositions = new ArrayList<>();
    for (int offset = 0; offset < text.length(); offset++) {
      char character = text.charAt(offset);
      TextPosition position = positions.get(Math.min(positionIndex, positions.size() - 1));
      positionIndex++;
      if (Character.isWhitespace(character)) {
        appendWord(token, tokenPositions, ++wordNumber);
      } else {
        token.append(character);
        tokenPositions.add(position);
      }
    }
    appendWord(token, tokenPositions, ++wordNumber);
  }

  private void appendWord(StringBuilder token, List<TextPosition> positions, int wordNumber) {
    if (token.isEmpty()) {
      return;
    }
    PdfHelperResponse.BoundingBox box = boundingBox(positions);
    words.add(
        new PdfHelperResponse.NativeWord(
            1, 1, lineNumber, wordNumber, ++pageWordOrder, token.toString(), box));
    token.setLength(0);
    positions.clear();
  }

  private PdfHelperResponse.BoundingBox boundingBox(List<TextPosition> positions) {
    float minX = Float.MAX_VALUE;
    float minY = Float.MAX_VALUE;
    float maxX = Float.MIN_VALUE;
    float maxY = Float.MIN_VALUE;
    for (TextPosition position : positions) {
      minX = Math.min(minX, position.getXDirAdj());
      minY = Math.min(minY, position.getYDirAdj());
      maxX = Math.max(maxX, position.getXDirAdj() + position.getWidthDirAdj());
      maxY = Math.max(maxY, position.getYDirAdj() + position.getHeightDir());
    }
    double scale = dpi / 72D;
    int left = (int) Math.floor(Math.max(0, minX - cropBox.getLowerLeftX()) * scale);
    int top = (int) Math.floor(Math.max(0, minY - cropBox.getLowerLeftY()) * scale);
    int width = Math.max(1, (int) Math.ceil((maxX - minX) * scale));
    int height = Math.max(1, (int) Math.ceil((maxY - minY) * scale));
    return new PdfHelperResponse.BoundingBox(left, top, width, height);
  }
}
