package com.openecosystem.os.worker.ocr;

public enum OcrSourceKind {
  PDF_TEXT_LAYER("pdf_text_layer"),
  TESSERACT_TSV("tesseract_tsv");

  private final String value;

  OcrSourceKind(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
