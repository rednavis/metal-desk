package com.rednavis.metaldesk.admin.web;

import com.rednavis.metaldesk.admin.security.UnauthorizedException;
import com.rednavis.metaldesk.share.error.ConflictException;
import com.rednavis.metaldesk.share.error.DomainException;
import com.rednavis.metaldesk.share.error.NotFoundException;
import com.rednavis.metaldesk.share.error.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns failures into the platform's {@link ErrorEnvelope}: no staff identity is a 401, a {@link
 * ValidationException} or an unreadable request a 400, a {@link NotFoundException} a 404, a {@link
 * ConflictException} (including an illegal order transition) a 409, and anything else a 500 with a
 * fixed message and the detail only in the log.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final String HEADER = "X-Correlation-Id";
  private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  /**
   * Maps a missing staff identity to 401.
   *
   * @param failure the failure
   * @param request the request, whose correlation id header is echoed
   * @return the envelope response
   */
  @ExceptionHandler(UnauthorizedException.class)
  public ResponseEntity<ErrorEnvelope> handleUnauthorized(
      UnauthorizedException failure, HttpServletRequest request) {
    return respond(
        HttpStatus.UNAUTHORIZED,
        "auth.unauthorized",
        failure.getMessage(),
        request.getHeader(HEADER));
  }

  /**
   * Maps a validation failure to 400.
   *
   * @param failure the failure
   * @param request the request, whose correlation id header is echoed
   * @return the envelope response
   */
  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ErrorEnvelope> handleValidation(
      ValidationException failure, HttpServletRequest request) {
    return domain(HttpStatus.BAD_REQUEST, failure, request.getHeader(HEADER));
  }

  /**
   * Maps a missing thing to 404.
   *
   * @param failure the failure
   * @param request the request, whose correlation id header is echoed
   * @return the envelope response
   */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorEnvelope> handleNotFound(
      NotFoundException failure, HttpServletRequest request) {
    return domain(HttpStatus.NOT_FOUND, failure, request.getHeader(HEADER));
  }

  /**
   * Maps a conflict, such as an illegal order transition, to 409.
   *
   * @param failure the failure
   * @param request the request, whose correlation id header is echoed
   * @return the envelope response
   */
  @ExceptionHandler(ConflictException.class)
  public ResponseEntity<ErrorEnvelope> handleConflict(
      ConflictException failure, HttpServletRequest request) {
    return domain(HttpStatus.CONFLICT, failure, request.getHeader(HEADER));
  }

  /**
   * Maps a request the framework could not read to 400.
   *
   * @param failure the framework's failure
   * @param request the request, whose correlation id header is echoed
   * @return the envelope response
   */
  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ErrorEnvelope> handleUnreadable(
      Exception failure, HttpServletRequest request) {
    return respond(
        HttpStatus.BAD_REQUEST,
        "request.invalid",
        "The request could not be read",
        request.getHeader(HEADER));
  }

  /**
   * Maps an unknown route to 404.
   *
   * @param failure the framework's failure
   * @param request the request, whose correlation id header is echoed
   * @return the envelope response
   */
  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorEnvelope> handleNoRoute(
      NoResourceFoundException failure, HttpServletRequest request) {
    return respond(
        HttpStatus.NOT_FOUND, "http.404", "The request was not served", request.getHeader(HEADER));
  }

  /**
   * Maps anything unexpected to a 500 that reveals nothing.
   *
   * @param failure the unexpected failure
   * @param request the request, whose correlation id header is echoed
   * @return the envelope response
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorEnvelope> handleUnexpected(
      Exception failure, HttpServletRequest request) {
    final String correlationId = correlationId(request.getHeader(HEADER));
    log.error("Unhandled failure, correlation id {}", correlationId, failure);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorEnvelope("internal-error", "An unexpected error occurred", correlationId));
  }

  private static ResponseEntity<ErrorEnvelope> domain(
      HttpStatusCode status, DomainException failure, String supplied) {
    return respond(status, failure.code(), failure.getMessage(), supplied);
  }

  private static ResponseEntity<ErrorEnvelope> respond(
      HttpStatusCode status, String code, String message, String supplied) {
    return ResponseEntity.status(status)
        .body(new ErrorEnvelope(code, message, correlationId(supplied)));
  }

  private static String correlationId(String supplied) {
    return supplied != null && SAFE.matcher(supplied).matches()
        ? supplied
        : UUID.randomUUID().toString();
  }
}
