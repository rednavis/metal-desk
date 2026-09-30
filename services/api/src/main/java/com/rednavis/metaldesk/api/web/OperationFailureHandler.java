package com.rednavis.metaldesk.api.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers an operation that failed after something durable exists (BRD FR-8.1): the failure is
 * logged at error level with its reference and the correlation id, and the response carries the
 * same correlation id, so a customer's report can be found in the log.
 *
 * <p>It runs before {@link DomainExceptionHandler}, whose catch-all would otherwise answer first.
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class OperationFailureHandler {

  /**
   * Maps the failure to 503 with the envelope, so the caller can retry.
   *
   * @param failure the failure
   * @param supplied the correlation id the caller sent, if any
   * @return the envelope response
   */
  @ExceptionHandler(OperationFailedException.class)
  public ResponseEntity<ApiErrorEnvelope> handle(
      OperationFailedException failure,
      @RequestHeader(name = CorrelationId.HEADER, required = false) String supplied) {
    final String correlationId = CorrelationId.choose(supplied);
    if (log.isErrorEnabled()) {
      log.error(
          "Operation failed: code {}, reference {}, correlation id {}",
          failure.code(),
          failure.reference(),
          correlationId,
          failure);
    }
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ApiErrorEnvelope(failure.code(), failure.getMessage(), correlationId));
  }
}
