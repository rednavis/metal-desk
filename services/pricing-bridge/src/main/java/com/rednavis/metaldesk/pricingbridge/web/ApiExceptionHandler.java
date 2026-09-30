package com.rednavis.metaldesk.pricingbridge.web;

import com.rednavis.metaldesk.share.error.NotFoundException;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

/**
 * Turns failures into the platform's {@link ErrorEnvelope}: a {@link ValidationException} or an
 * unreadable request is a 400, a {@link NotFoundException} a 404, anything else a 500 with a fixed
 * message and the detail only in the log.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final String HEADER = "X-Correlation-Id";
  private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  /**
   * Maps a validation failure to 400.
   *
   * @param failure the failure
   * @param supplied the caller's correlation id, if any
   * @return the envelope response
   */
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ErrorEnvelope> handleValidation(
      ValidationException failure,
      @RequestHeader(name = HEADER, required = false) String supplied) {
    return respond(HttpStatus.BAD_REQUEST, failure.code(), failure.getMessage(), supplied);
  }

  /**
   * Maps a missing thing to 404.
   *
   * @param failure the failure
   * @param supplied the caller's correlation id, if any
   * @return the envelope response
   */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorEnvelope> handleNotFound(
      NotFoundException failure, @RequestHeader(name = HEADER, required = false) String supplied) {
    return respond(HttpStatus.NOT_FOUND, failure.code(), failure.getMessage(), supplied);
  }

  /**
   * Maps a request the framework could not read to 400.
   *
   * @param failure the framework's failure
   * @param supplied the caller's correlation id, if any
   * @return the envelope response
   */
  @ExceptionHandler(ServerWebInputException.class)
  public ResponseEntity<ErrorEnvelope> handleInput(
      ServerWebInputException failure,
      @RequestHeader(name = HEADER, required = false) String supplied) {
    return respond(
        HttpStatus.BAD_REQUEST, "request.invalid", "The request could not be read", supplied);
  }

  /**
   * Maps anything unexpected to a 500 that reveals nothing.
   *
   * @param failure the unexpected failure
   * @param supplied the caller's correlation id, if any
   * @return the envelope response
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorEnvelope> handleUnexpected(
      Exception failure, @RequestHeader(name = HEADER, required = false) String supplied) {
    final String correlationId = correlationId(supplied);
    log.error("Unhandled failure, correlation id {}", correlationId, failure);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorEnvelope("internal-error", "An unexpected error occurred", correlationId));
  }

  private static ResponseEntity<ErrorEnvelope> respond(
      HttpStatus status, String code, String message, String supplied) {
    return ResponseEntity.status(status)
        .body(new ErrorEnvelope(code, message, correlationId(supplied)));
  }

  private static String correlationId(String supplied) {
    return supplied != null && SAFE.matcher(supplied).matches()
        ? supplied
        : UUID.randomUUID().toString();
  }
}
