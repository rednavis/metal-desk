package com.rednavis.metaldesk.share.domain.order;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;

/**
 * How many units of a product an order line holds: a whole number from 1 up to {@link #MAX}.
 *
 * <p>The per-line cap comes from BRD FR-3.3 ("capped at a fixed per-line maximum — illustrative:
 * 10"). It is a documented constant here because the BRD gives one figure and nothing yet makes it
 * configurable; if staff need to change it, it becomes configuration and this type takes it as a
 * parameter.
 *
 * @param value the number of units, from 1 to {@link #MAX}
 */
public record Quantity(int value) {

  /** The most units one line may hold (BRD FR-3.3, illustrative). */
  public static final int MAX = 10;

  private static final int MIN = 1;

  /**
   * Validates the range.
   *
   * @throws ValidationException if the value is below 1 or above {@link #MAX}
   */
  public Quantity {
    if (value < MIN) {
      throw new ValidationException(
          "quantity.not-positive", "Quantity must be at least " + MIN + ", was " + value);
    }
    if (value > MAX) {
      throw new ValidationException(
          "quantity.above-cap", "Quantity must be at most " + MAX + ", was " + value);
    }
  }

  /**
   * Creates a quantity.
   *
   * @param value the number of units
   * @return the quantity
   * @throws ValidationException if the value is out of range
   */
  public static Quantity of(int value) {
    return new Quantity(value);
  }

  /**
   * Returns the quantity as an exact decimal, for multiplying a {@code Money}.
   *
   * @return the value as a {@link BigDecimal}
   */
  public BigDecimal asFactor() {
    return BigDecimal.valueOf(value);
  }
}
