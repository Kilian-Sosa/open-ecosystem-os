package com.openecosystem.os.invoice;

import com.openecosystem.os.media.OcrDocumentResult;

public interface InvoiceExtractionPort {
  InvoiceExtraction extract(InvoiceExtractionRequest request, OcrDocumentResult result);
}
