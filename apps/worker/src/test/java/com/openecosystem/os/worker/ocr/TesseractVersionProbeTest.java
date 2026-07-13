package com.openecosystem.os.worker.ocr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TesseractVersionProbeTest {

  @Test
  void cachesTheNormalizedSemanticVersionFromTheFirstLine() {
    TesseractVersionProbe probe = probe(new CompletedProcess(0, "tesseract 5.5.1-rc1\nignored"));

    assertThat(probe.version()).isEqualTo("5.5.1-rc1");
    assertThat(probe.version()).isEqualTo("5.5.1-rc1");
  }

  @Test
  void rejectsMalformedVersionOutputWithASafeFailure() {
    assertFailure(probe(new CompletedProcess(0, "ocr provider 5.5.1")));
  }

  @Test
  void rejectsTimedOutAndOversizedVersionProbesWithASafeFailure() {
    assertFailure(probe(new BlockingProcess()));
    assertFailure(probe(new CompletedProcess(0, "tesseract " + "9".repeat(4096))));
  }

  private TesseractVersionProbe probe(Process process) {
    return new TesseractVersionProbe(
        new BoundedProcessRunner(command -> process),
        new WorkerOcrProperties(
            null, 0, null, null, null, 0, null, 0, 0, 0, 0, 0, null, null, null, null, null, 0, 0,
            null, 0),
        List.of("tesseract", "--version"));
  }

  private void assertFailure(TesseractVersionProbe probe) {
    assertThatThrownBy(probe::version)
        .isInstanceOf(OcrProviderException.class)
        .hasMessage("OCR provider version could not be established")
        .extracting(exception -> ((OcrProviderException) exception).code())
        .isEqualTo("OCR_PROVIDER_VERSION_INVALID");
  }

  private static class CompletedProcess extends Process {
    private final int exitCode;
    private final InputStream stdout;

    private CompletedProcess(int exitCode, String stdout) {
      this.exitCode = exitCode;
      this.stdout = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public OutputStream getOutputStream() {
      return new ByteArrayOutputStream();
    }

    @Override
    public InputStream getInputStream() {
      return stdout;
    }

    @Override
    public InputStream getErrorStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() {
      return exitCode;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public int exitValue() {
      return exitCode;
    }

    @Override
    public void destroy() {}

    @Override
    public Process destroyForcibly() {
      return this;
    }

    @Override
    public boolean isAlive() {
      return false;
    }
  }

  private static final class BlockingProcess extends CompletedProcess {
    private BlockingProcess() {
      super(0, "");
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
      return false;
    }

    @Override
    public boolean isAlive() {
      return true;
    }
  }
}
