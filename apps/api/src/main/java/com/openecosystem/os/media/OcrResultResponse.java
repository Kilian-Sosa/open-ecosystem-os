package com.openecosystem.os.media;

public record OcrResultResponse(
    String ocrResultId, String provider, String providerVersion, int pageCount, int wordCount) {}
