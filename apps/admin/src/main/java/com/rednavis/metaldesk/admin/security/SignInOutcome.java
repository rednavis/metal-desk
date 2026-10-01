package com.rednavis.metaldesk.admin.security;

import java.time.Duration;

/**
 * Every way a sign-in attempt can end, as one type, so the HTTP response is built from one place
 * and the failure cases cannot drift apart.
 *
 * <p>There is deliberately a single {@link Rejected}: an unknown login, a wrong password, a missing
 * field and a disabled user are all the same outcome.
 */
public sealed interface SignInOutcome {

  /**
   * The user signed in.
   *
   * @param response what to send back
   */
  record Success(SignInResponse response) implements SignInOutcome {}

  /** The attempt failed, for any reason. */
  record Rejected() implements SignInOutcome {}

  /**
   * The attempt is locked out and the password was not even checked.
   *
   * @param retryAfter how much longer the lock lasts
   */
  record Throttled(Duration retryAfter) implements SignInOutcome {}
}
