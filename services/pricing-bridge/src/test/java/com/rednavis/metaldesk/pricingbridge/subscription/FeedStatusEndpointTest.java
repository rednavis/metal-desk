package com.rednavis.metaldesk.pricingbridge.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.pricingbridge.feed.ReferencePriceTick;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/** The endpoint reports the age of the last tick and flags it stale past the threshold. */
class FeedStatusEndpointTest {

  private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
  private static final Duration THRESHOLD = Duration.ofSeconds(30);
  private static final SubscriptionProperties PROPERTIES =
      new SubscriptionProperties(Duration.ofSeconds(1), Duration.ofSeconds(8), 0, THRESHOLD);

  private static Clock at(Instant instant) {
    return Clock.fixed(instant, ZoneOffset.UTC);
  }

  private static FeedStatusEndpoint.FeedStatus statusAfter(Duration sinceLastTick) {
    final ReferencePriceBook book = new ReferencePriceBook(at(T0));
    book.record(new ReferencePriceTick(Metal.GOLD, Money.of("60.00", Currency.EUR), T0, "test"));
    return new FeedStatusEndpoint(book, at(T0.plus(sinceLastTick)), PROPERTIES).status();
  }

  @Test
  void isFreshWithinTheThreshold() {
    final FeedStatusEndpoint.FeedStatus status = statusAfter(Duration.ofSeconds(30));

    assertFalse(status.stale());
    assertEquals(30, status.ageSeconds());
    assertEquals(T0, status.lastTickAt());
    assertEquals(30, status.staleAfterSeconds());
  }

  @Test
  void isStaleOnceTheThresholdIsPassed() {
    assertTrue(statusAfter(Duration.ofSeconds(31)).stale());
  }

  @Test
  void feedThatNeverDeliveredBecomesStaleFromStartup() {
    final ReferencePriceBook book = new ReferencePriceBook(at(T0));

    final FeedStatusEndpoint.FeedStatus waiting =
        new FeedStatusEndpoint(book, at(T0.plusSeconds(10)), PROPERTIES).status();
    final FeedStatusEndpoint.FeedStatus silent =
        new FeedStatusEndpoint(book, at(T0.plusSeconds(31)), PROPERTIES).status();

    assertFalse(waiting.stale());
    assertNull(waiting.lastTickAt());
    assertTrue(silent.stale());
  }
}
