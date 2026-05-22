package com.openecosystem.os.common.errors;

import com.openecosystem.os.common.security.CorrelationContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ApiErrorResponse> handleApiException(
      ApiException exception, HttpServletRequest request) {
    return buildResponse(
        exception.status(),
        exception.code(),
        exception.getMessage(),
        request.getRequestURI(),
        exception.details());
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiErrorResponse> handleResponseStatusException(
      ResponseStatusException exception, HttpServletRequest request) {
    HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
    return buildResponse(
        status, codeFor(status), exception.getReason(), request.getRequestURI(), Map.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    Map<String, String> details = new LinkedHashMap<>();
    for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
      details.put(fieldError.getField(), fieldError.getDefaultMessage());
    }
    return buildResponse(
        HttpStatus.BAD_REQUEST,
        ApiErrorCode.VALIDATION_FAILED,
        "Request validation failed",
        request.getRequestURI(),
        details);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
      ConstraintViolationException exception, HttpServletRequest request) {
    Map<String, String> details = new LinkedHashMap<>();
    exception
        .getConstraintViolations()
        .forEach(
            violation ->
                details.put(violation.getPropertyPath().toString(), violation.getMessage()));
    return buildResponse(
        HttpStatus.BAD_REQUEST,
        ApiErrorCode.VALIDATION_FAILED,
        "Request validation failed",
        request.getRequestURI(),
        details);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleException(
      Exception exception, HttpServletRequest request) {
    LOGGER.error(
        "Unexpected API error at path {} with correlationId {}",
        request.getRequestURI(),
        CorrelationContext.currentOrCreate(),
        exception);
    return buildResponse(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ApiErrorCode.INTERNAL_ERROR,
        "Unexpected server error",
        request.getRequestURI(),
        Map.of());
  }

  private ResponseEntity<ApiErrorResponse> buildResponse(
      HttpStatus status,
      ApiErrorCode code,
      String message,
      String path,
      Map<String, String> details) {
    ApiErrorResponse response =
        new ApiErrorResponse(
            Instant.now(),
            status.value(),
            code.name(),
            message == null || message.isBlank() ? status.getReasonPhrase() : message,
            path,
            CorrelationContext.currentOrCreate(),
            details == null ? Map.of() : Map.copyOf(details));

    return ResponseEntity.status(status).body(response);
  }

  private ApiErrorCode codeFor(HttpStatus status) {
    return switch (status) {
      case BAD_REQUEST -> ApiErrorCode.BAD_REQUEST;
      case CONFLICT -> ApiErrorCode.CONFLICT;
      case FORBIDDEN -> ApiErrorCode.FORBIDDEN;
      case NOT_FOUND -> ApiErrorCode.NOT_FOUND;
      case UNAUTHORIZED -> ApiErrorCode.UNAUTHORIZED;
      default -> ApiErrorCode.INTERNAL_ERROR;
    };
  }
}
