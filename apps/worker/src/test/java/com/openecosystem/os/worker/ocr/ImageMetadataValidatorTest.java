package com.openecosystem.os.worker.ocr;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageMetadataValidatorTest {

  @TempDir Path temporaryRoot;

  @Test
  void rejectsTinyPngThatDeclaresTooManyPixelsBeforeDecode() throws IOException {
    Path image = temporaryRoot.resolve("bomb.png");
    Files.write(image, png(100_000, 100_000));

    assertFailure(() -> new ImageMetadataValidator().validate(image, "image/png", 20_000_000));
  }

  @Test
  void rejectsTinyJpegThatDeclaresTooManyPixelsBeforeDecode() throws IOException {
    Path image = temporaryRoot.resolve("bomb.jpg");
    Files.write(image, jpeg(50_000, 50_000));

    assertFailure(() -> new ImageMetadataValidator().validate(image, "image/jpeg", 20_000_000));
  }

  @Test
  void rejectsOverflowedAndNonPositiveDimensions() throws IOException {
    Path overflowed = temporaryRoot.resolve("overflow.png");
    Files.write(overflowed, png(-1, 1));
    Path nonPositive = temporaryRoot.resolve("zero.png");
    Files.write(nonPositive, png(0, 1));

    assertFailure(
        () -> new ImageMetadataValidator().validate(overflowed, "image/png", Long.MAX_VALUE));
    assertFailure(
        () -> new ImageMetadataValidator().validate(nonPositive, "image/png", 20_000_000));
  }

  private void assertFailure(ThrowingAction action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(OcrProviderException.class)
        .extracting(exception -> ((OcrProviderException) exception).code())
        .isIn("OCR_IMAGE_PIXEL_LIMIT", "OCR_IMAGE_DIMENSIONS_INVALID");
  }

  private byte[] png(int width, int height) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      output.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
      byte[] header =
          ByteBuffer.allocate(13)
              .order(ByteOrder.BIG_ENDIAN)
              .putInt(width)
              .putInt(height)
              .put((byte) 8)
              .put((byte) 2)
              .put((byte) 0)
              .put((byte) 0)
              .put((byte) 0)
              .array();
      chunk(output, "IHDR", header);
      chunk(output, "IEND", new byte[0]);
      return output.toByteArray();
    } catch (IOException exception) {
      throw new AssertionError(exception);
    }
  }

  private void chunk(ByteArrayOutputStream output, String type, byte[] data) throws IOException {
    output.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.length).array());
    byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    output.write(typeBytes);
    output.write(data);
    java.util.zip.CRC32 crc = new java.util.zip.CRC32();
    crc.update(typeBytes);
    crc.update(data);
    output.write(
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt((int) crc.getValue()).array());
  }

  private byte[] jpeg(int width, int height) {
    ByteBuffer buffer = ByteBuffer.allocate(23).order(ByteOrder.BIG_ENDIAN);
    buffer.put((byte) 0xFF).put((byte) 0xD8);
    buffer.put((byte) 0xFF).put((byte) 0xC0);
    buffer.putShort((short) 17);
    buffer.put((byte) 8);
    buffer.putShort((short) height);
    buffer.putShort((short) width);
    buffer.put((byte) 3);
    for (int component = 1; component <= 3; component++) {
      buffer.put((byte) component).put((byte) 0x11).put((byte) 0);
    }
    return buffer.array();
  }

  @FunctionalInterface
  private interface ThrowingAction {
    void run() throws Exception;
  }
}
