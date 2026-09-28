package com.rednavis.metaldesk.share.domain.fulfillment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Transit time, the manager quote and the evaluation outcomes: small value types. */
class SmallFulfillmentTypesTest {

  private static final Instant NOW = Instant.parse("2026-09-29T09:00:00Z");
  private static final Money PRICE = Money.of("12000.00", Currency.EUR);

  @Test
  void transitTimeIsRangeOfBusinessDays() {
    assertEquals(3, new TransitTime(1, 3).maxDays());
    assertEquals(new TransitTime(2, 2), new TransitTime(2, 2));
  }

  @Test
  void invalidTransitTimeIsRefused() {
    assertEquals(
        "transit-time.min-not-positive",
        assertThrows(ValidationException.class, () -> new TransitTime(0, 3)).code());
    assertEquals(
        "transit-time.range-inverted",
        assertThrows(ValidationException.class, () -> new TransitTime(4, 3)).code());
  }

  @Test
  void managerQuoteTrimsItsTerms() {
    assertEquals(
        "Insured courier, 5 days",
        new ManagerQuote(PRICE, "  Insured courier, 5 days ", NOW).terms());
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "\t"})
  void blankTermsAreRefused(String terms) {
    assertEquals(
        "manager-quote.terms-blank",
        assertThrows(ValidationException.class, () -> new ManagerQuote(PRICE, terms, NOW)).code());
  }

  @Test
  void overlongTermsAndBadPricesAreRefused() {
    final String tooLong = "x".repeat(ManagerQuote.MAX_TERMS_LENGTH + 1);
    final Money zero = Money.zero(Currency.EUR);
    assertEquals(
        "manager-quote.terms-too-long",
        assertThrows(ValidationException.class, () -> new ManagerQuote(PRICE, tooLong, NOW))
            .code());
    assertEquals(
        "manager-quote.price-invalid",
        assertThrows(ValidationException.class, () -> new ManagerQuote(zero, "ok", NOW)).code());
    assertEquals(
        "manager-quote.instant-missing",
        assertThrows(ValidationException.class, () -> new ManagerQuote(PRICE, "ok", null)).code());
  }

  @Test
  void evaluationOutcomesRefuseNulls() {
    assertEquals(
        "tier-evaluation.quote-missing",
        assertThrows(ValidationException.class, () -> new TierEvaluation.Priced(null)).code());
    assertEquals(
        "tier-evaluation.field-missing",
        assertThrows(
                ValidationException.class,
                () -> new TierEvaluation.ExceedsCeiling(CeilingKind.VALUE, null))
            .code());
    assertEquals(
        "tier-evaluation.region-missing",
        assertThrows(ValidationException.class, () -> new TierEvaluation.NoTier(null)).code());
  }
}
