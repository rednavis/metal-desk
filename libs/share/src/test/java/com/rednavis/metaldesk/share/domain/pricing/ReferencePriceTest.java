package com.rednavis.metaldesk.share.domain.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReferencePriceTest {

  private static final Instant OBSERVED = Instant.parse("2026-09-28T10:15:30Z");
  private static final Money PRICE = Money.of("60.00", Currency.EUR);

  @Test
  void carriesTheObservationInstant() {
    assertEquals(OBSERVED, new ReferencePrice(Metal.GOLD, PRICE, OBSERVED).observedAt());
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "-1.00"})
  void nonPositivePriceIsRefused(String amount) {
    final Money price = Money.of(amount, Currency.EUR);
    assertEquals(
        "reference-price.not-positive",
        assertThrows(
                ValidationException.class, () -> new ReferencePrice(Metal.GOLD, price, OBSERVED))
            .code());
  }

  @Test
  void missingMetalIsRefused() {
    assertEquals(
        "reference-price.metal-missing",
        assertThrows(ValidationException.class, () -> new ReferencePrice(null, PRICE, OBSERVED))
            .code());
  }

  @Test
  void missingPriceIsRefused() {
    assertEquals(
        "reference-price.price-missing",
        assertThrows(
                ValidationException.class, () -> new ReferencePrice(Metal.GOLD, null, OBSERVED))
            .code());
  }

  @Test
  void missingInstantIsRefused() {
    assertEquals(
        "reference-price.instant-missing",
        assertThrows(ValidationException.class, () -> new ReferencePrice(Metal.GOLD, PRICE, null))
            .code());
  }
}
