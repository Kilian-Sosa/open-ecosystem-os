package com.openecosystem.os.media;

import java.math.BigDecimal;
import java.util.List;

public record OcrExtractionFieldResponse(
    String fieldKey,
    String label,
    String displayValue,
    String normalizedValue,
    String status,
    BigDecimal confidence,
    List<OcrExtractionProvenanceResponse> provenance) {}
