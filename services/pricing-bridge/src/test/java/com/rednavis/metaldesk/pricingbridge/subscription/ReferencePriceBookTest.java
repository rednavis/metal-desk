package com.rednavis.metaldesk.pricingbridge.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.pricingbridge.feed.ReferencePriceTick;
import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** The book keeps the latest and the previous observation, and nothing more. */
class ReferencePriceBookTest {

  private static final String SIXTY = "60.00";
  private static final String SIXTY_ONE = "61.00";
  private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

  private final ReferencePriceBook book = new ReferencePriceBook(Clock.systemUTC());

  private static ReferencePriceTick tick(String price, long seconds) {
    return new ReferencePriceTick(
        Metal.GOLD, Money.of(price, Currency.EUR), T0.plusSeconds(seconds), "test");
  }

  @Test
  void firstObservationHasNoPrevious() {
    book.record(tick(SIXTY, 0));

    assertTrue(book.find(Metal.GOLD).orElseThrow().previous().isEmpty());
  }

  @Test
  void newObservationDisplacesLatestIntoPrevious() {
    book.record(tick(SIXTY, 0));
    book.record(tick(SIXTY_ONE, 1));
    book.record(tick("62.00", 2));

    final ReferencePriceBook.Observation observation = book.find(Metal.GOLD).orElseThrow();
    assertEquals(Money.of("62.00", Currency.EUR), observation.latest().pricePerGram());
    assertEquals(
        Money.of(SIXTY_ONE, Currency.EUR), observation.previous().orElseThrow().pricePerGram());
  }

  @Test
  void repeatOfLatestObservationKeepsPreviousPrice() {
    book.record(tick(SIXTY, 0));
    book.record(tick(SIXTY_ONE, 1));
    book.record(tick(SIXTY_ONE, 1));

    assertEquals(
        Money.of(SIXTY, Currency.EUR),
        book.find(Metal.GOLD).orElseThrow().previous().orElseThrow().pricePerGram());
  }
}
