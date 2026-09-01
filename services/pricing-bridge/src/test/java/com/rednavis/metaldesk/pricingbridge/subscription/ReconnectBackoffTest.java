package com.rednavis.metaldesk.pricingbridge.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The reconnect delay doubles, is capped, and is never zero. */
class ReconnectBackoffTest {

  private static final Duration ONE_SECOND = Duration.ofSeconds(1);
  private static final Duration EIGHT_SECONDS = Duration.ofSeconds(8);

  @Test
  void doublesFromTheInitialDelayUpToTheCap() {
    final ReconnectBackoff backoff = new ReconnectBackoff(ONE_SECOND, EIGHT_SECONDS, 0);

    assertEquals(Duration.ofSeconds(1), backoff.delay(0, 0));
    assertEquals(Duration.ofSeconds(2), backoff.delay(1, 0));
    assertEquals(Duration.ofSeconds(4), backoff.delay(2, 0));
    assertEquals(Duration.ofSeconds(8), backoff.delay(3, 0));
    assertEquals(Duration.ofSeconds(8), backoff.delay(4, 0));
  }

  @Test
  void neverExceedsTheCapNorOverflowAtAnyAttempt() {
    final ReconnectBackoff backoff = new ReconnectBackoff(ONE_SECOND, EIGHT_SECONDS, 0.5);

    for (final long attempt : new long[] {0, 1, 5, 30, 31, 63, 64, 1_000_000, Long.MAX_VALUE}) {
      final Duration delay = backoff.delay(attempt, 0);
      assertTrue(delay.compareTo(EIGHT_SECONDS) <= 0, "attempt " + attempt);
      assertTrue(delay.compareTo(Duration.ZERO) > 0, "attempt " + attempt);
    }
  }

  @Test
  void jitterOnlyShortensAndNeverReachesZero() {
    final ReconnectBackoff backoff = new ReconnectBackoff(ONE_SECOND, EIGHT_SECONDS, 0.5);

    assertEquals(Duration.ofMillis(1000), backoff.delay(0, 0));
    assertEquals(Duration.ofMillis(500), backoff.delay(0, 0.999_999_999_9));
    assertTrue(backoff.delay(0, 0.999_999_999_9).compareTo(Duration.ZERO) > 0);
  }

  @Test
  void refusesPolicyThatCouldSpin() {
    assertThrows(
        ValidationException.class, () -> new ReconnectBackoff(Duration.ZERO, ONE_SECOND, 0));
    assertThrows(
        ValidationException.class, () -> new ReconnectBackoff(EIGHT_SECONDS, ONE_SECOND, 0));
    assertThrows(
        ValidationException.class, () -> new ReconnectBackoff(ONE_SECOND, EIGHT_SECONDS, 1));
  }
}
