package com.openecosystem.os.worker.ocr;

import org.springframework.stereotype.Component;

@Component
public class MockOcrProvider implements OcrProvider {

  @Override
  public String name() {
    return "mock";
  }

  @Override
  public OcrProviderResult extractText(OcrJob job) {
    if (job.fileId().contains("fail")) {
      throw new OcrProviderException("MOCK_OCR_FAILED", "Mock OCR provider failed");
    }

    String text =
        """
        Mock OCR result - fake/test data only
        File: %s
        Content type: %s
        Storage key: %s
        Invoice number: TEST-INV-2026-0001
        Supplier: Demo Supplies S.L. (fake/test data)
        Test NIF: B00000000 (test data)
        Test IBAN: ES00 0000 0000 0000 0000 0000 (test data)
        Total: 124.00 EUR
        Due date: 2026-06-15
        Extracted text is generated locally by the mock provider for MVP validation.
        """
            .formatted(job.fileId(), job.contentType(), job.storageKey())
            .trim();
    return new OcrProviderResult(text);
  }
}
