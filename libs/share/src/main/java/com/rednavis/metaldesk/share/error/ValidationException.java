package com.rednavis.metaldesk.share.error;

import java.io.Serial;

/**
 * Signals that the caller sent something the domain rejects — a malformed value, an out-of-range
 * amount, or two amounts in different currencies.
 *
 * <p>Services map this kind to a client error the caller can fix by changing the request. It is
 * distinguished from its siblings by type alone, never by a flag or by message text; the
 * {@linkplain #code() code} identifies the specific failure.
 */
public final class ValidationException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the failure.
   *
   * @param code the stable machine-readable failure code, for example {@code order.closed}
   * @param message the human-readable explanation
   */
  public ValidationException(String code, String message) {
    super(code, message);
  }

  /**
   * Creates the failure, keeping the underlying cause.
   *
   * @param code the stable machine-readable failure code, for example {@code
   *     money.malformed-amount}
   * @param message the human-readable explanation
   * @param cause what made the input invalid
   */
  public ValidationException(String code, String message, Throwable cause) {
    super(code, message, cause);
  }
}
