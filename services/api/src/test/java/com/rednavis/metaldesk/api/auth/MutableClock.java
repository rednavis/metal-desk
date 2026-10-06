package com.rednavis.metaldesk.api.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock that only moves when a test says so. */
public final class MutableClock extends Clock {

  private Instant now;

  /**
   * Creates the clock.
   *
   * @param start the time it starts at
   */
  public MutableClock(Instant start) {
    super();
    this.now = start;
  }

  /**
   * Moves the clock forward.
   *
   * @param amount how far
   */
  public void advance(Duration amount) {
    now = now.plus(amount);
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
