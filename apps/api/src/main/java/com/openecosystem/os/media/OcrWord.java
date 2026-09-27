package com.openecosystem.os.media;

import java.math.BigDecimal;
import java.time.Instant;

public record OcrWord(
    String ocrWordId,
    String ocrPageId,
    String ocrResultId,
    String workspaceId,
    int readingOrder,
    int pageNumber,
    int pageWordOrder,
    int blockNumber,
    int paragraphNumber,
    int lineNumber,
    int wordNumber,
    String wordText,
    BigDecimal confidence,
    Integer leftPx,
    Integer topPx,
    Integer widthPx,
    Integer heightPx,
    String sourceKind,
    Instant createdAt) {}
