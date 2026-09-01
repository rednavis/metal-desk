package com.rednavis.metaldesk.api.web;

import java.util.Arrays;
import java.util.List;

/**
 * A request failed validation on one or more fields; all of them are reported, not just the first,
 * so a form can show every mistake in one round trip. It becomes a 400 whose {@link
 * ApiErrorEnvelope} carries the list.
 */
public class FieldViolationsException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** The envelope code of a request with field violations. */
  public static final String CODE = "validation.failed";

  private final FieldViolation[] found;

  /**
   * Creates the exception.
   *
   * @param violations what is wrong, at least one
   */
  public FieldViolationsException(List<FieldViolation> violations) {
    super("The request has " + violations.size() + " invalid field(s)");
    this.found = violations.toArray(new FieldViolation[0]);
  }

  /**
   * Creates the exception, keeping the failure that caused it.
   *
   * @param violations what is wrong, at least one
   * @param cause what caused it
   */
  public FieldViolationsException(List<FieldViolation> violations, Throwable cause) {
    super("The request has " + violations.size() + " invalid field(s)", cause);
    this.found = violations.toArray(new FieldViolation[0]);
  }

  /**
   * What is wrong.
   *
   * @return the violations
   */
  public List<FieldViolation> violations() {
    return Arrays.asList(found.clone());
  }
}
