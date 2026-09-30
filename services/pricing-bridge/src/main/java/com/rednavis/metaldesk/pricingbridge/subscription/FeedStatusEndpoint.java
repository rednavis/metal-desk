package com.rednavis.metaldesk.pricingbridge.subscription;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

/**
 * The staleness signal, served at {@code /actuator/feed}: how long since a tick last arrived, and
 * whether that is longer than {@code staleAfter}.
 *
 * <p>The age is measured from the last tick's arrival, or from startup if none has arrived, so a
 * feed that never connects becomes stale too. It is a separate endpoint and not part of {@code
 * /actuator/health} on purpose: restarting the container does not bring a silent external feed
 * back, so staleness must not fail a liveness probe. The alert that reads it is T-079's.
 */
@Component
@Endpoint(id = "feed")
public class FeedStatusEndpoint {

  private final ReferencePriceBook book;
  private final Clock clock;
  private final Duration staleAfter;

  /**
   * Creates the endpoint.
   *
   * @param book the book that knows when a tick last arrived
   * @param clock the clock the age is measured with
   * @param properties the staleness threshold
   */
  public FeedStatusEndpoint(
      ReferencePriceBook book, Clock clock, SubscriptionProperties properties) {
    this.book = book;
    this.clock = clock;
    this.staleAfter = properties.staleAfter();
  }

  /**
   * The feed's status.
   *
   * @param stale whether the silence has lasted longer than the threshold
   * @param lastTickAt when a tick last arrived, absent if none has
   * @param ageSeconds seconds since the last tick, or since startup if there was none
   * @param staleAfterSeconds the threshold, in seconds
   */
  public record FeedStatus(
      boolean stale, Instant lastTickAt, long ageSeconds, long staleAfterSeconds) {}

  /**
   * Reports the feed's freshness.
   *
   * @return the status
   */
  @ReadOperation
  public FeedStatus status() {
    final Instant since = book.lastTickAt().orElse(book.startedAt());
    final Duration age = Duration.between(since, clock.instant());
    return new FeedStatus(
        age.compareTo(staleAfter) > 0,
        book.lastTickAt().orElse(null),
        age.toSeconds(),
        staleAfter.toSeconds());
  }
}
