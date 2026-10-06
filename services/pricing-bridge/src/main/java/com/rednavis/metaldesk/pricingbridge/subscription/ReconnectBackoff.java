package com.rednavis.metaldesk.pricingbridge.subscription;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Duration;

/**
 * The delay before the n-th consecutive resubscription: exponential, capped, and jittered.
 *
 * <p>The delay doubles with every consecutive failure, starting from {@code initial} and never
 * exceeding {@code max}. Jitter then shortens it by up to {@code jitter} of itself, so that many
 * instances that lost the feed together do not come back together. Jitter only ever shortens, which
 * keeps the cap a real cap, and because {@code jitter} is below one the delay is never zero, which
 * is what keeps a failing feed from being hammered in a tight loop.
 *
 * @param initial the delay before the first retry, positive
 * @param max the largest delay, not less than {@code initial}
 * @param jitter the fraction of the delay that may be shaved off, from 0 up to but excluding 1
 */
public record ReconnectBackoff(Duration initial, Duration max, double jitter) {

  private static final int MAX_SHIFT = 30;

  /**
   * Validates the policy.
   *
   * @throws ValidationException if a duration is not positive, {@code max} is below {@code
   *     initial}, or the jitter is outside [0, 1)
   */
  public ReconnectBackoff {
    if (initial == null || initial.isZero() || initial.isNegative()) {
      throw new ValidationException("backoff.initial-invalid", "Initial backoff must be positive");
    }
    if (max == null || max.compareTo(initial) < 0) {
      throw new ValidationException(
          "backoff.max-invalid", "Maximum backoff must not be below the initial backoff");
    }
    if (jitter < 0 || jitter >= 1) {
      throw new ValidationException("backoff.jitter-invalid", "Jitter must be in [0, 1)");
    }
  }

  /**
   * Computes a delay.
   *
   * @param attempt how many consecutive failures came before this one; zero for the first retry
   * @param random a uniform value in [0, 1) that decides how much jitter to apply
   * @return the delay: positive, and never more than {@code max}
   */
  public Duration delay(long attempt, double random) {
    final int shift = (int) Math.min(Math.max(attempt, 0), MAX_SHIFT);
    final long factor = 1L << shift;
    final Duration base =
        initial.compareTo(max.dividedBy(factor)) > 0 ? max : initial.multipliedBy(factor);
    final long nanos = Math.round(base.toNanos() * (1 - jitter * random));
    return Duration.ofNanos(Math.max(nanos, 1));
  }
}
