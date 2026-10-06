package com.rednavis.metaldesk.share.domain.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.id.FulfillmentTierId;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The multi-tier rules a BRD leaves open: which tier wins, and which ceiling is named. */
class TierSelectorChoiceTest {

  private static final Region EU_CORE = new Region("eu-core");
  private static final Instant NOW = Instant.parse("2026-09-29T09:00:00Z");
  private static final String HIGH_CEILING = "20000.00";
  private static final String STANDARD_PRICE = "25.00";
  private static final Weight LIGHT = Weight.of("5", WeightUnit.KILOGRAM);

  private static Money eur(String amount) {
    return Money.of(amount, Currency.EUR);
  }

  private static FulfillmentTier tier(
      String id, String valueCeiling, String kg, String price, int minDays, int maxDays) {
    return new FulfillmentTier(
        new FulfillmentTierId(id),
        EU_CORE,
        eur(valueCeiling),
        Weight.of(kg, WeightUnit.KILOGRAM),
        eur(price),
        new TransitTime(minDays, maxDays));
  }

  private static TierEvaluation select(Money value, Weight weight, List<FulfillmentTier> tiers) {
    return TierSelector.select(EU_CORE, value, weight, tiers, NOW);
  }

  @Test
  void cheapestQualifyingTierWinsWhateverTheListOrder() {
    final FulfillmentTier express = tier("express", HIGH_CEILING, "30", "60.00", 1, 1);
    final FulfillmentTier standard = tier("standard", HIGH_CEILING, "30", STANDARD_PRICE, 3, 5);
    final FulfillmentTier expected = standard;
    for (final List<FulfillmentTier> order :
        List.of(List.of(express, standard), List.of(standard, express))) {
      final TierEvaluation.Priced priced =
          assertInstanceOf(TierEvaluation.Priced.class, select(eur("100.00"), LIGHT, order));
      assertEquals(expected.id(), priced.quote().tierId());
    }
  }

  @Test
  void tierTheOrderExceedsIsNotChosenEvenIfCheaper() {
    final FulfillmentTier small = tier("small", "500.00", "30", "5.00", 3, 5);
    final FulfillmentTier large = tier("large", HIGH_CEILING, "30", "40.00", 3, 5);
    final TierEvaluation.Priced priced =
        assertInstanceOf(
            TierEvaluation.Priced.class, select(eur("900.00"), LIGHT, List.of(small, large)));
    assertEquals(large.id(), priced.quote().tierId());
  }

  @Test
  void equalPricesGoToTheFasterTierThenTheLowerId() {
    final FulfillmentTier slow = tier("a-slow", HIGH_CEILING, "30", STANDARD_PRICE, 4, 6);
    final FulfillmentTier fast = tier("b-fast", HIGH_CEILING, "30", STANDARD_PRICE, 1, 2);
    final FulfillmentTier alsoFast = tier("a-also-fast", HIGH_CEILING, "30", STANDARD_PRICE, 1, 2);
    assertEquals(
        alsoFast.id(),
        assertInstanceOf(
                TierEvaluation.Priced.class,
                select(eur("100.00"), LIGHT, List.of(slow, fast, alsoFast)))
            .quote()
            .tierId());
  }

  @Test
  void exceedingEveryTierNamesTheMostPermissiveOne() {
    final FulfillmentTier small = tier("small", "500.00", "30", "5.00", 3, 5);
    final FulfillmentTier large = tier("large", "2000.00", "30", "40.00", 3, 5);
    final TierEvaluation.ExceedsCeiling exceeded =
        assertInstanceOf(
            TierEvaluation.ExceedsCeiling.class,
            select(eur("5000.00"), LIGHT, List.of(small, large)));
    assertEquals(large, exceeded.tier());
    assertEquals(CeilingKind.VALUE, exceeded.which());
  }

  @Test
  void whenBothCeilingsAreExceededValueTakesPrecedence() {
    final FulfillmentTier only = tier("only", "1000.00", "10", STANDARD_PRICE, 3, 5);
    final TierEvaluation.ExceedsCeiling exceeded =
        assertInstanceOf(
            TierEvaluation.ExceedsCeiling.class,
            select(eur("5000.00"), Weight.of("50", WeightUnit.KILOGRAM), List.of(only)));
    assertEquals(CeilingKind.VALUE, exceeded.which());
  }

  @Test
  void invalidInputIsRefused() {
    final FulfillmentTier only = tier("only", "1000.00", "10", STANDARD_PRICE, 3, 5);
    final List<FulfillmentTier> tiers = List.of(only);
    final Money dollars = Money.of("100.00", Currency.USD);
    final Money negative = eur("-1.00");
    assertEquals(
        "tier-selector.input-missing",
        assertThrows(
                ValidationException.class,
                () -> TierSelector.select(null, eur("1.00"), LIGHT, tiers, NOW))
            .code());
    assertEquals(
        "tier-selector.value-negative",
        assertThrows(ValidationException.class, () -> select(negative, LIGHT, tiers)).code());
    assertEquals(
        "money.currency-mismatch",
        assertThrows(ValidationException.class, () -> select(dollars, LIGHT, tiers)).code());
  }
}
