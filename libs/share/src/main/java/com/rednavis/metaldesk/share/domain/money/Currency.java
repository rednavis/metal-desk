package com.rednavis.metaldesk.share.domain.money;

/**
 * The display currencies a customer can switch between (BRD FR-1.8), identified by ISO-4217 code.
 *
 * <p>The enum constant name <em>is</em> the ISO code. Each constant also states how many minor-unit
 * digits an amount in that currency carries, which is what {@link Money} scales to.
 *
 * <p>This type says nothing about exchange rates: converting between currencies needs a rate from
 * outside the domain and lives in a later converter that consumes {@link Money}.
 */
public enum Currency {
  /** United States dollar. */
  USD(2),
  /** Euro. */
  EUR(2);

  private final int digits;

  Currency(int minorUnitDigits) {
    this.digits = minorUnitDigits;
  }

  /**
   * Returns the ISO-4217 code.
   *
   * @return the three-letter code, for example {@code EUR}
   */
  public String code() {
    return name();
  }

  /**
   * Returns the number of minor-unit digits, for example 2 for cents.
   *
   * @return the number of digits after the decimal point that an amount in this currency carries
   */
  public int minorUnitDigits() {
    return digits;
  }
}
