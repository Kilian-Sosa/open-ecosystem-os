package com.openecosystem.os.worker.ocr;

public interface OcrProvider {

  String name();

  OcrProviderResult extractText(OcrJob job);
}
