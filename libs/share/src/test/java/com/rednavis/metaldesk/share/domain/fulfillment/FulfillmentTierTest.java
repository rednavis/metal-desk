package com.rednavis.metaldesk.share.domain.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.id.FulfillmentTierId;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import org.junit.jupiter.api.Test;

class FulfillmentTierTest {

  private static final FulfillmentTierId ID = new FulfillmentTierId("t-1");
  private static final Region REGION = new Region("eu-core");
  private static final Money CEILING = Money.of("1000.00", Currency.EUR);
  private static final Weight KILOS = Weight.of("10", WeightUnit.KILOGRAM);
  private static final Money PRICE = Money.of("25.00", Currency.EUR);
  private static final TransitTime TRANSIT = new TransitTime(1, 3);

  @Test
  void acceptsAnOrderWithinBothCeilingsInclusive() {
    final FulfillmentTier tier = new FulfillmentTier(ID, REGION, CEILING, KILOS, PRICE, TRANSIT);
    assertTrue(tier.accepts(CEILING, KILOS));
    assertFalse(tier.accepts(Money.of("1000.01", Currency.EUR), KILOS));
    assertFalse(tier.accepts(CEILING, Weight.of("10.001", WeightUnit.KILOGRAM)));
  }

  @Test
  void weightCeilingCompareAcrossUnits() {
    final FulfillmentTier tier = new FulfillmentTier(ID, REGION, CEILING, KILOS, PRICE, TRANSIT);
    assertFalse(tier.exceedsWeight(Weight.of("10000", WeightUnit.GRAM)));
    assertTrue(tier.exceedsWeight(Weight.of("10001", WeightUnit.GRAM)));
  }

  @Test
  void freeDeliveryIsAllowed() {
    final Money free = Money.zero(Currency.EUR);
    assertEquals(
        free, new FulfillmentTier(ID, REGION, CEILING, KILOS, free, TRANSIT).deliveryPrice());
  }

  @Test
  void ceilingsMustBeGreaterThanZero() {
    final Money zero = Money.zero(Currency.EUR);
    final Weight noWeight = Weight.of("0", WeightUnit.KILOGRAM);
    assertEquals(
        "fulfillment-tier.ceiling-invalid",
        assertThrows(
                ValidationException.class,
                () -> new FulfillmentTier(ID, REGION, zero, KILOS, PRICE, TRANSIT))
            .code());
    assertEquals(
        "fulfillment-tier.ceiling-invalid",
        assertThrows(
                ValidationException.class,
                () -> new FulfillmentTier(ID, REGION, CEILING, noWeight, PRICE, TRANSIT))
            .code());
  }

  @Test
  void deliveryPriceMustNotBeNegativeAndMustShareTheCurrency() {
    final Money negative = Money.of("-1.00", Currency.EUR);
    final Money dollars = Money.of("25.00", Currency.USD);
    assertEquals(
        "fulfillment-tier.price-invalid",
        assertThrows(
                ValidationException.class,
                () -> new FulfillmentTier(ID, REGION, CEILING, KILOS, negative, TRANSIT))
            .code());
    assertEquals(
        "fulfillment-tier.currency-mismatch",
        assertThrows(
                ValidationException.class,
                () -> new FulfillmentTier(ID, REGION, CEILING, KILOS, dollars, TRANSIT))
            .code());
  }

  @Test
  void missingFieldsAreRefused() {
    assertEquals(
        "fulfillment-tier.field-missing",
        assertThrows(
                ValidationException.class,
                () -> new FulfillmentTier(null, REGION, CEILING, KILOS, PRICE, TRANSIT))
            .code());
    assertEquals(
        "fulfillment-tier.field-missing",
        assertThrows(
                ValidationException.class,
                () -> new FulfillmentTier(ID, REGION, CEILING, KILOS, PRICE, null))
            .code());
  }
}
