package com.openecosystem.os.media;

import java.util.Set;

final class DiagnosticFailureSanitizer {

  private static final Set<String> SAFE_CODES =
      Set.of("MOCK_OCR_FAILED", "OCR_PROVIDER_FAILED", "PROCESSING_FAILED");

  private DiagnosticFailureSanitizer() {}

  static String code(String value) {
    if (value == null) return null;
    return SAFE_CODES.contains(value) ? value : "PROCESSING_FAILED";
  }

  static String ocrReason(OcrJob job) {
    if (job.failureCode() == null && job.failureMessage() == null) return null;
    return "OCR processing did not complete. Use the correlation ID for permitted diagnostics.";
  }

  static String workflowReason() {
    return "Workflow processing failed. Use the correlation ID for permitted diagnostics.";
  }
}
