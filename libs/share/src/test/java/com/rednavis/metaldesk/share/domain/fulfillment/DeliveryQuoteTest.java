package com.rednavis.metaldesk.share.domain.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.id.FulfillmentTierId;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class DeliveryQuoteTest {

  private static final FulfillmentTierId TIER_ID = new FulfillmentTierId("t-1");
  private static final Money COST = Money.of("25.00", Currency.EUR);
  private static final TransitTime TRANSIT = new TransitTime(1, 3);
  private static final Instant NOW = Instant.parse("2026-09-29T09:00:00Z");

  @Test
  void hasExactlyOneMonetaryComponentAndNoInsuranceOne() {
    final RecordComponent[] components = DeliveryQuote.class.getRecordComponents();
    assertEquals(
        1,
        Arrays.stream(components).filter(component -> component.getType() == Money.class).count());
    assertFalse(
        Arrays.stream(components)
            .anyMatch(
                component -> component.getName().toLowerCase(Locale.ROOT).contains("insurance")));
  }

  @Test
  void quotingTierCopiesItsPriceAndTransitTime() {
    final FulfillmentTier tier =
        new FulfillmentTier(
            TIER_ID,
            new Region("eu-core"),
            Money.of("1000.00", Currency.EUR),
            Weight.of("10", WeightUnit.KILOGRAM),
            COST,
            TRANSIT);
    final DeliveryQuote quote = DeliveryQuote.from(tier, NOW);
    assertEquals(new DeliveryQuote(TIER_ID, COST, TRANSIT, NOW), quote);
  }

  @Test
  void negativeCostIsRefused() {
    final Money negative = Money.of("-0.01", Currency.EUR);
    assertEquals(
        "delivery-quote.cost-negative",
        assertThrows(
                ValidationException.class, () -> new DeliveryQuote(TIER_ID, negative, TRANSIT, NOW))
            .code());
  }

  @Test
  void missingFieldsAreRefused() {
    assertEquals(
        "delivery-quote.field-missing",
        assertThrows(ValidationException.class, () -> new DeliveryQuote(null, COST, TRANSIT, NOW))
            .code());
    assertEquals(
        "delivery-quote.field-missing",
        assertThrows(
                ValidationException.class, () -> new DeliveryQuote(TIER_ID, COST, TRANSIT, null))
            .code());
    assertEquals(
        "delivery-quote.field-missing",
        assertThrows(ValidationException.class, () -> DeliveryQuote.from(null, NOW)).code());
  }
}
