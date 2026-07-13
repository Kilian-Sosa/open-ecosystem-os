package com.openecosystem.os.worker.ocr;

public interface OcrProvider {

  String name();

  OcrDocumentResult extract(OcrJob job, OcrExecutionDeadline deadline);
}
