package com.rednavis.metaldesk.api.auth;

import java.time.Duration;
import java.time.Instant;

/**
 * A freshly minted access token.
 *
 * @param value the compact signed token
 * @param expiresAt when it stops being valid
 * @param lifetime how long it was issued for
 */
public record IssuedToken(String value, Instant expiresAt, Duration lifetime) {

  /** Redacts the token, so a logged value does not leak it. */
  @Override
  public String toString() {
    return "IssuedToken[expiresAt=" + expiresAt + ']';
  }
}
