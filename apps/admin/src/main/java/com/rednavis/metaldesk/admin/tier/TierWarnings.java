package com.rednavis.metaldesk.admin.tier;

import com.rednavis.metaldesk.admin.tier.dto.TierWarning;
import com.rednavis.metaldesk.share.domain.Region;
import com.rednavis.metaldesk.share.domain.fulfillment.FulfillmentTier;
import com.rednavis.metaldesk.share.domain.fulfillment.TierEvaluation;
import java.util.List;

/** The warnings a tier change can carry, and the tests that decide whether one applies. */
public final class TierWarnings {

  private TierWarnings() {}

  /**
   * Whether a tier's ceilings are lower than before on either axis.
   *
   * @param after the tier as changed
   * @param before the tier as it was
   * @return true if the value or the weight ceiling was lowered
   */
  public static boolean narrower(FulfillmentTier after, FulfillmentTier before) {
    return after.valueCeiling().compareTo(before.valueCeiling()) < 0
        || after.weightCeiling().compareTo(before.weightCeiling()) < 0;
  }

  /**
   * The warning for a region left with no tier.
   *
   * @param region the region
   * @return the warning
   */
  public static TierWarning uncovered(Region region) {
    return new TierWarning(
        "region.uncovered",
        "Region "
            + region.code()
            + " has no tier left: every order there will go to manager handoff");
  }

  /**
   * The warning for the only tier of a region having lower ceilings.
   *
   * @param region the region
   * @return the warning
   */
  public static TierWarning narrowed(Region region) {
    return new TierWarning(
        "region.narrowed",
        "The only tier of region "
            + region.code()
            + " now has lower ceilings: orders above them will go to manager handoff");
  }

  /**
   * The warnings for a tier, given what the selector picks at the tier's own ceilings: if that is
   * another tier, this one can never be chosen.
   *
   * @param tier the tier that was saved
   * @param atCeilings the selector's answer for an order exactly at the tier's ceilings
   * @return {@code tier.shadowed} if another tier wins there, otherwise none
   */
  public static List<TierWarning> shadowed(FulfillmentTier tier, TierEvaluation atCeilings) {
    List<TierWarning> warnings = List.of();
    if (atCeilings instanceof TierEvaluation.Priced priced
        && !priced.quote().tierId().equals(tier.id())) {
      warnings =
          List.of(
              new TierWarning(
                  "tier.shadowed",
                  "Tier "
                      + tier.id().value()
                      + " is never chosen: tier "
                      + priced.quote().tierId().value()
                      + " covers the same orders for less (cheapest wins, then faster, then lower"
                      + " id)"));
    }
    return warnings;
  }
}
