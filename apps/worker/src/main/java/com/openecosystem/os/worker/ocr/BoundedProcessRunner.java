package com.openecosystem.os.worker.ocr;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public final class BoundedProcessRunner {

  private static final Duration TERMINATION_GRACE = Duration.ofMillis(100);

  private final ProcessStarter processStarter;

  public BoundedProcessRunner() {
    this(command -> new ProcessBuilder(command).start());
  }

  BoundedProcessRunner(ProcessStarter processStarter) {
    this.processStarter = processStarter;
  }

  public BoundedProcessResult run(List<String> command, Duration timeout, int maxOutputBytes) {
    return run(command, timeout, maxOutputBytes, maxOutputBytes);
  }

  public BoundedProcessResult run(
      List<String> command, Duration timeout, int maxStdoutBytes, int maxStderrBytes) {
    List<String> arguments = validate(command, timeout, maxStdoutBytes, maxStderrBytes);
    Process process = start(arguments);
    ExecutorService drainers =
        Executors.newFixedThreadPool(
            2, Thread.ofPlatform().daemon().name("ocr-process-output-", 0).factory());
    Future<byte[]> stdout =
        drainers.submit(
            () ->
                readBounded(
                    process.getInputStream(), maxStdoutBytes, OutputStreamKind.STDOUT, process));
    Future<byte[]> stderr =
        drainers.submit(
            () ->
                readBounded(
                    process.getErrorStream(), maxStderrBytes, OutputStreamKind.STDERR, process));

    try {
      process.getOutputStream().close();
      if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
        terminate(process);
        throw failure("OCR_PROCESS_TIMEOUT", "OCR process timed out");
      }

      byte[] boundedStdout = completedOutput(stdout, process);
      completedOutput(stderr, process);
      return new BoundedProcessResult(process.exitValue(), boundedStdout);
    } catch (InterruptedException exception) {
      terminate(process);
      stdout.cancel(true);
      stderr.cancel(true);
      Thread.currentThread().interrupt();
      throw failure("OCR_PROCESS_INTERRUPTED", "OCR process was interrupted");
    } catch (IOException exception) {
      terminate(process);
      throw failure("OCR_PROCESS_IO_FAILED", "OCR process could not be controlled");
    } finally {
      drainers.shutdownNow();
      closeQuietly(process.getInputStream());
      closeQuietly(process.getErrorStream());
      closeQuietly(process.getOutputStream());
    }
  }

  private List<String> validate(
      List<String> command, Duration timeout, int maxStdoutBytes, int maxStderrBytes) {
    if (command == null || command.isEmpty() || command.getFirst().isBlank()) {
      throw new IllegalArgumentException("Process command must not be empty");
    }
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("Process timeout must be positive");
    }
    if (maxStdoutBytes <= 0 || maxStderrBytes <= 0) {
      throw new IllegalArgumentException("Process output limit must be positive");
    }
    return List.copyOf(command);
  }

  private Process start(List<String> command) {
    try {
      return processStarter.start(command);
    } catch (IOException exception) {
      throw failure("OCR_PROCESS_UNAVAILABLE", "OCR process could not be started");
    }
  }

  private byte[] readBounded(
      InputStream input, int maxBytes, OutputStreamKind kind, Process process) throws IOException {
    try (input;
        ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        if (read == 0) {
          continue;
        }
        if ((long) output.size() + read > maxBytes) {
          terminate(process);
          throw new OutputLimitException(kind);
        }
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  private byte[] completedOutput(Future<byte[]> output, Process process)
      throws InterruptedException {
    try {
      return output.get();
    } catch (ExecutionException exception) {
      terminate(process);
      if (exception.getCause() instanceof OutputLimitException limitException) {
        throw limitException.kind() == OutputStreamKind.STDOUT
            ? failure(
                "OCR_PROCESS_STDOUT_LIMIT", "OCR process output exceeded the configured limit")
            : failure(
                "OCR_PROCESS_STDERR_LIMIT",
                "OCR process diagnostics exceeded the configured limit");
      }
      throw failure("OCR_PROCESS_IO_FAILED", "OCR process output could not be read");
    }
  }

  private void terminate(Process process) {
    try {
      process.toHandle().descendants().forEach(handle -> handle.destroy());
      process
          .toHandle()
          .descendants()
          .forEach(
              handle -> {
                if (handle.isAlive()) handle.destroyForcibly();
              });
    } catch (UnsupportedOperationException ignored) {
      // Test doubles and legacy process implementations may not expose a ProcessHandle.
    }
    if (!process.isAlive()) {
      process.destroy();
      return;
    }
    process.destroy();
    try {
      if (!process.waitFor(TERMINATION_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
        process.destroyForcibly();
        process.waitFor(TERMINATION_GRACE.toMillis(), TimeUnit.MILLISECONDS);
      }
    } catch (InterruptedException exception) {
      process.destroyForcibly();
      Thread.currentThread().interrupt();
    }
  }

  private void closeQuietly(AutoCloseable closeable) {
    try {
      closeable.close();
    } catch (Exception ignored) {
      // Process streams are best-effort cleanup after the process has ended.
    }
  }

  private OcrProviderException failure(String code, String summary) {
    return new OcrProviderException(code, summary);
  }

  @FunctionalInterface
  interface ProcessStarter {
    Process start(List<String> command) throws IOException;
  }

  private enum OutputStreamKind {
    STDOUT,
    STDERR
  }

  private static final class OutputLimitException extends IOException {

    private final OutputStreamKind kind;

    private OutputLimitException(OutputStreamKind kind) {
      this.kind = kind;
    }

    private OutputStreamKind kind() {
      return kind;
    }
  }
}
