package com.rednavis.metaldesk.share.error;

import java.io.Serial;

/**
 * Signals that the request is valid but the current state forbids it — a closed order, an expired
 * quote.
 *
 * <p>Services map this kind to a state conflict the caller can only resolve by re-reading the
 * current state. It is distinguished from its siblings by type alone, never by a flag or by message
 * text; the {@linkplain #code() code} identifies the specific failure.
 *
 * <p>This class is {@code non-sealed} so that a domain package can define a conflict that carries
 * its own detail, such as an illegal order transition naming the status and the trigger. Such a
 * subclass is still the conflict kind: it is not a fourth kind, and every service maps it to the
 * same HTTP class as any other conflict. Do not use this to add a kind that is not a state
 * conflict.
 */
public non-sealed class ConflictException extends DomainException {

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
