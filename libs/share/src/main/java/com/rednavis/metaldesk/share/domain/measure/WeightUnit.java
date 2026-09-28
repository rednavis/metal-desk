package com.rednavis.metaldesk.share.domain.measure;

import java.math.BigDecimal;

/**
 * The units a {@link Weight} can be quoted in, each with its exact conversion factor to the
 * canonical unit, the gram.
 *
 * <p>Troy ounce is required because it is the unit a precious-metals catalog actually quotes; its
 * factor is the exact definition (31.1034768 g), not a rounded approximation.
 */
public enum WeightUnit {
  /** The canonical unit, against which tier weight ceilings are compared (BRD FR-5.1). */
  GRAM("1"),
  /** One thousand grams. */
  KILOGRAM("1000"),
  /** The troy ounce, exactly 31.1034768 grams. */
  TROY_OUNCE("31.1034768");

  private final BigDecimal grams;

  WeightUnit(String gramsPerUnit) {
    this.grams = new BigDecimal(gramsPerUnit);
  }

  /**
   * Returns how many grams one of this unit weighs.
   *
   * @return the exact conversion factor to grams
   */
  public BigDecimal gramsPerUnit() {
    return grams;
  }
}
