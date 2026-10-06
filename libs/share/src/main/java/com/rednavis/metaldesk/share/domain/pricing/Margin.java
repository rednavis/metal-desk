package com.rednavis.metaldesk.share.domain.pricing;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;

/**
 * The platform's markup over the reference price, as a percentage (BRD BR-3, glossary "Margin").
 *
 * <p>The valid range is 0 to 100 percent inclusive. A negative margin would sell below the
 * reference price, which is a configuration error rather than a discount feature; a margin above
 * 100 percent would price the metal at over twice spot, which no bullion seller intends, so it is
 * treated as a mistyped value (for example {@code 500} for {@code 5}) rather than accepted. The
 * value is an exact decimal, stripped of trailing zeros at construction so {@code 5.0} equals
 * {@code 5}.
 *
 * @param percent the markup in percent, in the range [0, 100]
 */
public record Margin(BigDecimal percent) {

  private static final BigDecimal MAX = BigDecimal.valueOf(100);

  /**
   * Validates the range and normalises the scale.
   *
   * @throws ValidationException if the value is null, negative or above 100
   */
  public Margin {
    if (percent == null) {
      throw new ValidationException("margin.required", "Margin requires a percentage");
    }
    if (percent.signum() < 0) {
      throw new ValidationException(
          "margin.negative", "Margin cannot be negative, was " + percent + "%");
    }
    if (percent.compareTo(MAX) > 0) {
      throw new ValidationException(
          "margin.too-high", "Margin cannot exceed 100%, was " + percent + "%");
    }
    final BigDecimal stripped = percent.stripTrailingZeros();
    percent = stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }

  /**
   * Creates a margin from its decimal text form.
   *
   * @param percent the decimal text, for example {@code "5"} or {@code "2.5"}
   * @return the margin
   * @throws ValidationException if the text is not a decimal number or is out of range
   */
  public static Margin of(String percent) {
    if (percent == null) {
      throw new ValidationException("margin.required", "Margin requires a percentage");
    }
    try {
      return new Margin(new BigDecimal(percent));
    } catch (NumberFormatException e) {
      throw new ValidationException("margin.malformed", "Not a decimal percentage: " + percent, e);
    }
  }

  /**
   * Returns the margin as a fraction, exactly: 5 percent is {@code 0.05}.
   *
   * @return the percentage divided by 100, without rounding
   */
  public BigDecimal asFraction() {
    return percent.movePointLeft(2);
  }
}
