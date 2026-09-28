package com.rednavis.metaldesk.share.domain.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.catalog.Metal;
import com.rednavis.metaldesk.share.domain.catalog.ProductSpecification;
import com.rednavis.metaldesk.share.domain.id.CategoryId;
import com.rednavis.metaldesk.share.domain.measure.Purity;
import com.rednavis.metaldesk.share.domain.measure.Weight;
import com.rednavis.metaldesk.share.domain.measure.WeightUnit;
import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PriceDerivationTest {

  private static final Instant OBSERVED = Instant.parse("2026-09-28T10:15:30Z");

  /** One troy ounce of 999.9-fine gold. */
  private static final ProductSpecification ONE_OUNCE =
      new ProductSpecification(
          Metal.GOLD, Purity.of("999.9"), Weight.of("1", WeightUnit.TROY_OUNCE), Optional.empty());

  private static final ReferencePrice SIXTY_PER_GRAM =
      new ReferencePrice(Metal.GOLD, Money.of("60.00", Currency.EUR), OBSERVED);

  private static PriceRule rule(String percent) {
    return new PriceRule(
        Margin.of(percent), new PriceRule.Scope.ForCategory(new CategoryId("c-1")));
  }

  /**
   * Hand-computed: 31.1034768 g x 999.9 / 1000 = 31.10036645232 g fine; x 60.00 = 1866.0219871392;
   * x 1.05 = 1959.32308649616, which rounds once to 1959.32. Leaving purity out gives 1959.52.
   */
  @Test
  void workedExampleMatchesHandComputation() {
    final SellablePrice price = PriceDerivation.derive(SIXTY_PER_GRAM, ONE_OUNCE, rule("5"));
    assertEquals(Money.of("1959.32", Currency.EUR), price.unitPrice());
  }

  @Test
  void zeroMarginYieldsExactlyTheMetalValue() {
    final SellablePrice price = PriceDerivation.derive(SIXTY_PER_GRAM, ONE_OUNCE, rule("0"));
    assertEquals(Money.of("1866.02", Currency.EUR), price.unitPrice());
  }

  @Test
  void sameInputsGiveEqualResults() {
    final PriceRule rule = rule("5");
    assertEquals(
        PriceDerivation.derive(SIXTY_PER_GRAM, ONE_OUNCE, rule),
        PriceDerivation.derive(SIXTY_PER_GRAM, ONE_OUNCE, rule));
  }

  @Test
  void resultKeepsItsProvenance() {
    final PriceRule rule = rule("5");
    final SellablePrice price = PriceDerivation.derive(SIXTY_PER_GRAM, ONE_OUNCE, rule);
    assertEquals(rule, price.rule());
    assertEquals(SIXTY_PER_GRAM, price.reference());
  }

  @Test
  void derivationIsStatelessAndHasNoClock() {
    final boolean hasInstanceState =
        Arrays.stream(PriceDerivation.class.getDeclaredFields())
            .anyMatch(field -> !Modifier.isStatic(field.getModifiers()));
    final boolean holdsClock =
        Arrays.stream(PriceDerivation.class.getDeclaredFields())
            .anyMatch(field -> field.getType() == Clock.class);
    assertFalse(hasInstanceState);
    assertFalse(holdsClock);
  }

  @Test
  void referenceForAnotherMetalIsRefused() {
    final ReferencePrice silver =
        new ReferencePrice(Metal.SILVER, Money.of("0.85", Currency.EUR), OBSERVED);
    assertEquals(
        "price-derivation.metal-mismatch",
        assertThrows(
                ValidationException.class,
                () -> PriceDerivation.derive(silver, ONE_OUNCE, rule("5")))
            .code());
  }

  @Test
  void missingInputIsRefused() {
    assertEquals(
        "price-derivation.input-missing",
        assertThrows(
                ValidationException.class,
                () -> PriceDerivation.derive(SIXTY_PER_GRAM, ONE_OUNCE, null))
            .code());
  }
}
