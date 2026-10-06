package com.rednavis.metaldesk.share.domain.money;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * An amount of money together with the currency it is denominated in (BRD BR-5, FR-1.8).
 *
 * <p>This is the only type in the system that holds a monetary {@link BigDecimal}; no amount may
 * travel without its currency. The amount is scaled to the currency's minor-unit digits with {@link
 * RoundingMode#HALF_UP} <em>once, at construction</em>, so the generated record {@code equals} is
 * correct: {@code 1.5 USD} equals {@code 1.50 USD}.
 *
 * <p>Refuses: a null amount or currency, and any arithmetic or comparison across two currencies —
 * it never coerces and never picks a side. Currency conversion is intentionally absent; it needs an
 * exchange rate from outside the domain, so do not add a {@code convertTo} with a fixed rate here.
 *
 * @param amount the amount, already scaled to the currency's minor-unit digits
 * @param currency the currency the amount is denominated in
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

  private static final String CODE_REQUIRED = "money.required";
  private static final String CODE_MISMATCH = "money.currency-mismatch";

  /**
   * Validates the components and scales the amount to the currency's minor-unit digits.
   *
   * @throws ValidationException if the amount or currency is null
   */
  public Money {
    if (currency == null) {
      throw new ValidationException(CODE_REQUIRED, "Money requires a currency");
    }
    if (amount == null) {
      throw new ValidationException(CODE_REQUIRED, "Money requires an amount");
    }
    amount = amount.setScale(currency.minorUnitDigits(), RoundingMode.HALF_UP);
  }

  /**
   * Creates an amount from its decimal text form.
   *
   * @param amount the decimal text, for example {@code "1.5"}
   * @param currency the currency
   * @return the amount, scaled to the currency's minor-unit digits
   * @throws ValidationException if the text is not a decimal number, or the currency is null
   */
  public static Money of(String amount, Currency currency) {
    if (amount == null) {
      throw new ValidationException(CODE_REQUIRED, "Money requires an amount");
    }
    try {
      return new Money(new BigDecimal(amount), currency);
    } catch (NumberFormatException e) {
      throw new ValidationException("money.malformed-amount", "Not a decimal amount: " + amount, e);
    }
  }

  /**
   * Creates an amount from an exact decimal.
   *
   * @param amount the amount
   * @param currency the currency
   * @return the amount, scaled to the currency's minor-unit digits
   * @throws ValidationException if the amount or currency is null
   */
  public static Money of(BigDecimal amount, Currency currency) {
    return new Money(amount, currency);
  }

  /**
   * Creates a zero amount.
   *
   * @param currency the currency
   * @return zero in that currency
   * @throws ValidationException if the currency is null
   */
  public static Money zero(Currency currency) {
    return new Money(BigDecimal.ZERO, currency);
  }

  /**
   * Adds another amount.
   *
   * @param other the amount to add
   * @return the sum, in the shared currency
   * @throws ValidationException if the currencies differ
   */
  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  /**
   * Subtracts another amount.
   *
   * @param other the amount to subtract
   * @return the difference, in the shared currency
   * @throws ValidationException if the currencies differ
   */
  public Money minus(Money other) {
    requireSameCurrency(other);
    return new Money(amount.subtract(other.amount), currency);
  }

  /**
   * Multiplies by a factor such as a quantity or a rate, rounding once to the minor unit.
   *
   * @param factor the factor
   * @return the product, in this amount's currency
   * @throws ValidationException if the factor is null
   */
  public Money multiply(BigDecimal factor) {
    if (factor == null) {
      throw new ValidationException(CODE_REQUIRED, "Money can only be multiplied by a factor");
    }
    return new Money(amount.multiply(factor), currency);
  }

  /**
   * Tells whether the amount is zero.
   *
   * @return {@code true} if the amount is zero
   */
  public boolean isZero() {
    return amount.signum() == 0;
  }

  /**
   * Tells whether the amount is below zero.
   *
   * @return {@code true} if the amount is negative
   */
  public boolean isNegative() {
    return amount.signum() < 0;
  }

  /**
   * Orders two amounts of the same currency.
   *
   * @param other the amount to compare with
   * @return a negative, zero or positive number as this amount is less than, equal to or greater
   *     than the other
   * @throws ValidationException if the currencies differ
   */
  @Override
  public int compareTo(Money other) {
    requireSameCurrency(other);
    return amount.compareTo(other.amount);
  }

  private void requireSameCurrency(Money other) {
    if (other == null) {
      throw new ValidationException(CODE_REQUIRED, "Money operation requires another amount");
    }
    if (currency != other.currency) {
      throw new ValidationException(
          CODE_MISMATCH, "Cannot combine " + currency.code() + " with " + other.currency.code());
    }
  }
}
