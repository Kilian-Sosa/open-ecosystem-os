package com.openecosystem.os.worker.ocr;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class TesseractVersionProbe {

  private static final Pattern VERSION =
      Pattern.compile("(?i)^tesseract\\s+(\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?)$");

  private final BoundedProcessRunner processRunner;
  private final WorkerOcrProperties properties;
  private final List<String> command;
  private String cachedVersion;

  @Autowired
  public TesseractVersionProbe(BoundedProcessRunner processRunner, WorkerOcrProperties properties) {
    this(processRunner, properties, List.of(properties.command(), "--version"));
  }

  TesseractVersionProbe(
      BoundedProcessRunner processRunner, WorkerOcrProperties properties, List<String> command) {
    this.processRunner = processRunner;
    this.properties = properties;
    this.command = List.copyOf(command);
  }

  TesseractVersionProbe(String cachedVersion) {
    processRunner = null;
    properties = null;
    command = List.of();
    this.cachedVersion = cachedVersion;
  }

  public synchronized String version() {
    if (cachedVersion != null) return cachedVersion;
    try {
      BoundedProcessResult result =
          processRunner.run(
              command,
              properties.versionProbeTimeout(),
              properties.versionProbeMaxOutputBytes(),
              properties.versionProbeMaxOutputBytes());
      if (result.exitCode() != 0) throw failure();
      String firstLine = result.stdoutUtf8().lines().findFirst().orElse("");
      Matcher matcher = VERSION.matcher(firstLine);
      if (!matcher.matches()) throw failure();
      cachedVersion = matcher.group(1);
      return cachedVersion;
    } catch (OcrProviderException exception) {
      throw failure();
    }
  }

  private OcrProviderException failure() {
    return new OcrProviderException(
        "OCR_PROVIDER_VERSION_INVALID", "OCR provider version could not be established");
  }
}
