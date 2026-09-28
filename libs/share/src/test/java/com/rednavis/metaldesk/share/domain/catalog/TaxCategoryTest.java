package com.rednavis.metaldesk.share.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.domain.catalog.TaxCategory.RateSource;
import com.rednavis.metaldesk.share.error.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TaxCategoryTest {

  private static final String STANDARD_KEY = "standard";

  @Test
  void investmentGradeResolvesToZeroRate() {
    final RateSource.Zero zero = new RateSource.Zero();
    assertTrue(TaxCategory.INVESTMENT_GRADE.isZeroRated());
    assertEquals(zero, TaxCategory.INVESTMENT_GRADE.rateSource());
    assertEquals(0, zero.rate().signum());
  }

  @Test
  void standardIsConfiguredReferenceNotLiteralRate() {
    assertFalse(TaxCategory.STANDARD.isZeroRated());
    assertEquals(new RateSource.Configured(STANDARD_KEY), TaxCategory.STANDARD.rateSource());
  }

  @Test
  void rateSourceSwitchIsExhaustive() {
    for (final TaxCategory category : TaxCategory.values()) {
      final String kind =
          switch (category.rateSource()) {
            case RateSource.Zero() -> "zero";
            case RateSource.Configured(String reference) -> reference;
          };
      assertEquals(category.isZeroRated() ? "zero" : STANDARD_KEY, kind);
    }
  }

  @Test
  void referenceIsTrimmed() {
    assertEquals(STANDARD_KEY, new RateSource.Configured(" standard ").reference());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void blankReferenceIsRefused(String reference) {
    assertEquals(
        "tax-rate.reference-blank",
        assertThrows(ValidationException.class, () -> new RateSource.Configured(reference)).code());
  }
}
