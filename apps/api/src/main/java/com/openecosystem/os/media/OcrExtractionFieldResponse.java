package com.openecosystem.os.media;

import java.math.BigDecimal;
import java.util.List;

public record OcrExtractionFieldResponse(
    String fieldKey,
    String displayValue,
    String normalizedValue,
    String status,
    BigDecimal confidence,
    int sourcePageNumber,
    int sourceBlockNumber,
    int sourceParagraphNumber,
    int sourceLineNumber,
    List<OcrExtractionFieldSourceResponse> sources) {}
