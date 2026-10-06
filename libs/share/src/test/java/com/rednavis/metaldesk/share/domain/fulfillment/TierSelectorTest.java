package com.rednavis.metaldesk.share.domain.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.id.FulfillmentTierId;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TierSelectorTest {

  private static final Region EU_CORE = new Region("eu-core");
  private static final Instant NOW = Instant.parse("2026-09-29T09:00:00Z");

  /** The BRD's illustrative tier: 15,000 euro or 25 kg, whichever is reached first. */
  private static final FulfillmentTier EU_TIER =
      new FulfillmentTier(
          new FulfillmentTierId("eu-core-1"),
          EU_CORE,
          Money.of("15000.00", Currency.EUR),
          Weight.of("25", WeightUnit.KILOGRAM),
          Money.of("35.00", Currency.EUR),
          new TransitTime(2, 4));

  private static Money eur(String amount) {
    return Money.of(amount, Currency.EUR);
  }

  private static Weight kilograms(String amount) {
    return Weight.of(amount, WeightUnit.KILOGRAM);
  }

  private static TierEvaluation select(String value, String kg) {
    return TierSelector.select(EU_CORE, eur(value), kilograms(kg), List.of(EU_TIER), NOW);
  }

  @Test
  void selectorIsStatelessAndTakesTiersAsParameter() {
    final boolean hasInstanceState =
        Arrays.stream(TierSelector.class.getDeclaredFields())
            .anyMatch(field -> !Modifier.isStatic(field.getModifiers()));
    final boolean takesTiers =
        Arrays.stream(TierSelector.class.getDeclaredMethods())
            .filter(method -> "select".equals(method.getName()))
            .anyMatch(method -> Arrays.asList(method.getParameterTypes()).contains(List.class));
    assertFalse(hasInstanceState);
    assertTrue(takesTiers);
  }

  @Test
  void orderWithinBothCeilingsIsPriced() {
    final TierEvaluation.Priced priced =
        assertInstanceOf(TierEvaluation.Priced.class, select("1000.00", "5"));
    assertEquals(eur("35.00"), priced.quote().cost());
    assertEquals(EU_TIER.id(), priced.quote().tierId());
    assertEquals(new TransitTime(2, 4), priced.quote().transit());
    assertEquals(NOW, priced.quote().quotedAt());
  }

  @Test
  void ceilingsAreInclusive() {
    assertInstanceOf(TierEvaluation.Priced.class, select("15000.00", "25"));
  }

  @Test
  void orderOverTheValueCeilingExceedsValue() {
    final TierEvaluation.ExceedsCeiling exceeded =
        assertInstanceOf(TierEvaluation.ExceedsCeiling.class, select("15000.01", "5"));
    assertEquals(CeilingKind.VALUE, exceeded.which());
    assertEquals(EU_TIER, exceeded.tier());
  }

  @Test
  void orderOverTheWeightCeilingExceedsWeight() {
    final TierEvaluation.ExceedsCeiling exceeded =
        assertInstanceOf(TierEvaluation.ExceedsCeiling.class, select("1000.00", "25.001"));
    assertEquals(CeilingKind.WEIGHT, exceeded.which());
  }

  @Test
  void regionWithoutTierIsDistinctOutcomeNeverZeroQuote() {
    final TierEvaluation outcome =
        TierSelector.select(new Region("us"), eur("100.00"), kilograms("1"), List.of(EU_TIER), NOW);
    assertEquals(new TierEvaluation.NoTier(new Region("us")), outcome);
    assertEquals(
        new TierEvaluation.NoTier(EU_CORE),
        TierSelector.select(EU_CORE, eur("100.00"), kilograms("1"), List.of(), NOW));
  }

  /**
   * FR-5.1 and BR-8 evaluate the ex-tax value. The same order that is priced on its ex-tax value of
   * 14,000 euro goes to handoff if the gross total (19% tax, 16,660) is passed instead.
   */
  @Test
  void theSameOrderCanChangeOutcomeOnItsGrossTotal() {
    assertInstanceOf(TierEvaluation.Priced.class, select("14000.00", "5"));
    final TierEvaluation.ExceedsCeiling grossOutcome =
        assertInstanceOf(TierEvaluation.ExceedsCeiling.class, select("16660.00", "5"));
    assertEquals(CeilingKind.VALUE, grossOutcome.which());
  }
}
