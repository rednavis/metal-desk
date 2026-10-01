package com.rednavis.metaldesk.admin.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Locks a login out for a cool-down after too many consecutive failures from one address.
 *
 * <p>The key is the login together with the caller's address, so someone guessing a password does
 * not also lock the real user out from their own machine. The counter is per instance and in
 * memory: with several instances an attacker gets that many times the attempts, and a restart
 * forgets every lock. It slows guessing down; it is not a guarantee.
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
   * Tells whether an attempt is currently locked out.
   *
   * @param key the login and address of the attempt
   * @return how much longer it is locked, or empty if it may try
   */
  public Optional<Duration> lockedFor(String key) {
    final Instant now = clock.instant();
    final Entry entry = entries.get(key);
    return entry == null || entry.lockedUntil() == null || !entry.lockedUntil().isAfter(now)
        ? Optional.empty()
        : Optional.of(Duration.between(now, entry.lockedUntil()));
  }

  /**
   * Records a failed sign-in, locking the key once it has had too many in a row.
   *
   * @param key the login and address of the attempt
   */
  public void recordFailure(String key) {
    final Instant now = clock.instant();
    if (entries.size() > SWEEP_THRESHOLD) {
      entries.values().removeIf(entry -> stale(entry, now));
    }
    entries.compute(key, (k, held) -> after(held, now));
  }

  /**
   * Records a successful sign-in, which clears the failures.
   *
   * @param key the login and address of the attempt
   */
  public void recordSuccess(String key) {
    entries.remove(key);
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

  /** What is known about one key. */
  private record Entry(int failures, Instant lockedUntil, Instant lastFailure) {}
}
