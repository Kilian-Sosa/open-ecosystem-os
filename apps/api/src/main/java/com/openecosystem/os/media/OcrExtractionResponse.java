package com.openecosystem.os.media;

import java.math.BigDecimal;
import java.util.List;

public record OcrExtractionResponse(
    String extractionId,
    String extractor,
    String extractorVersion,
    String status,
    boolean reviewRequired,
    BigDecimal confidence,
    int fieldCount,
    int warningCount,
    List<OcrExtractionWarningResponse> warnings,
    List<OcrExtractionFieldResponse> fields) {}
