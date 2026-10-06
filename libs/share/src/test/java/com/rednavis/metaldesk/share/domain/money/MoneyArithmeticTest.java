package com.rednavis.metaldesk.share.domain.money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class MoneyArithmeticTest {

  @Test
  void plusAndMinusWorkWithinOneCurrency() {
    final Money five = Money.of("5", Currency.EUR);
    final Money two = Money.of("2.25", Currency.EUR);

    assertEquals(Money.of("7.25", Currency.EUR), five.plus(two));
    assertEquals(Money.of("2.75", Currency.EUR), five.minus(two));
  }

  @Test
  void plusAcrossCurrenciesIsRefused() {
    final Money usd = Money.of("1", Currency.USD);
    final Money eur = Money.of("1", Currency.EUR);

    final ValidationException failure =
        assertThrows(ValidationException.class, () -> usd.plus(eur));
    assertEquals("money.currency-mismatch", failure.code());
  }

  @Test
  void minusAcrossCurrenciesIsRefused() {
    final Money usd = Money.of("1", Currency.USD);
    final Money eur = Money.of("1", Currency.EUR);

    assertThrows(ValidationException.class, () -> usd.minus(eur));
  }

  @Test
  void compareToAcrossCurrenciesIsRefused() {
    final Money usd = Money.of("1", Currency.USD);
    final Money eur = Money.of("1", Currency.EUR);

    assertThrows(ValidationException.class, () -> usd.compareTo(eur));
  }

  @Test
  void compareToOrdersWithinOneCurrency() {
    final Money one = Money.of("1", Currency.USD);
    final Money two = Money.of("2", Currency.USD);

    assertTrue(one.compareTo(two) < 0);
    assertTrue(two.compareTo(one) > 0);
    assertEquals(0, one.compareTo(Money.of("1.00", Currency.USD)));
  }

  @Test
  void multiplyRoundsOnceToTheMinorUnit() {
    final Money price = Money.of("10.05", Currency.USD);

    assertEquals(Money.of("30.15", Currency.USD), price.multiply(new BigDecimal("3")));
    assertEquals(Money.of("3.02", Currency.USD), price.multiply(new BigDecimal("0.3")));
  }

  @Test
  void nullOperandsAreRefused() {
    final Money usd = Money.of("1", Currency.USD);

    assertThrows(ValidationException.class, () -> Objects.requireNonNull(usd.plus(null)));
    assertThrows(ValidationException.class, () -> Objects.requireNonNull(usd.multiply(null)));
  }
}
