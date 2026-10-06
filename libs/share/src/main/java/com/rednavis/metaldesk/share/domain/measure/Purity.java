package com.rednavis.metaldesk.share.domain.measure;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;

/**
 * The fineness of a metal product in parts per thousand, for example 999.9 for four-nines gold (BRD
 * FR-1.3).
 *
 * <p>The valid range is greater than 0 and at most 1000: a product with no precious metal has no
 * fineness, and nothing can be purer than pure. The value is an exact decimal and is stripped of
 * trailing zeros at construction so {@code 999.90} equals {@code 999.9}.
 *
 * @param partsPerThousand the fineness, in the range (0, 1000]
 */
public record Purity(BigDecimal partsPerThousand) implements Comparable<Purity> {

  private static final BigDecimal MAX = BigDecimal.valueOf(1000);

  /**
   * Validates the range and normalises the scale.
   *
   * @throws ValidationException if the value is null or outside (0, 1000]
   */
  public Purity {
    if (partsPerThousand == null) {
      throw new ValidationException("purity.required", "Purity requires a fineness");
    }
    if (partsPerThousand.signum() <= 0 || partsPerThousand.compareTo(MAX) > 0) {
      throw new ValidationException(
          "purity.out-of-range",
          "Fineness must be greater than 0 and at most 1000, was " + partsPerThousand);
    }
    final BigDecimal stripped = partsPerThousand.stripTrailingZeros();
    partsPerThousand = stripped.scale() < 0 ? stripped.setScale(0) : stripped;
  }

  /**
   * Creates a purity from its decimal text form.
   *
   * @param partsPerThousand the decimal text, for example {@code "999.9"}
   * @return the purity
   * @throws ValidationException if the text is not a decimal number or is out of range
   */
  public static Purity of(String partsPerThousand) {
    if (partsPerThousand == null) {
      throw new ValidationException("purity.required", "Purity requires a fineness");
    }
    try {
      return new Purity(new BigDecimal(partsPerThousand));
    } catch (NumberFormatException e) {
      throw new ValidationException(
          "purity.malformed", "Not a decimal fineness: " + partsPerThousand, e);
    }
  }

  @Override
  public int compareTo(Purity other) {
    return partsPerThousand.compareTo(other.partsPerThousand);
  }
}
