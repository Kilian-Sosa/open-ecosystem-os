package com.openecosystem.os.worker.ocr;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

@ConfigurationProperties("openecosystem.media.ocr")
public record WorkerOcrProperties(
    String provider,
    int maxAttempts,
    @DurationUnit(ChronoUnit.SECONDS) Duration retryDelay,
    String command,
    String languages,
    int maxConcurrentDocuments,
    @DurationUnit(ChronoUnit.SECONDS) Duration pageTimeout,
    int maxPages,
    int renderDpi,
    long maxInputBytes,
    long maxRenderedPixels,
    int maxProcessOutputBytes,
    @DurationUnit(ChronoUnit.SECONDS) Duration staleProcessingTimeout,
    @DurationUnit(ChronoUnit.SECONDS) Duration documentTimeout,
    @DurationUnit(ChronoUnit.SECONDS) Duration persistenceCleanupMargin,
    String pdfHelperCommand,
    String pdfHelperJar) {

  public WorkerOcrProperties {
    provider = provider == null || provider.isBlank() ? "tesseract" : provider;
    maxAttempts = maxAttempts <= 0 ? 3 : maxAttempts;
    retryDelay =
        retryDelay == null || retryDelay.isNegative() ? Duration.ofSeconds(30) : retryDelay;
    command = command == null || command.isBlank() ? "tesseract" : command;
    languages = languages == null || languages.isBlank() ? "eng" : languages;
    maxConcurrentDocuments = maxConcurrentDocuments <= 0 ? 1 : maxConcurrentDocuments;
    pageTimeout =
        pageTimeout == null || pageTimeout.isNegative() || pageTimeout.isZero()
            ? Duration.ofSeconds(60)
            : pageTimeout;
    maxPages = maxPages <= 0 ? 50 : maxPages;
    renderDpi = renderDpi <= 0 ? 200 : renderDpi;
    maxInputBytes = maxInputBytes <= 0 ? 25L * 1024 * 1024 : maxInputBytes;
    maxRenderedPixels = maxRenderedPixels <= 0 ? 20_000_000L : maxRenderedPixels;
    maxProcessOutputBytes = maxProcessOutputBytes <= 0 ? 10 * 1024 * 1024 : maxProcessOutputBytes;
    staleProcessingTimeout =
        staleProcessingTimeout == null
                || staleProcessingTimeout.isNegative()
                || staleProcessingTimeout.isZero()
            ? Duration.ofMinutes(15)
            : staleProcessingTimeout;
    documentTimeout =
        documentTimeout == null || documentTimeout.isNegative() || documentTimeout.isZero()
            ? Duration.ofMinutes(10)
            : documentTimeout;
    persistenceCleanupMargin =
        persistenceCleanupMargin == null
                || persistenceCleanupMargin.isNegative()
                || persistenceCleanupMargin.isZero()
            ? Duration.ofMinutes(2)
            : persistenceCleanupMargin;
    pdfHelperCommand =
        pdfHelperCommand == null || pdfHelperCommand.isBlank() ? "java" : pdfHelperCommand;
    pdfHelperJar =
        pdfHelperJar == null || pdfHelperJar.isBlank() ? "/app/pdf-helper.jar" : pdfHelperJar;
  }
}
