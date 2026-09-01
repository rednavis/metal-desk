package com.rednavis.metaldesk.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Once the table is large, old unlocked entries are swept so it cannot grow without limit. */
class SignInThrottleSweepTest {

  private static final Instant START = Instant.parse("2026-10-01T10:00:00Z");
  private static final String LOCKED = "locked";
  private static final int MANY = 10_001;

  @Test
  void staleEntriesAreSweptWhenTheTableIsLargeAndLockedOnesKept() {
    final ThrottleProperties properties =
        new ThrottleProperties(2, Duration.ofMinutes(15), List.of());
    final Clock early = Clock.fixed(START, ZoneOffset.UTC);
    final SignInThrottle first = new SignInThrottle(properties, early);
    for (int i = 0; i < MANY; i++) {
      first.recordFailure("k" + i);
    }
    first.recordFailure(LOCKED);
    first.recordFailure(LOCKED);

    final Clock late = Clock.fixed(START.plus(Duration.ofMinutes(10)), ZoneOffset.UTC);
    final SignInThrottle second = new SignInThrottle(properties, late);
    second.recordFailure("fresh");

    assertEquals(Optional.empty(), second.lockedFor("k1"));
    assertEquals(Optional.empty(), first.lockedFor("k1"));
    assertEquals(Optional.of(Duration.ofMinutes(15)), first.lockedFor(LOCKED));
  }

  @Test
  void sweepOnSameTableRemovesOnlyStale() {
    final ThrottleProperties properties =
        new ThrottleProperties(2, Duration.ofMinutes(15), List.of());
    final MovingClock clock = new MovingClock();
    final SignInThrottle throttle = new SignInThrottle(properties, clock);
    for (int i = 0; i < MANY; i++) {
      throttle.recordFailure("k" + i);
    }
    throttle.recordFailure(LOCKED);
    throttle.recordFailure(LOCKED);
    clock.now = START.plus(Duration.ofMinutes(16));

    throttle.recordFailure("trigger");

    assertEquals(Optional.empty(), throttle.lockedFor(LOCKED));
    assertEquals(Optional.empty(), throttle.lockedFor("k5"));
  }

  /** A clock whose time the test sets. */
  private static final class MovingClock extends Clock {

    private Instant now = START;

    @Override
    public java.time.ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
