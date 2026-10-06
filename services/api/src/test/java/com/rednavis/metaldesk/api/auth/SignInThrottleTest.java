package com.rednavis.metaldesk.api.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The FR-2.2 policy: three consecutive failures lock a source for a cool-down. */
class SignInThrottleTest {

  private static final String SOURCE = "203.0.113.7";
  private static final Duration COOL_DOWN = Duration.ofMinutes(15);

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T10:00:00Z"));
  private final SignInThrottle throttle =
      new SignInThrottle(new ThrottleProperties(3, COOL_DOWN, List.of()), clock);

  private void fail(int times) {
    for (int i = 0; i < times; i++) {
      throttle.recordFailure(SOURCE);
    }
  }

  @Test
  void twoFailuresDoNotLock() {
    fail(2);

    assertTrue(throttle.lockedFor(SOURCE).isEmpty());
  }

  @Test
  void thirdFailureLocksForTheCoolDown() {
    fail(3);

    assertEquals(COOL_DOWN, throttle.lockedFor(SOURCE).orElseThrow());
  }

  @Test
  void otherSourcesAreNotAffected() {
    fail(3);

    assertTrue(throttle.lockedFor("198.51.100.1").isEmpty());
  }

  @Test
  void successResetsTheCounter() {
    fail(2);
    throttle.recordSuccess(SOURCE);
    fail(2);

    assertTrue(throttle.lockedFor(SOURCE).isEmpty());
  }

  @Test
  void lockExpiresAfterTheCoolDownAndCountingStartsAgain() {
    fail(3);
    clock.advance(COOL_DOWN.plusSeconds(1));

    assertTrue(throttle.lockedFor(SOURCE).isEmpty());
    fail(2);
    assertTrue(throttle.lockedFor(SOURCE).isEmpty());
    fail(1);
    assertTrue(throttle.lockedFor(SOURCE).isPresent());
  }

  @Test
  void attemptsWhileLockedDoNotExtendTheLock() {
    fail(3);
    clock.advance(Duration.ofMinutes(10));
    fail(1);

    assertEquals(Duration.ofMinutes(5), throttle.lockedFor(SOURCE).orElseThrow());
  }
}
