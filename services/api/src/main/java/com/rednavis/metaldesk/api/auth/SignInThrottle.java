package com.rednavis.metaldesk.api.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * The sign-in throttle of BRD FR-2.2: after a number of consecutive failures from one source, that
 * source is locked out for a cool-down. Both values are configuration ({@link ThrottleProperties});
 * the defaults are the BRD's illustrative 3 failures.
 *
 * <p><strong>This is a per-instance, in-memory counter, and that makes it weaker than it
 * looks.</strong> The service is stateless and horizontally scaled (Architecture section 5), so
 * with N instances an attacker gets up to N times the attempts, a restart forgets every lock, and
 * an instance never sees failures another one handled. It is good enough to exercise the behaviour
 * in this phase and is <em>not</em> the FR-2.2 guarantee in a real deployment: that needs a shared
 * store, which is deliberately out of scope here.
 *
 * <p>An attempt from a locked source is refused without checking the password and does not extend
 * the lock. A success clears the source's failures. Entries that are neither locked nor recent are
 * swept when the table grows, so the table cannot grow without bound.
 */
@Component
public class SignInThrottle {

  private static final int SWEEP_THRESHOLD = 10_000;

  private final ThrottleProperties properties;
  private final Clock clock;
  private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

  /**
   * Creates the throttle.
   *
   * @param properties the failure count and cool-down
   * @param clock the source of time
   */
  public SignInThrottle(ThrottleProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Tells whether a source is currently locked out.
   *
   * @param source the source key, see {@link ClientAddressResolver}
   * @return how much longer it is locked, or empty if it may try
   */
  public Optional<Duration> lockedFor(String source) {
    final Instant now = clock.instant();
    final Entry entry = entries.get(source);
    return entry == null || entry.lockedUntil() == null || !entry.lockedUntil().isAfter(now)
        ? Optional.empty()
        : Optional.of(Duration.between(now, entry.lockedUntil()));
  }

  /**
   * Records a failed sign-in, locking the source once it has had too many in a row.
   *
   * @param source the source key
   */
  public void recordFailure(String source) {
    final Instant now = clock.instant();
    if (entries.size() > SWEEP_THRESHOLD) {
      entries.values().removeIf(entry -> stale(entry, now));
    }
    entries.compute(source, (key, held) -> after(held, now));
  }

  /**
   * Records a successful sign-in, which clears the source's failures.
   *
   * @param source the source key
   */
  public void recordSuccess(String source) {
    entries.remove(source);
  }

  private Entry after(Entry held, Instant now) {
    final boolean locked = held != null && held.lockedUntil() != null;
    final boolean lapsed = locked && !held.lockedUntil().isAfter(now);
    final int failures = held == null || lapsed ? 1 : held.failures() + 1;
    final Instant lockedUntil =
        failures >= properties.maxFailures() ? now.plus(properties.coolDown()) : null;
    return locked && !lapsed ? held : new Entry(failures, lockedUntil, now);
  }

  private boolean stale(Entry entry, Instant now) {
    final boolean unlocked = entry.lockedUntil() == null || !entry.lockedUntil().isAfter(now);
    return unlocked && entry.lastFailure().plus(properties.coolDown()).isBefore(now);
  }

  /** What is known about one source. */
  private record Entry(int failures, Instant lockedUntil, Instant lastFailure) {}
}
