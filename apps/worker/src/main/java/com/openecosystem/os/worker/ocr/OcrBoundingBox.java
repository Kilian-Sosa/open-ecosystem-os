package com.openecosystem.os.worker.ocr;

public record OcrBoundingBox(int left, int top, int width, int height) {

  public OcrBoundingBox {
    if (left < 0 || top < 0 || width < 0 || height < 0) {
      throw new IllegalArgumentException("Bounding box values must not be negative");
    }
  }
}
