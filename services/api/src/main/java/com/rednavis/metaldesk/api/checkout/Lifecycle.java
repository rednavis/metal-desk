package com.rednavis.metaldesk.api.checkout;

import java.time.Duration;
import java.time.Instant;

/**
 * When a checkout session was made and changed, how many times, and when it expires.
 *
 * @param version the change counter used for compare-and-set
 * @param createdAt when the session started
 * @param updatedAt when it last changed
 * @param expiresAt when it is deleted
 */
public record Lifecycle(long version, Instant createdAt, Instant updatedAt, Instant expiresAt) {

  /**
   * Starts a lifecycle.
   *
   * @param now the current time
   * @param ttl how long an untouched session lives
   * @return version 0, created and updated now
   */
  public static Lifecycle begin(Instant now, Duration ttl) {
    return new Lifecycle(0, now, now, now.plus(ttl));
  }

  /**
   * Records a change.
   *
   * @param now the current time
   * @param ttl how long an untouched session lives from now
   * @return the next version, updated now, with the expiry pushed out
   */
  public Lifecycle touched(Instant now, Duration ttl) {
    return new Lifecycle(version + 1, createdAt, now, now.plus(ttl));
  }
}
