package com.openecosystem.os.worker.ocr;

public enum OcrProcessingOutcome {
  COMPLETED,
  RETRY,
  DEAD_LETTER,
  NO_OP
}
