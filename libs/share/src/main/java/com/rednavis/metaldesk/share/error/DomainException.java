package com.rednavis.metaldesk.share.error;

import java.io.Serial;

/**
 * Base unchecked supertype for every domain failure (BRD FR-8.1: failures must be
 * support-actionable, never silent).
 *
 * <p>The hierarchy is deliberately closed to three kinds — {@link ValidationException}, {@link
 * NotFoundException} and {@link ConflictException} — because each kind becomes one HTTP mapping in
 * every service. The constructor is package-private and the type is {@code sealed}, so a fourth
 * kind cannot be added outside this package by accident; adding one is a deliberate change to this
 * file.
 *
 * <p>Every failure carries a stable, machine-readable {@link #code()} next to its human message, so
 * an error envelope can be matched by clients and support staff without string-matching prose. It
 * deliberately holds no HTTP status: mapping a kind to a status is the web layer's job, not the
 * domain's.
 */
public sealed class DomainException extends RuntimeException
    permits ValidationException, NotFoundException, ConflictException {

  @Serial private static final long serialVersionUID = 1L;

  private final String failureCode;

  /* default */ DomainException(String code, String message) {
    super(message);
    this.failureCode = code;
  }

  /* default */ DomainException(String code, String message, Throwable cause) {
    super(message, cause);
    this.failureCode = code;
  }

  /**
   * Returns the stable machine-readable failure code, for example {@code money.currency-mismatch}.
   *
   * @return the failure code, never blank
   */
  public String code() {
    return failureCode;
  }
}
