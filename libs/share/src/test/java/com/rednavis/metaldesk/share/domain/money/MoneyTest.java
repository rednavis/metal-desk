package com.rednavis.metaldesk.share.domain.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {

  @Test
  void differentScalesOfTheSameAmountAreEqual() {
    assertEquals(Money.of("1.5", Currency.USD), Money.of("1.50", Currency.USD));
  }

  @Test
  void amountIsRoundedHalfUpToTheMinorUnitOnce() {
    assertEquals(new BigDecimal("1.01"), Money.of("1.005", Currency.EUR).amount());
    assertEquals(new BigDecimal("1.00"), Money.of("1.004", Currency.EUR).amount());
  }

  @Test
  void signPredicatesReflectTheAmount() {
    assertTrue(Money.zero(Currency.USD).isZero());
    assertFalse(Money.of("0.01", Currency.USD).isZero());
    assertTrue(Money.of("-0.01", Currency.USD).isNegative());
    assertFalse(Money.zero(Currency.USD).isNegative());
  }

  @Test
  void nullsAreRefused() {
    assertThrows(ValidationException.class, () -> new Money(null, Currency.USD));
    assertThrows(ValidationException.class, () -> new Money(BigDecimal.ONE, null));
    assertThrows(ValidationException.class, () -> Money.of((String) null, Currency.USD));
  }

  @Test
  void malformedTextIsRefused() {
    final ValidationException failure =
        assertThrows(ValidationException.class, () -> Money.of("twelve", Currency.USD));
    assertEquals("money.malformed-amount", failure.code());
  }
}
