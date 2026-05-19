package com.openecosystem.os.common.errors;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

  private final HttpStatus status;
  private final ApiErrorCode code;
  private final Map<String, String> details;

  public ApiException(HttpStatus status, ApiErrorCode code, String message) {
    this(status, code, message, Map.of());
  }

  public ApiException(
      HttpStatus status, ApiErrorCode code, String message, Map<String, String> details) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details == null ? Map.of() : Map.copyOf(details);
  }

  public HttpStatus status() {
    return status;
  }

  public ApiErrorCode code() {
    return code;
  }

  public Map<String, String> details() {
    return details;
  }
}
