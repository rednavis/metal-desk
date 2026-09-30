package com.rednavis.metaldesk.api.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * The body of every error response (BRD FR-8.1: errors must be support-actionable).
 *
 * <p>{@code code} is the stable, machine-readable code a client switches on and support searches
 * for; {@code message} is for a human and may change; {@code correlationId} ties the response to
 * the server's log line, and is the request's {@code X-Correlation-Id} header when the caller sent
 * a safe one. {@code violations} lists every invalid field and is left out unless the request
 * failed field validation. Nothing else is ever in it: no stack trace, no class name, no internal
 * detail.
 *
 * @param code the machine-readable error code, for example {@code product.not-found}
 * @param message a human-readable description
 * @param correlationId the id to quote to support
 * @param violations the invalid fields; empty (and so omitted) unless the request failed field
 *     validation
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorEnvelope(
    String code, String message, String correlationId, List<FieldViolation> violations) {

  /**
   * Creates an envelope without field violations.
   *
   * @param code the machine-readable error code
   * @param message a human-readable description
   * @param correlationId the id to quote to support
   */
  public ApiErrorEnvelope(String code, String message, String correlationId) {
    this(code, message, correlationId, List.of());
  }

  /** Copies the violations, so the envelope cannot be changed through them. */
  public ApiErrorEnvelope {
    violations = List.copyOf(violations);
  }
}
