package com.rednavis.metaldesk.api.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.domain.pricing.ReferencePrice;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The cache keeps the latest and the previous observation, and never invents a previous one. */
class ReferencePriceCacheTest {

  private static final String OLD = "60.00";
  private static final String NEW = "61.00";
  private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

  private final ReferencePriceCache cache = new ReferencePriceCache();

  private static ReferencePrice gold(String price, Instant at) {
    return new ReferencePrice(Metal.GOLD, Money.of(price, Currency.EUR), at);
  }

  @Test
  void coldCacheHasNothing() {
    assertTrue(cache.all().isEmpty());
    assertTrue(cache.find(Metal.GOLD).isEmpty());
  }

  @Test
  void firstObservationHasNoPrevious() {
    cache.record(gold(OLD, T0));

    assertEquals(gold(OLD, T0), cache.find(Metal.GOLD).orElseThrow().latest());
    assertTrue(cache.find(Metal.GOLD).orElseThrow().previous().isEmpty());
  }

  @Test
  void secondObservationBecomesLatestAndFirstBecomesPrevious() {
    cache.record(gold(OLD, T0));
    cache.record(gold(NEW, T0.plusSeconds(20)));

    final ReferencePriceCache.Observation observation = cache.find(Metal.GOLD).orElseThrow();
    assertEquals(gold(NEW, T0.plusSeconds(20)), observation.latest());
    assertEquals(gold(OLD, T0), observation.previous().orElseThrow());
  }

  @Test
  void repeatOfTheLatestObservationDoesNotOverwriteThePrevious() {
    cache.record(gold(OLD, T0));
    cache.record(gold(NEW, T0.plusSeconds(20)));
    cache.record(gold(NEW, T0.plusSeconds(20)));

    assertEquals(gold(OLD, T0), cache.find(Metal.GOLD).orElseThrow().previous().orElseThrow());
  }
}
