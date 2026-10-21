package com.openecosystem.os.drive;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openecosystem.os.common.errors.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DriveUploadContentValidatorTest {
  @Test
  void rejectsCorruptOfficeCentralDirectoryChecksums() {
    byte[] bytes = SyntheticDriveFiles.bytes(SyntheticDriveFiles.TYPES[3]);
    var buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < bytes.length - 46; i++) {
      if (buffer.getInt(i) == 0x02014b50) {
        buffer.putInt(i + 16, buffer.getInt(i + 16) ^ 1);
        break;
      }
    }
    assertThatThrownBy(() -> validator.validate(bytes, SyntheticDriveFiles.TYPES[3]))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void rejectsBadImageChecksumAndTruncatedJpegWithEndMarker() {
    byte[] png = SyntheticDriveFiles.bytes("image/png");
    png[29] ^= 1;
    assertThatThrownBy(() -> validator.validate(png, "image/png")).isInstanceOf(ApiException.class);
    byte[] jpeg = SyntheticDriveFiles.bytes("image/jpeg");
    byte[] truncated = Arrays.copyOf(jpeg, jpeg.length / 2);
    truncated[truncated.length - 2] = (byte) 0xff;
    truncated[truncated.length - 1] = (byte) 0xd9;
    assertThatThrownBy(() -> validator.validate(truncated, "image/jpeg"))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void rejectsOversizedSourceAndExpandedOfficeEntry() {
    assertThatThrownBy(() -> validator.validate(new byte[25 * 1024 * 1024 + 1], "text/plain"))
        .isInstanceOf(ApiException.class);
    byte[] office =
        SyntheticDriveFiles.office(
            "word/document.xml", "<x>" + "x".repeat(8 * 1024 * 1024) + "</x>");
    assertThatThrownBy(() -> validator.validate(office, SyntheticDriveFiles.TYPES[3]))
        .isInstanceOf(ApiException.class);
  }

  private final DriveUploadContentValidator validator = new DriveUploadContentValidator();

  @Test
  void rejectsDeclaredMimeAndSignatureMismatch() {
    assertThatThrownBy(() -> validator.validate("synthetic text".getBytes(), "application/pdf"))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> validator.validate(SyntheticDriveFiles.pdf(), "image/png"))
        .isInstanceOf(ApiException.class);
  }

  @Test
  void rejectsCorruptAllowedFormats() {
    for (String mime : SyntheticDriveFiles.TYPES) {
      byte[] valid = SyntheticDriveFiles.bytes(mime);
      assertThatThrownBy(() -> validator.validate(Arrays.copyOf(valid, valid.length / 2), mime))
          .as(mime)
          .isInstanceOf(ApiException.class);
    }
  }

  @Test
  void validSupportedFormatsStillValidate() {
    for (String mime : SyntheticDriveFiles.TYPES)
      validator.validate(SyntheticDriveFiles.bytes(mime), mime);
    validator.validate("Synthetic UTF-8 café\n".getBytes(StandardCharsets.UTF_8), "text/plain");
  }

  @Test
  void rejectsMalformedUtf8ControlCharactersAndBinaryDisguisedAsText() {
    for (byte[] bytes :
        new byte[][] {new byte[] {(byte) 0xc3, 0x28}, new byte[] {0}, SyntheticDriveFiles.pdf()}) {
      assertThatThrownBy(() -> validator.validate(bytes, "text/plain"))
          .isInstanceOf(ApiException.class);
    }
  }

  @Test
  void rejectsOfficeTypeMismatchAndUnsafeXml() {
    assertThatThrownBy(
            () ->
                validator.validate(
                    SyntheticDriveFiles.bytes(SyntheticDriveFiles.TYPES[3]),
                    SyntheticDriveFiles.TYPES[4]))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                validator.validate(
                    SyntheticDriveFiles.office(
                        "word/document.xml",
                        "<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///synthetic'>]><x>&x;</x>"),
                    SyntheticDriveFiles.TYPES[3]))
        .isInstanceOf(ApiException.class);
  }
}
