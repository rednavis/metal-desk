package com.rednavis.metaldesk.share.error;

import java.io.Serial;

/**
 * Signals that the request is valid but the current state forbids it — a closed order, an expired
 * quote.
 *
 * <p>Services map this kind to a state conflict the caller can only resolve by re-reading the
 * current state. It is distinguished from its siblings by type alone, never by a flag or by message
 * text; the {@linkplain #code() code} identifies the specific failure.
 */
public final class ConflictException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the failure.
   *
   * @param code the stable machine-readable failure code, for example {@code order.closed}
   * @param message the human-readable explanation
   */
  public ConflictException(String code, String message) {
    super(code, message);
  }
}
