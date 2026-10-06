package com.rednavis.metaldesk.share.domain.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SellablePriceTest {

  private static final Money UNIT = Money.of("1959.32", Currency.EUR);
  private static final PriceRule RULE =
      new PriceRule(Margin.of("5"), new PriceRule.Scope.ForCategory(new CategoryId("c-1")));
  private static final ReferencePrice REFERENCE =
      new ReferencePrice(
          Metal.GOLD, Money.of("60.00", Currency.EUR), Instant.parse("2026-09-28T10:15:30Z"));

  @Test
  void exposesTheRuleAndReferenceItCameFrom() {
    final SellablePrice price = new SellablePrice(UNIT, RULE, REFERENCE);
    assertEquals(UNIT, price.unitPrice());
    assertEquals(RULE, price.rule());
    assertEquals(REFERENCE, price.reference());
  }

  @Test
  void priceInAnotherCurrencyThanItsReferenceIsRefused() {
    final Money dollars = Money.of("1959.32", Currency.USD);
    assertEquals(
        "sellable-price.currency-mismatch",
        assertThrows(ValidationException.class, () -> new SellablePrice(dollars, RULE, REFERENCE))
            .code());
  }

  @Test
  void missingFieldsAreRefused() {
    assertEquals(
        "sellable-price.price-missing",
        assertThrows(ValidationException.class, () -> new SellablePrice(null, RULE, REFERENCE))
            .code());
    assertEquals(
        "sellable-price.rule-missing",
        assertThrows(ValidationException.class, () -> new SellablePrice(UNIT, null, REFERENCE))
            .code());
    assertEquals(
        "sellable-price.reference-missing",
        assertThrows(ValidationException.class, () -> new SellablePrice(UNIT, RULE, null)).code());
  }
}
