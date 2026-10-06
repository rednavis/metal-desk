package com.rednavis.metaldesk.share.error;

import java.io.Serial;

/**
 * Signals that a referenced aggregate does not exist.
 *
 * <p>Services map this kind to a missing resource. It is distinguished from its siblings by type
 * alone, never by a flag or by message text; the {@linkplain #code() code} identifies the specific
 * failure.
 */
public final class NotFoundException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the failure.
   *
   * @param code the stable machine-readable failure code, for example {@code order.closed}
   * @param message the human-readable explanation
   */
  public NotFoundException(String code, String message) {
    super(code, message);
  }
}
