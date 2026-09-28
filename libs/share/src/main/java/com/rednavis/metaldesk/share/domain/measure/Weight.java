package com.rednavis.metaldesk.share.domain.measure;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;

/**
 * A non-negative weight with the unit it was quoted in (BRD FR-1.3, FR-5.1).
 *
 * <p>Tier evaluation compares an order's weight against a ceiling, and those two values will not
 * arrive in the same unit, so {@link #toCanonical()} converts to grams and {@link #compareTo}
 * orders by that canonical value. Conversion is exact: every {@link WeightUnit} factor is a finite
 * decimal, so nothing is rounded.
 *
 * <p>The amount is stripped of trailing zeros at construction so {@code 1.0 g} equals {@code 1 g}.
 * Two weights in different units are ordered by {@code compareTo} but are not {@code equals};
 * compare their {@link #toCanonical()} forms when unit-independent equality is wanted.
 *
 * <p>Refuses: a null amount or unit, and a negative amount.
 *
 * @param amount the amount in {@code unit}, never negative
 * @param unit the unit the amount is quoted in
 */
public record Weight(BigDecimal amount, WeightUnit unit) implements Comparable<Weight> {

  private static final String CODE_REQUIRED = "weight.required";

  /**
   * Validates the components and normalises the amount's scale.
   *
   * @throws ValidationException if the amount or unit is null, or the amount is negative
   */
  public Weight {
    if (unit == null) {
      throw new ValidationException(CODE_REQUIRED, "Weight requires a unit");
    }
    if (amount == null) {
      throw new ValidationException(CODE_REQUIRED, "Weight requires an amount");
    }
    if (amount.signum() < 0) {
      throw new ValidationException("weight.negative", "Weight cannot be negative: " + amount);
    }
    final BigDecimal stripped = amount.stripTrailingZeros();
    amount = stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }

  /**
   * Creates a weight from its decimal text form.
   *
   * @param amount the decimal text, for example {@code "31.1034768"}
   * @param unit the unit
   * @return the weight
   * @throws ValidationException if the text is not a decimal number, or the weight is invalid
   */
  public static Weight of(String amount, WeightUnit unit) {
    if (amount == null) {
      throw new ValidationException(CODE_REQUIRED, "Weight requires an amount");
    }
    try {
      return new Weight(new BigDecimal(amount), unit);
    } catch (NumberFormatException e) {
      throw new ValidationException(
          "weight.malformed-amount", "Not a decimal weight: " + amount, e);
    }
  }

  /**
   * Converts to the canonical unit.
   *
   * @return this weight expressed in grams, exactly
   */
  public Weight toCanonical() {
    return new Weight(amount.multiply(unit.gramsPerUnit()), WeightUnit.GRAM);
  }

  /**
   * Orders two weights by their canonical (gram) value, regardless of unit.
   *
   * @param other the weight to compare with
   * @return a negative, zero or positive number as this weight is lighter than, equal to or heavier
   *     than the other
   */
  @Override
  public int compareTo(Weight other) {
    return toCanonical().amount.compareTo(other.toCanonical().amount);
  }
}
