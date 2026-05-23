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
        Mock OCR result
        File: %s
        Content type: %s
        Storage key: %s
        Extracted text is generated locally by the mock provider for MVP validation.
        """
            .formatted(job.fileId(), job.contentType(), job.storageKey())
            .trim();
    return new OcrProviderResult(text);
  }
}
