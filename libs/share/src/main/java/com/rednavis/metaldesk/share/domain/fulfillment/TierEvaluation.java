package com.rednavis.metaldesk.share.domain.fulfillment;

import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.error.ValidationException;

/**
 * The outcome of evaluating an order against the configured tiers (BRD FR-5.2, FR-5.3). Sealed, so
 * a {@code switch} over it is exhaustive and no caller can forget a case.
 *
 * <p>Only {@link Priced} lets checkout continue to payment. The other two are the manager-handoff
 * path: nothing here ever yields a free or zero-cost quote for an order no tier can price.
 */
public sealed interface TierEvaluation
    permits TierEvaluation.Priced, TierEvaluation.ExceedsCeiling, TierEvaluation.NoTier {

  /**
   * The order is within a tier's ceilings and delivery is priced automatically (BRD FR-5.2).
   *
   * @param quote the delivery quote, never null
   */
  record Priced(DeliveryQuote quote) implements TierEvaluation {

    /**
     * Validates the quote.
     *
     * @throws ValidationException if the quote is null
     */
    public Priced {
      if (quote == null) {
        throw new ValidationException("tier-evaluation.quote-missing", "Quote must not be null");
      }
    }
  }

  /**
   * The order is over every tier's ceilings for its region, so it goes to manager handoff (BRD
   * FR-5.3). The tier named is the region's most permissive one, the last that could have priced
   * the order, and {@code which} is the ceiling of that tier that was exceeded.
   *
   * @param which the ceiling that was exceeded; when both were, {@link CeilingKind#VALUE}
   * @param tier the region's most permissive tier, never null
   */
  record ExceedsCeiling(CeilingKind which, FulfillmentTier tier) implements TierEvaluation {

    /**
     * Validates the fields.
     *
     * @throws ValidationException if a field is null
     */
    public ExceedsCeiling {
      if (which == null || tier == null) {
        throw new ValidationException(
            "tier-evaluation.field-missing", "Ceiling kind and tier must not be null");
      }
    }
  }

  /**
   * Staff have configured no tier for the order's region, so nothing can price it. This is a
   * distinct outcome rather than a quote of zero: a missing configuration must not become free
   * delivery. Checkout is expected to treat it like a handoff so a person decides.
   *
   * @param region the region with no tier, never null
   */
  record NoTier(Region region) implements TierEvaluation {

    /**
     * Validates the region.
     *
     * @throws ValidationException if the region is null
     */
    public NoTier {
      if (region == null) {
        throw new ValidationException("tier-evaluation.region-missing", "Region must not be null");
      }
    }
  }
}
