package com.rednavis.metaldesk.api.checkout.delivery;

/**
 * Why an order needs a manager, as a stable code the client (T-054) explains to the customer and
 * staff (T-040) read. These are the whole set; there is no free-text reason.
 */
public enum HandoffReason {

  /** The order's value before tax is over the region's widest value ceiling (BRD FR-5.1). */
  VALUE_CEILING_EXCEEDED,

  /** The order's weight is over the region's widest weight ceiling (BRD FR-5.1). */
  WEIGHT_CEILING_EXCEEDED,

  /**
   * No tier is configured for the destination region. A human must price it; a zero-cost quote
   * would ship metal for free.
   */
  NO_TIER_FOR_REGION
}
