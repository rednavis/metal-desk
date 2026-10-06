package com.rednavis.metaldesk.api.auth;

import java.time.Duration;

/**
 * Every way a sign-in attempt can end, as one type, so that the HTTP response is built from one
 * place and the failure cases cannot drift apart (BRD FR-2.2).
 *
 * <p>There is deliberately a single {@link Rejected}: an unknown identifier, a wrong password, a
 * malformed or missing field and a disabled credential are all the same outcome.
 */
public sealed interface SignInOutcome {

  /**
   * The customer signed in.
   *
   * @param token the issued token
   */
  record Success(IssuedToken token) implements SignInOutcome {}

  /** The attempt failed, for any reason. */
  record Rejected() implements SignInOutcome {}

  /**
   * The source is locked out and the password was not even checked.
   *
   * @param retryAfter how much longer the lock lasts
   */
  record Throttled(Duration retryAfter) implements SignInOutcome {}
}
