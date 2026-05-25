package com.openecosystem.os.demo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DemoInvoiceExtractionResponse(
    String extractionId,
    String invoiceNumber,
    String supplierName,
    String supplierTestNif,
    String supplierTestIban,
    BigDecimal totalAmount,
    String currency,
    LocalDate dueDate,
    boolean isTestData) {}
