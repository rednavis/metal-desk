package com.rednavis.metaldesk.admin.security;

import java.io.Serial;

/** Signals that a request carries no staff identity. Mapped to 401. */
public final class UnauthorizedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** Creates the failure. */
  public UnauthorizedException() {
    super("A staff identity is required");
  }
}
