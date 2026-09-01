package com.rednavis.metaldesk.share.domain.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.domain.catalog.TaxCategory;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TaxRateTest {

  @Test
  void investmentGradeIsZeroRated() {
    assertTrue(TaxRate.forCategory(TaxCategory.INVESTMENT_GRADE).isZero());
  }

  @Test
  void standardIsNotZeroRated() {
    assertFalse(TaxRate.forCategory(TaxCategory.STANDARD).isZero());
  }

  @Test
  void standardRateComesFromTheResolver() {
    final TaxRate.Resolver seven = reference -> TaxRate.of("7");
    assertEquals(TaxRate.of("7"), TaxRate.forCategory(TaxCategory.STANDARD, seven));
  }

  @Test
  void zeroRatingIsNotConfigurable() {
    final TaxRate.Resolver twenty = reference -> TaxRate.of("20");
    assertEquals(TaxRate.ZERO, TaxRate.forCategory(TaxCategory.INVESTMENT_GRADE, twenty));
  }

  @Test
  void resolverWithoutRateIsRefused() {
    final TaxRate.Resolver none = reference -> null;
    assertEquals(
        "tax-rate.unresolved",
        assertThrows(
                ValidationException.class, () -> TaxRate.forCategory(TaxCategory.STANDARD, none))
            .code());
  }

  @Test
  void fractionIsExact() {
    assertEquals(new BigDecimal("0.19"), TaxRate.of("19").asFraction());
    assertEquals(TaxRate.of("19"), TaxRate.of("19.00"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"-1", "100.01"})
  void outOfRangeRateIsRefused(String percent) {
    assertEquals(
        "tax-rate.out-of-range",
        assertThrows(ValidationException.class, () -> TaxRate.of(percent)).code());
  }

  @Test
  void malformedAndMissingAreRefused() {
    assertEquals(
        "tax-rate.malformed",
        assertThrows(ValidationException.class, () -> TaxRate.of("x")).code());
    assertEquals(
        "tax-rate.required",
        assertThrows(ValidationException.class, () -> TaxRate.of(null)).code());
    assertEquals(
        "tax-rate.input-missing",
        assertThrows(ValidationException.class, () -> TaxRate.forCategory(null)).code());
  }
}
