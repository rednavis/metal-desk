package com.rednavis.metaldesk.api.marketdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.pricing.ReferencePrice;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The fake feed is repeatable and yields a positive price for every metal. */
class FakeMarketDataClientTest {

  private static final Clock CLOCK =
      Clock.fixed(Instant.parse("2026-10-01T10:00:00Z"), ZoneOffset.UTC);

  @Test
  void yieldsOnePositivePricePerMetal() {
    final List<ReferencePrice> prices =
        new FakeMarketDataClient(CLOCK).fetchLatest().collectList().block();

    assertEquals(Metal.values().length, prices.size());
    assertTrue(prices.stream().allMatch(price -> price.pricePerGram().amount().signum() > 0));
  }

  @Test
  void sameSeedGivesTheSameSequence() {
    final FakeMarketDataClient first = new FakeMarketDataClient(CLOCK);
    final FakeMarketDataClient second = new FakeMarketDataClient(CLOCK);

    for (int i = 0; i < 5; i++) {
      assertEquals(
          first.fetchLatest().collectList().block(), second.fetchLatest().collectList().block());
    }
  }
}
