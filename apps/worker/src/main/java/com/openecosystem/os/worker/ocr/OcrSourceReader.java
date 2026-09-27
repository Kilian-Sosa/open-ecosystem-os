package com.openecosystem.os.worker.ocr;

@FunctionalInterface
public interface OcrSourceReader {

  OcrSource open(OcrJob job);
}
