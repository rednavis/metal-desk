package com.rednavis.metaldesk.admin.tier;

import com.rednavis.metaldesk.admin.tier.dto.TierView;
import com.rednavis.metaldesk.share.domain.fulfillment.FulfillmentTier;

/** Builds the response view of a tier. */
public final class TierViews {

  private TierViews() {}

  /**
   * Builds the view.
   *
   * @param tier the tier
   * @return the view
   */
  public static TierView of(FulfillmentTier tier) {
    return new TierView(
        tier.id().value(),
        tier.region().code(),
        tier.valueCeiling().amount().toPlainString(),
        tier.valueCeiling().currency().code(),
        tier.weightCeiling().toCanonical().amount().toPlainString(),
        tier.deliveryPrice().amount().toPlainString(),
        tier.transit().minDays(),
        tier.transit().maxDays());
  }
}
