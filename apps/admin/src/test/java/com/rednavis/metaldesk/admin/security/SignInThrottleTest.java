package com.rednavis.metaldesk.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The lock-out: after enough failures, for the cool-down, per key, and cleared by a success. */
class SignInThrottleTest {

  private static final String KEY = "admin|10.0.0.1";
  private static final String OTHER = "admin|10.0.0.2";

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T10:00:00Z"));
  private SignInThrottle throttle;

  @BeforeEach
  void createThrottle() {
    throttle = new SignInThrottle(new ThrottleProperties(3, Duration.ofMinutes(15)), clock);
  }

  private void fail(String key, int times) {
    for (int i = 0; i < times; i++) {
      throttle.recordFailure(key);
    }
  }

  @Test
  void freshKeyMayTry() {
    assertEquals(Optional.empty(), throttle.lockedFor(KEY));
  }

  @Test
  void failuresBelowTheLimitDoNotLock() {
    fail(KEY, 2);
    assertEquals(Optional.empty(), throttle.lockedFor(KEY));
  }

  @Test
  void reachingTheLimitLocksForTheCoolDownAndOnlyThatKey() {
    fail(KEY, 3);

    assertEquals(Optional.of(Duration.ofMinutes(15)), throttle.lockedFor(KEY));
    assertEquals(Optional.empty(), throttle.lockedFor(OTHER));
  }

  @Test
  void theLockCountsDownAndLapses() {
    fail(KEY, 3);
    clock.advance(Duration.ofMinutes(10));
    assertEquals(Optional.of(Duration.ofMinutes(5)), throttle.lockedFor(KEY));

    clock.advance(Duration.ofMinutes(5));
    assertEquals(Optional.empty(), throttle.lockedFor(KEY));
  }

  @Test
  void failuresWhileLockedDoNotExtendTheLock() {
    fail(KEY, 3);
    clock.advance(Duration.ofMinutes(5));
    fail(KEY, 2);

    assertEquals(Optional.of(Duration.ofMinutes(10)), throttle.lockedFor(KEY));
  }

  @Test
  void countStartsAgainOnceLockLapses() {
    fail(KEY, 3);
    clock.advance(Duration.ofMinutes(16));

    fail(KEY, 1);
    assertEquals(Optional.empty(), throttle.lockedFor(KEY));
    fail(KEY, 2);
    assertTrue(throttle.lockedFor(KEY).isPresent());
  }

  @Test
  void successClearsTheFailures() {
    fail(KEY, 2);
    throttle.recordSuccess(KEY);
    fail(KEY, 2);

    assertEquals(Optional.empty(), throttle.lockedFor(KEY));
  }

  /** A clock the test moves by hand. */
  private static final class MutableClock extends Clock {

    private Instant now;

    /* default */ MutableClock(Instant start) {
      super();
      this.now = start;
    }

    /* default */ void advance(Duration by) {
      now = now.plus(by);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
