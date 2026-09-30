package com.rednavis.metaldesk.api.web;

/**
 * An operation that failed for a reason outside the caller's control, after something durable
 * already exists (an order, an inquiry), so that someone can act on it (BRD FR-8.1: "a
 * support-actionable error message rather than a silent failure").
 *
 * <p>It carries a machine-readable code and the reference a support agent searches for. The handler
 * logs it at error level with the reference and the correlation id, and returns the same
 * correlation id in the response, which is what connects a customer's report to the server log.
 */
public class OperationFailedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String errorCode;
  private final String errorReference;

  /**
   * Creates the exception.
   *
   * @param code the machine-readable code
   * @param message what the customer is told
   * @param reference the order number or other reference support can search for
   * @param cause what went wrong
   */
  public OperationFailedException(String code, String message, String reference, Throwable cause) {
    super(message, cause);
    this.errorCode = code;
    this.errorReference = reference;
  }

  /**
   * The machine-readable code.
   *
   * @return the code
   */
  public String code() {
    return errorCode;
  }

  /**
   * The reference support can search for.
   *
   * @return the reference
   */
  public String reference() {
    return errorReference;
  }
}
