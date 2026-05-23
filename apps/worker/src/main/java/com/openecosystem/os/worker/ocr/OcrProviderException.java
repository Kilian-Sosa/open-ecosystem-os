package com.openecosystem.os.worker.ocr;

public class OcrProviderException extends RuntimeException {

  private final String code;

  public OcrProviderException(String code, String message) {
    super(message);
    this.code = code == null || code.isBlank() ? "OCR_PROVIDER_FAILED" : code;
  }

  public String code() {
    return code;
  }
}
