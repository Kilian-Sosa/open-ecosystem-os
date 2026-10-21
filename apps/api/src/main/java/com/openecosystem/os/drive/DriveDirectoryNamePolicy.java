package com.openecosystem.os.drive;

import com.openecosystem.os.common.errors.ApiErrorCode;
import com.openecosystem.os.common.errors.ApiException;
import java.text.Normalizer;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DriveDirectoryNamePolicy {
  public String normalize(String name) {
    if (name == null
        || name.isBlank()
        || name.length() > 255
        || name.equals(".")
        || name.equals("..")
        || name.indexOf('/') >= 0
        || name.indexOf('\\') >= 0
        || name.codePoints()
            .anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT))
      throw new ApiException(
          HttpStatus.BAD_REQUEST, ApiErrorCode.BAD_REQUEST, "Drive name is invalid");
    return comparisonKey(name);
  }

  String comparisonKey(String name) {
    return Normalizer.normalize(
        Normalizer.normalize(name, Normalizer.Form.NFC).toLowerCase(Locale.ROOT),
        Normalizer.Form.NFC);
  }
}
