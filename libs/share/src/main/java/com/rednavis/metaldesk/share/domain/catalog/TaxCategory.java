package com.rednavis.metaldesk.share.domain.catalog;

import com.rednavis.metaldesk.share.error.ValidationException;
import java.math.BigDecimal;

/**
 * The tax classification of a {@link Category} (BRD BR-4). A product has no tax classification of
 * its own; it takes its category's.
 *
 * <p>Each constant carries a {@link RateSource} rather than a percentage. A literal percentage in
 * an enum is a redeploy to change and wrong for a second jurisdiction, and the legacy catalog's
 * per-product {@code vat} field is what this design replaces. The two constants differ in what is
 * fixed:
 *
 * <ul>
 *   <li>{@link #INVESTMENT_GRADE} is a <em>domain constant</em>: zero-rating is a legal
 *       classification (the EU investment-gold treatment BR-4 refers to), so its rate is genuinely
 *       {@link RateSource.Zero zero}.
 *   <li>{@link #STANDARD} is <em>configuration</em>: its rate is a {@link RateSource.Configured
 *       named reference} that the pricing layer (T-013) resolves for the deployment's jurisdiction.
 * </ul>
 *
 * <p>BR-4 is a compliance mapping and must be reviewed against current tax law wherever a real
 * deployment operates; which categories are {@code INVESTMENT_GRADE} is catalog data, not code.
 *
 * <p>An order line snapshots the resolved treatment when the order is placed (T-014), so changing a
 * category's classification later cannot alter a settled order (BR-2).
 */
public enum TaxCategory {

  /** Zero-rated investment-grade metal products (BRD BR-4). */
  INVESTMENT_GRADE(new RateSource.Zero()),

  /** Everything else, at the configured standard rate (BRD BR-4). */
  STANDARD(new RateSource.Configured("standard"));

  private final RateSource source;

  TaxCategory(RateSource source) {
    this.source = source;
  }

  /**
   * Returns where this classification's rate comes from.
   *
   * @return the rate source, never null
   */
  public RateSource rateSource() {
    return source;
  }

  /**
   * Reports whether this classification is zero-rated.
   *
   * @return {@code true} when the rate source is {@link RateSource.Zero}
   */
  public boolean isZeroRated() {
    return source instanceof RateSource.Zero;
  }

  /**
   * Where a tax rate comes from. Sealed, so a {@code switch} over it is exhaustive and a new kind
   * of source cannot be added without every consumer noticing.
   */
  public sealed interface RateSource permits RateSource.Zero, RateSource.Configured {

    /** The zero-rated case: a legal classification, not a configured value. */
    record Zero() implements RateSource {

      /**
       * Returns the rate.
       *
       * @return {@link BigDecimal#ZERO}
       */
      public BigDecimal rate() {
        return BigDecimal.ZERO;
      }
    }

    /**
     * A rate held in configuration and looked up by name, so it can change without a redeploy.
     *
     * @param reference the configuration key the pricing layer resolves, never null or blank
     */
    record Configured(String reference) implements RateSource {

      /**
       * Validates the reference.
       *
       * @throws ValidationException if the reference is null or blank
       */
      public Configured {
        if (reference == null || reference.isBlank()) {
          throw new ValidationException(
              "tax-rate.reference-blank", "Tax rate reference must not be null or blank");
        }
        reference = reference.strip();
      }
    }
  }
}
