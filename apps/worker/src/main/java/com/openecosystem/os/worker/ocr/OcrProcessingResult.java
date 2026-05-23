package com.openecosystem.os.worker.ocr;

public record OcrProcessingResult(OcrProcessingOutcome outcome, String jobId) {

  public boolean retry() {
    return outcome == OcrProcessingOutcome.RETRY;
  }

  public boolean deadLetter() {
    return outcome == OcrProcessingOutcome.DEAD_LETTER;
  }
}
