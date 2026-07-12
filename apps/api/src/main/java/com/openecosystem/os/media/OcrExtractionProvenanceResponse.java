package com.openecosystem.os.media;

public record OcrExtractionProvenanceResponse(
    String sourceRole,
    String ocrWordId,
    int pageNumber,
    int blockNumber,
    int paragraphNumber,
    int lineNumber,
    int wordNumber,
    int readingOrder,
    String sourceKind) {}
