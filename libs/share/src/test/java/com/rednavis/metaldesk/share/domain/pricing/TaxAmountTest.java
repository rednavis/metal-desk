package com.rednavis.metaldesk.share.domain.pricing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rednavis.metaldesk.share.domain.money.Currency;
import com.rednavis.metaldesk.share.domain.money.Money;
import com.rednavis.metaldesk.share.error.ValidationException;
import org.junit.jupiter.api.Test;

class TaxAmountTest {

  private static final Money NET = Money.of("1959.32", Currency.EUR);

  @Test
  void taxIsRoundedOnceToTheMinorUnit() {
    final TaxAmount amount = TaxAmount.of(NET, TaxRate.of("19"));
    assertEquals(Money.of("372.27", Currency.EUR), amount.tax());
    assertEquals(NET, amount.net());
    assertEquals(TaxRate.of("19"), amount.rate());
  }

  @Test
  void zeroRateGivesZeroTax() {
    assertEquals(Money.zero(Currency.EUR), TaxAmount.of(NET, TaxRate.ZERO).tax());
  }

  @Test
  void taxThatDoesNotFollowFromNetAndRateIsRefused() {
    final Money wrong = Money.of("1.00", Currency.EUR);
    final TaxRate rate = TaxRate.of("19");
    assertEquals(
        "tax-amount.inconsistent",
        assertThrows(ValidationException.class, () -> new TaxAmount(NET, rate, wrong)).code());
  }

  @Test
  void missingInputIsRefused() {
    assertEquals(
        "tax-amount.input-missing",
        assertThrows(ValidationException.class, () -> TaxAmount.of(null, TaxRate.ZERO)).code());
    assertEquals(
        "tax-amount.input-missing",
        assertThrows(ValidationException.class, () -> TaxAmount.of(NET, null)).code());
  }
}
