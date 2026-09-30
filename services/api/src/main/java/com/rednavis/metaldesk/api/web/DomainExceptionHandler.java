package com.rednavis.metaldesk.api.web;

import com.rednavis.metaldesk.share.error.ConflictException;
import com.rednavis.metaldesk.share.error.DomainException;
import com.rednavis.metaldesk.share.error.NotFoundException;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

/**
 * Turns every failure into the one {@link ApiErrorEnvelope}.
 *
 * <p>A {@link ValidationException} is a 400, a {@link NotFoundException} a 404 and a {@link
 * ConflictException} a 409, each carrying the code the domain put on it. A malformed request the
 * framework rejects (a page number that is not a number) is a 400 with code {@code
 * request.invalid}. A status the framework chose itself, such as 404 for an unknown route, is kept.
 * Anything else is a 500 with code {@code internal-error} and a fixed message: the detail goes to
 * the log, tagged with the correlation id, and never to the client.
 *
 * <p>The correlation id is the request's {@code X-Correlation-Id} header when it is short and made
 * of letters, digits, dots, dashes and underscores, and a fresh random id otherwise, so a caller
 * cannot put arbitrary text into the response body or the log.
 */
@Slf4j
@RestControllerAdvice
public class DomainExceptionHandler {

  private static final String HEADER = "X-Correlation-Id";
  private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  /**
   * Maps a validation failure to 400.
   *
   * @param failure the failure
   * @param supplied the caller's correlation id, if any
   * @return the envelope response
   */
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ApiErrorEnvelope> handleValidation(
      ValidationException failure,
      @RequestHeader(name = HEADER, required = false) String supplied) {
    return respond(HttpStatus.BAD_REQUEST, failure, supplied);
  }

  /**
   * Maps a missing thing to 404.
   *
   * @param failure the failure
   * @param supplied the caller's correlation id, if any
   * @return the envelope response
   */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiErrorEnvelope> handleNotFound(
      NotFoundException failure, @RequestHeader(name = HEADER, required = false) String supplied) {
    return respond(HttpStatus.NOT_FOUND, failure, supplied);
  }

  /**
   * Maps a conflict to 409.
   *
   * @param failure the failure
   * @param supplied the caller's correlation id, if any
   * @return the envelope response
   */
  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ApiErrorEnvelope> handleConflict(
      ConflictException failure, @RequestHeader(name = HEADER, required = false) String supplied) {
    return respond(HttpStatus.CONFLICT, failure, supplied);
  }

  /**
   * Maps a request the framework could not read to a 400.
   *
   * @param failure the framework's failure
   * @param supplied the caller's correlation id, if any
   * @return the envelope response
   */
  @ExceptionHandler(ServerWebInputException.class)
  public ResponseEntity<ApiErrorEnvelope> handleInput(
      ServerWebInputException failure,
      @RequestHeader(name = HEADER, required = false) String supplied) {
    return respond(
        HttpStatus.BAD_REQUEST, "request.invalid", "The request could not be read", supplied);
  }

  /**
   * Keeps a status the framework chose, such as 404 for an unknown route.
   *
   * @param failure the framework's failure
   * @param supplied the caller's correlation id, if any
   * @return the envelope response
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiErrorEnvelope> handleStatus(
      ResponseStatusException failure,
      @RequestHeader(name = HEADER, required = false) String supplied) {
    final HttpStatusCode status = failure.getStatusCode();
    return respond(status, "http." + status.value(), "The request was not served", supplied);
  }

  /**
   * Maps anything unexpected to a 500 that reveals nothing.
   *
   * @param failure the unexpected failure
   * @param supplied the caller's correlation id, if any
   * @return the envelope response
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorEnvelope> handleUnexpected(
      Exception failure, @RequestHeader(name = HEADER, required = false) String supplied) {
    final String correlationId = correlationId(supplied);
    log.error("Unhandled failure, correlation id {}", correlationId, failure);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            new ApiErrorEnvelope("internal-error", "An unexpected error occurred", correlationId));
  }

  private static ResponseEntity<ApiErrorEnvelope> respond(
      HttpStatus status, DomainException failure, String supplied) {
    return respond(status, failure.code(), failure.getMessage(), supplied);
  }

  private static ResponseEntity<ApiErrorEnvelope> respond(
      HttpStatusCode status, String code, String message, String supplied) {
    return ResponseEntity.status(status)
        .body(new ApiErrorEnvelope(code, message, correlationId(supplied)));
  }

  private static String correlationId(String supplied) {
    return supplied != null && SAFE_ID.matcher(supplied).matches()
        ? supplied
        : UUID.randomUUID().toString();
  }
}
