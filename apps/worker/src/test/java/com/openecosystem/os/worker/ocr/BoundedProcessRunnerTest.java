package com.openecosystem.os.worker.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BoundedProcessRunnerTest {

  @Test
  void returnsBoundedStdoutForSuccessfulProcess() {
    ControlledProcess process = ControlledProcess.completed(0, "ok", "diagnostic");
    BoundedProcessRunner runner = new BoundedProcessRunner(command -> process);

    BoundedProcessResult result =
        runner.run(List.of("tesseract", "input", "stdout"), Duration.ofSeconds(1), 64);

    assertThat(result.exitCode()).isZero();
    assertThat(result.stdoutUtf8()).isEqualTo("ok");
    assertThat(process.stdinClosed.get()).isTrue();
  }

  @Test
  void terminatesTimedOutProcessWithFixedSafeFailure() {
    ControlledProcess process = ControlledProcess.running("", "secret diagnostic");
    BoundedProcessRunner runner = new BoundedProcessRunner(command -> process);

    assertFailure(
        () -> runner.run(List.of("tesseract"), Duration.ofMillis(10), 64),
        "OCR_PROCESS_TIMEOUT",
        "OCR process timed out");
    assertThat(process.destroyed.get()).isTrue();
    assertThat(process.isAlive()).isFalse();
  }

  @Test
  void forciblyTerminatesAStubbornHelperProcessWithinItsDeadline() {
    StubbornProcess process = new StubbornProcess();
    BoundedProcessRunner runner = new BoundedProcessRunner(command -> process);

    assertFailure(
        () -> runner.run(List.of("java", "-jar", "helper.jar"), Duration.ofMillis(10), 64, 16),
        "OCR_PROCESS_TIMEOUT",
        "OCR process timed out");

    assertThat(process.destroyed.get()).isTrue();
    assertThat(process.forciblyDestroyed.get()).isTrue();
  }

  @Test
  void terminatesProcessWhenStdoutExceedsCap() {
    ControlledProcess process = ControlledProcess.completed(0, "x".repeat(65), "");
    BoundedProcessRunner runner = new BoundedProcessRunner(command -> process);

    assertFailure(
        () -> runner.run(List.of("tesseract"), Duration.ofSeconds(1), 64),
        "OCR_PROCESS_STDOUT_LIMIT",
        "OCR process output exceeded the configured limit");
    assertThat(process.destroyed.get()).isTrue();
  }

  @Test
  void terminatesARunningProcessAsSoonAsAStreamCapIsExceeded() {
    ControlledProcess process = ControlledProcess.running("x".repeat(65), "");
    BoundedProcessRunner runner = new BoundedProcessRunner(command -> process);

    assertFailure(
        () -> runner.run(List.of("tesseract"), Duration.ofMillis(100), 64),
        "OCR_PROCESS_STDOUT_LIMIT",
        "OCR process output exceeded the configured limit");
    assertThat(process.destroyed.get()).isTrue();
  }

  @Test
  void terminatesProcessWhenStderrExceedsCapWithoutExposingDiagnostics() {
    ControlledProcess process =
        ControlledProcess.completed(0, "ok", "private-path-and-content".repeat(4));
    BoundedProcessRunner runner = new BoundedProcessRunner(command -> process);

    assertFailure(
        () -> runner.run(List.of("tesseract"), Duration.ofSeconds(1), 32),
        "OCR_PROCESS_STDERR_LIMIT",
        "OCR process diagnostics exceeded the configured limit");
    assertThat(process.destroyed.get()).isTrue();
  }

  @Test
  void interruptionTerminatesProcessAndRestoresInterruptStatus() throws Exception {
    ControlledProcess process = ControlledProcess.running("", "");
    BoundedProcessRunner runner = new BoundedProcessRunner(command -> process);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicBoolean interruptRestored = new AtomicBoolean(false);

    Thread thread =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    runner.run(List.of("tesseract"), Duration.ofMinutes(1), 64);
                  } catch (Throwable exception) {
                    failure.set(exception);
                    interruptRestored.set(Thread.currentThread().isInterrupted());
                  }
                });

    assertThat(process.waitStarted.await(1, TimeUnit.SECONDS)).isTrue();
    thread.interrupt();
    thread.join(1_000);

    assertThat(thread.isAlive()).isFalse();
    assertThat(failure.get()).isInstanceOf(OcrProviderException.class);
    assertThat(((OcrProviderException) failure.get()).code()).isEqualTo("OCR_PROCESS_INTERRUPTED");
    assertThat(failure.get()).hasMessage("OCR process was interrupted");
    assertThat(interruptRestored).isTrue();
    assertThat(process.destroyed.get()).isTrue();
  }

  @Test
  void forwardsLiteralArgumentListWithoutShellInterpretationSurface() {
    ControlledProcess process = ControlledProcess.completed(0, "ok", "");
    AtomicReference<List<String>> startedCommand = new AtomicReference<>();
    BoundedProcessRunner runner =
        new BoundedProcessRunner(
            command -> {
              startedCommand.set(command);
              return process;
            });
    List<String> command =
        List.of("tesseract", "literal;name.png", "stdout", "$(private)", "&", "tsv");

    runner.run(command, Duration.ofSeconds(1), 64);

    assertThat(startedCommand.get()).containsExactlyElementsOf(command);
  }

  private void assertFailure(
      ThrowingAction action, String expectedCode, String expectedSafeSummary) {
    assertThatThrownBy(action::run)
        .isInstanceOf(OcrProviderException.class)
        .hasMessage(expectedSafeSummary)
        .extracting(exception -> ((OcrProviderException) exception).code())
        .isEqualTo(expectedCode);
  }

  @FunctionalInterface
  private interface ThrowingAction {
    void run();
  }

  private static final class ControlledProcess extends Process {

    private final InputStream stdout;
    private final InputStream stderr;
    private final int exitCode;
    private final CountDownLatch completion = new CountDownLatch(1);
    private final CountDownLatch waitStarted = new CountDownLatch(1);
    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final AtomicBoolean stdinClosed = new AtomicBoolean(false);
    private final OutputStream stdin =
        new ByteArrayOutputStream() {
          @Override
          public void close() {
            stdinClosed.set(true);
          }
        };

    private ControlledProcess(int exitCode, String stdout, String stderr, boolean completed) {
      this.exitCode = exitCode;
      this.stdout =
          new ByteArrayInputStream(stdout.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      this.stderr =
          new ByteArrayInputStream(stderr.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      if (completed) {
        completion.countDown();
      }
    }

    static ControlledProcess completed(int exitCode, String stdout, String stderr) {
      return new ControlledProcess(exitCode, stdout, stderr, true);
    }

    static ControlledProcess running(String stdout, String stderr) {
      return new ControlledProcess(0, stdout, stderr, false);
    }

    @Override
    public OutputStream getOutputStream() {
      return stdin;
    }

    @Override
    public InputStream getInputStream() {
      return stdout;
    }

    @Override
    public InputStream getErrorStream() {
      return stderr;
    }

    @Override
    public int waitFor() throws InterruptedException {
      waitStarted.countDown();
      completion.await();
      return exitCode;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
      waitStarted.countDown();
      return completion.await(timeout, unit);
    }

    @Override
    public int exitValue() {
      if (isAlive()) {
        throw new IllegalThreadStateException("Process is still running");
      }
      return exitCode;
    }

    @Override
    public void destroy() {
      destroyed.set(true);
      completion.countDown();
    }

    @Override
    public Process destroyForcibly() {
      destroy();
      return this;
    }

    @Override
    public boolean isAlive() {
      return completion.getCount() > 0;
    }
  }

  private static final class StubbornProcess extends Process {

    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final AtomicBoolean forciblyDestroyed = new AtomicBoolean(false);

    @Override
    public OutputStream getOutputStream() {
      return new ByteArrayOutputStream();
    }

    @Override
    public InputStream getInputStream() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public InputStream getErrorStream() {
      return new ByteArrayInputStream(new byte[0]);
    }

    @Override
    public int waitFor() throws InterruptedException {
      Thread.sleep(Long.MAX_VALUE);
      return 0;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
      return forciblyDestroyed.get();
    }

    @Override
    public int exitValue() {
      return 0;
    }

    @Override
    public void destroy() {
      destroyed.set(true);
    }

    @Override
    public Process destroyForcibly() {
      forciblyDestroyed.set(true);
      return this;
    }

    @Override
    public boolean isAlive() {
      return !forciblyDestroyed.get();
    }
  }
}
